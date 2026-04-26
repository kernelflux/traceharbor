/*
 * Tencent is pleased to support the open source community by making wechat-matrix available.
 * Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kernelflux.traceharbor.backtrace

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.kernelflux.traceharbor.util.TraceHarborLog
import com.kernelflux.traceharbor.xlog.XLogNative
import java.io.File
import java.util.HashSet

class TraceHarborBacktrace private constructor() {
    private var initialized = false
    private var configured = false
    private var configuration: Configuration? = null
    private val warmUpDelegate = WarmUpDelegate()
    private val handler = Handler(Looper.getMainLooper())
    private var scheduleQutGenerationRequestsRunning = false

    fun isBacktraceThreadBlocked(): Boolean {
        return warmUpDelegate.isBacktraceThreadBlocked()
    }

    @Synchronized
    fun configure(context: Context): Configuration {
        val existing = configuration
        if (existing != null) {
            return existing
        }
        return Configuration(context, this).also {
            configuration = it
            initialized = true
        }
    }

    fun getSavingPath(): String? {
        return warmUpDelegate.mSavingPath
    }

    private fun requestQutGenerate() {
        if (!initialized || !configured) {
            return
        }
        warmUpDelegate.requestConsuming()
    }

    private fun startScheduleQutGenerationRequests() {
        if (scheduleQutGenerationRequestsRunning) {
            return
        }
        scheduleQutGenerationRequestsRunning = false
        handler.postDelayed(
            {
                requestQutGenerate()
                scheduleQutGenerationRequestsRunning = false
                startScheduleQutGenerationRequests()
            },
            6 * 3600 * 1000L
        ) // per 6 hour.
    }

    private fun dealWithCoolDown(configuration: Configuration) {
        if (configuration.mIsWarmUpProcess) {
            val markFile = WarmUpUtility.warmUpMarkedFile(configuration.mContext)
            if (configuration.mCoolDownIfApkUpdated && markFile.exists()) {
                val content = WarmUpUtility.readFileContent(markFile, 4096)
                if (content == null) {
                    configuration.mCoolDown = true
                } else {
                    val lastNativeLibraryPath = content.split("\n")[0]
                    if (!lastNativeLibraryPath.equals(configuration.mContext.applicationInfo.nativeLibraryDir, ignoreCase = true)) {
                        TraceHarborLog.i(TAG, "Apk updated, remove warmed-up file.")
                        configuration.mCoolDown = true
                    }
                }
            }
            if (configuration.mCoolDown) {
                markFile.delete()
                WarmUpUtility.unfinishedFile(configuration.mContext).delete()
            }
        }
    }

    private fun runningInIsolateProcess(configuration: Configuration): Boolean {
        val processName = ProcessUtil.getProcessNameByPid(configuration.mContext)
        return processName.endsWith(ISOLATE_PROCESS_SUFFIX)
    }

    private fun configure(configuration: Configuration) {
        if (runningInIsolateProcess(configuration)) {
            TraceHarborLog.i(TAG, "Isolate process does not need any configuration.")
            return
        }

        if (configuration.mWarmUpInIsolateProcess && configuration.mLibraryLoader != null) {
            throw ConfigurationException("Custom library loader is not supported in isolate process warm-up mode.")
        }

        // Load backtrace library.
        loadLibrary(configuration.mLibraryLoader)

        // Init xlog
        XLogNative.setXLogger(configuration.mPathOfXLogSo)

        // Enable log
        enableLogger(configuration.mEnableLog)

        TraceHarborLog.i(TAG, configuration.toString())

        if (configuration.mBacktraceMode == Mode.Fp || configuration.mBacktraceMode == Mode.Dwarf) {
            TraceHarborBacktraceNative.setBacktraceMode(configuration.mBacktraceMode.value)
        }

        if (configuration.mBacktraceMode == Mode.Quicken ||
            configuration.mBacktraceMode == Mode.FpUntilQuickenWarmedUp ||
            configuration.mBacktraceMode == Mode.DwarfUntilQuickenWarmedUp ||
            configuration.mQuickenAlwaysOn
        ) {
            // Init saving path
            var savingPath = WarmUpUtility.validateSavingPath(configuration)
            TraceHarborLog.i(TAG, "Set saving path: %s", savingPath)
            File(savingPath).mkdirs()
            if (!savingPath.endsWith(File.separator)) {
                savingPath += File.separator
            }
            warmUpDelegate.setSavingPath(savingPath)

            // Remove warm up marked file if cool down is set.
            dealWithCoolDown(configuration)

            // Prepare warm-up logic.
            warmUpDelegate.prepare(configuration)

            // Set backtrace mode
            val hasWarmedUp = WarmUpUtility.hasWarmedUp(configuration.mContext)
            if (configuration.mBacktraceMode == Mode.Quicken || !configuration.mQuickenAlwaysOn) {
                var mode = Mode.Quicken
                if (!hasWarmedUp) {
                    if (configuration.mBacktraceMode == Mode.FpUntilQuickenWarmedUp) {
                        mode = Mode.Fp
                    } else if (configuration.mBacktraceMode == Mode.DwarfUntilQuickenWarmedUp) {
                        mode = Mode.Dwarf
                    }
                }
                TraceHarborBacktraceNative.setBacktraceMode(mode.value)
            }

            TraceHarborLog.i(TAG, "Has warmed up: %s", hasWarmedUp)

            // Set warmed up flag
            TraceHarborBacktraceNative.setWarmedUp(hasWarmedUp)

            startScheduleQutGenerationRequests()

            // Register warmed up receiver for other processes.
            if (!configuration.mIsWarmUpProcess) {
                warmUpDelegate.registerWarmedUpReceiver(configuration, configuration.mBacktraceMode)
            }
        }

        configured = true
    }

    interface LibraryLoader {
        fun load(library: String)
    }

    class ConfigurationException(message: String) : RuntimeException(message)

    enum class Mode(@JvmField val value: Int) {
        Fp(0),
        Quicken(1),
        Dwarf(2),
        FpUntilQuickenWarmedUp(3),
        DwarfUntilQuickenWarmedUp(4);

        override fun toString(): String {
            return when (this) {
                Fp -> "FramePointer-based."
                Quicken -> "WeChat QuickenUnwindTable-based."
                Dwarf -> "Dwarf-based."
                FpUntilQuickenWarmedUp -> "Use fp-based backtrace before quicken has warmed up."
                DwarfUntilQuickenWarmedUp -> "Use dwarf-based backtrace before quicken has warmed up."
            }
        }
    }

    enum class WarmUpTiming {
        WhileScreenOff,
        WhileCharging,
        PostStartup,
    }

    class Configuration internal constructor(
        @JvmField val mContext: Context,
        private val traceHarborBacktrace: TraceHarborBacktrace
    ) {
        @JvmField
        var mSavingPath: String? = null

        @JvmField
        var mWarmUpDirectoriesList: HashSet<String> = HashSet()

        @JvmField
        var mBacktraceMode: Mode = Mode.Quicken

        @JvmField
        var mLibraryLoader: LibraryLoader? = null

        @JvmField
        var mCoolDown = false

        @JvmField
        var mQuickenAlwaysOn = false

        @JvmField
        var mCoolDownIfApkUpdated = true // Default true.

        @JvmField
        var mIsWarmUpProcess = false

        @JvmField
        var mWarmUpInIsolateProcess = true

        @JvmField
        var mWarmUpTiming: WarmUpTiming = WarmUpTiming.WhileScreenOff

        @JvmField
        var mWarmUpDelay: Long = WarmUpScheduler.DELAY_SHORTLY

        @JvmField
        var mEnableLog = false

        @JvmField
        var mEnableIsolateProcessLog = false

        @JvmField
        var mPathOfXLogSo: String? = null

        private var mCommitted = false

        init {
            // Default warm-up
            mWarmUpDirectoriesList.add(mContext.applicationInfo.nativeLibraryDir)
            mWarmUpDirectoriesList.add(getSystemLibraryPath())
            mWarmUpDirectoriesList.add(getBaseODEXPath(mContext))
            mIsWarmUpProcess = ProcessUtil.isMainProcess(mContext)
        }

        /**
         * Set path to save quicken unwind table data.
         */
        fun savingPath(savingPath: String?): Configuration {
            if (mCommitted) {
                return this
            }
            mSavingPath = savingPath
            return this
        }

        /**
         * Backtrace mode, default using Quicken.
         */
        fun setBacktraceMode(mode: Mode?): Configuration {
            if (mCommitted) {
                return this
            }
            if (mode != null) {
                mBacktraceMode = mode
            }
            return this
        }

        /**
         * Always can use quicken unwind no matter which backtrace mode was set.
         */
        fun setQuickenAlwaysOn(): Configuration {
            if (mCommitted) {
                return this
            }
            mQuickenAlwaysOn = true
            return this
        }

        /**
         * Give a custom library loader.
         */
        fun setLibraryLoader(loader: LibraryLoader?): Configuration {
            if (mCommitted) {
                return this
            }
            mLibraryLoader = loader
            return this
        }

        /**
         * Directory contains elf files which will be warmed up later.
         */
        fun directoryToWarmUp(directory: String): Configuration {
            if (mCommitted) {
                return this
            }
            mWarmUpDirectoriesList.add(directory)
            return this
        }

        /**
         * Clear all warm-up dir set.
         */
        fun clearWarmUpDirectorySet(): Configuration {
            if (mCommitted) {
                return this
            }
            mWarmUpDirectoriesList.clear()
            return this
        }

        /**
         * Need force remove warmed-up state.
         */
        fun coolDown(coolDown: Boolean): Configuration {
            if (mCommitted) {
                return this
            }
            mCoolDown = coolDown
            return this
        }

        /**
         * Set if should cool down while apk was updated. Default true.
         */
        fun coolDownIfApkUpdated(ifApkUpdated: Boolean): Configuration {
            if (mCommitted) {
                return this
            }
            mCoolDownIfApkUpdated = ifApkUpdated
            return this
        }

        /**
         * Tell current process is warm-up caller process. It's main process by default.
         */
        fun isWarmUpProcess(isWarmUpProcess: Boolean): Configuration {
            if (mCommitted) {
                return this
            }
            mIsWarmUpProcess = isWarmUpProcess
            return this
        }

        /**
         * Should do real warm up process in isolate process. Default true.
         */
        fun warmUpInIsolateProcess(isolateProcess: Boolean): Configuration {
            if (mCommitted) {
                return this
            }
            mWarmUpInIsolateProcess = isolateProcess
            return this
        }

        /**
         * Warm-up timing.
         */
        fun warmUpSettings(timing: WarmUpTiming, delayMs: Long): Configuration {
            if (mCommitted) {
                return this
            }
            mWarmUpTiming = timing
            mWarmUpDelay = delayMs
            return this
        }

        /**
         * Path of XLogger so. So we can write xlog from native code.
         */
        fun xLoggerPath(pathOfXLogSo: String?): Configuration {
            if (mCommitted) {
                return this
            }
            mPathOfXLogSo = pathOfXLogSo
            return this
        }

        /**
         * Enable logger in processes other than the isolate process.
         */
        fun enableOtherProcessLogger(enable: Boolean): Configuration {
            if (mCommitted) {
                return this
            }
            mEnableLog = enable
            return this
        }

        /**
         * Enable logger in isolate process.
         */
        fun enableIsolateProcessLogger(enable: Boolean): Configuration {
            if (mCommitted) {
                return this
            }
            mEnableIsolateProcessLog = enable
            return this
        }

        /**
         * Commit configurations.
         */
        fun commit() {
            if (mCommitted) {
                return
            }
            mCommitted = true
            traceHarborBacktrace.configure(this)
        }

        override fun toString(): String {
            return "\n" +
                "WeChat backtrace configurations: \n" +
                ">>> Backtrace Mode: $mBacktraceMode\n" +
                ">>> Quicken always on: $mQuickenAlwaysOn\n" +
                ">>> Saving Path: ${mSavingPath ?: WarmUpUtility.defaultSavingPath(this)}\n" +
                ">>> Custom Library Loader: ${mLibraryLoader != null}\n" +
                ">>> Directories to Warm-up: ${mWarmUpDirectoriesList}\n" +
                ">>> Is Warm-up Process: $mIsWarmUpProcess\n" +
                ">>> Warm-up Timing: $mWarmUpTiming\n" +
                ">>> Warm-up Delay: ${mWarmUpDelay}ms\n" +
                ">>> Warm-up in isolate process: $mWarmUpInIsolateProcess\n" +
                ">>> Enable logger: $mEnableLog\n" +
                ">>> Enable Isolate Process logger: $mEnableIsolateProcessLog\n" +
                ">>> Path of XLog: $mPathOfXLogSo\n" +
                ">>> Cool-down: $mCoolDown\n" +
                ">>> Cool-down if Apk Updated: $mCoolDownIfApkUpdated\n"
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.Backtrace"
        private const val SYSTEM_LIBRARY_PATH_Q = "/apex/com.android.runtime/lib/"
        private const val SYSTEM_LIBRARY_PATH_Q_64 = "/apex/com.android.runtime/lib64/"
        private const val SYSTEM_LIBRARY_PATH = "/system/lib/"
        private const val SYSTEM_LIBRARY_PATH_64 = "/system/lib64/"
        private const val SYSTEM_BOOT_OAT_PATH = "/system/framework/arm/"
        private const val SYSTEM_BOOT_OAT_PATH_64 = "/system/framework/arm64/"
        private const val BACKTRACE_LIBRARY_NAME = "traceharbor-backtrace"

        @JvmField
        val ISOLATE_PROCESS_SUFFIX = ":backtrace__"

        private var libraryLoaded = false

        @JvmStatic
        fun is64BitRuntime(): Boolean {
            val currRuntimeABI = Build.CPU_ABI
            return "arm64-v8a".equals(currRuntimeABI, ignoreCase = true) ||
                "x86_64".equals(currRuntimeABI, ignoreCase = true) ||
                "mips64".equals(currRuntimeABI, ignoreCase = true)
        }

        @JvmStatic
        fun getSystemLibraryPath(): String {
            return if (Build.VERSION.SDK_INT >= 29) {
                if (!is64BitRuntime()) SYSTEM_LIBRARY_PATH_Q else SYSTEM_LIBRARY_PATH_Q_64
            } else {
                if (!is64BitRuntime()) SYSTEM_LIBRARY_PATH else SYSTEM_LIBRARY_PATH_64
            }
        }

        @JvmStatic
        fun getSystemFrameworkOATPath(): String {
            return if (!is64BitRuntime()) SYSTEM_BOOT_OAT_PATH else SYSTEM_BOOT_OAT_PATH_64
        }

        @JvmStatic
        fun getBaseODEXPath(context: Context): String {
            val abiName = if (!is64BitRuntime()) "arm" else "arm64"
            val appLibDirParent = File(context.applicationInfo.nativeLibraryDir).parentFile
            val baseDir = appLibDirParent?.parentFile ?: appLibDirParent ?: context.filesDir
            return File(
                baseDir,
                "/oat/$abiName/base.odex"
            ).absolutePath
        }

        /**
         * Warm-up Reporter. Will emit several statistic information.
         */
        @JvmStatic
        fun setReporter(reporter: WarmUpReporter?) {
            WarmUpDelegate.sReporter = reporter
        }

        private object Singleton {
            val INSTANCE = TraceHarborBacktrace()
        }

        @JvmStatic
        fun instance(): TraceHarborBacktrace = Singleton.INSTANCE

        @JvmStatic
        fun loadLibrary(loader: LibraryLoader?) {
            if (libraryLoaded) {
                return
            }
            if (loader == null) {
                loadLibrary()
            } else {
                TraceHarborLog.i(TAG, "Using custom library loader: %s.", loader)
                loader.load(BACKTRACE_LIBRARY_NAME)
            }
            libraryLoaded = true
        }

        // Invoke by warm-up provider
        @JvmStatic
        fun loadLibrary() {
            System.loadLibrary(BACKTRACE_LIBRARY_NAME)
        }

        @JvmStatic
        fun enableLogger(enableLogger: Boolean) {
            TraceHarborBacktraceNative.enableLogger(enableLogger)
        }

        @JvmStatic
        fun hasWarmedUp(context: Context): Boolean {
            return WarmUpUtility.hasWarmedUp(context)
        }

        @JvmStatic
        fun doStatistic(pathOfSo: String?): IntArray? {
            return TraceHarborBacktraceNative.statistic(pathOfSo)
        }
    }
}

