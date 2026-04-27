package com.kernelflux.traceharbor.memguard

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Process
import androidx.annotation.Keep
import com.kernelflux.traceharbor.hook.AbsHook
import com.kernelflux.traceharbor.hook.memory.MemoryHook
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FilenameFilter
import java.util.Arrays
import java.util.Collections
import java.util.Objects

class MemGuard private constructor() {
    interface NativeLibLoader {
        fun loadLibrary(libraryName: String)
    }

    interface IssueCallback {
        @Throws(Throwable::class)
        fun onIssueDumpped(dumpFile: String)
    }

    class Options private constructor() {
        /**
         * Max allocation size MemGuard can detect its under/overflow issues.
         */
        @Keep
        @JvmField
        var maxAllocationSize: Int = 0

        /**
         * Max allocation count MemGuard can detect its under/overflow issues.
         */
        @Keep
        @JvmField
        var maxDetectableAllocationCount: Int = 0

        /**
         * Max skipped allocation count between two guarded allocations.
         */
        @Keep
        @JvmField
        var maxSkippedAllocationCount: Int = 0

        /**
         * Probability of putting guard page on the left side of specific pointer.
         */
        @Keep
        @JvmField
        var percentageOfLeftSideGuard: Int = 0

        /**
         * Whether MemGuard should return a pointer with guard page on right side without gaps.
         */
        @Keep
        @JvmField
        var perfectRightSideGuard: Boolean = false

        /**
         * Whether MemGuard should regard overlapped reading as an issue.
         */
        @Keep
        @JvmField
        var ignoreOverlappedReading: Boolean = false

        /**
         * Path to write dump file when memory issue was detected.
         */
        @Keep
        @JvmField
        var issueDumpFilePath: String? = null

        /**
         * Patterns described by RegEx of target libs that we want to detect any memory issues.
         */
        @Keep
        @JvmField
        var targetSOPatterns: Array<String> = emptyArray()

        /**
         * Patterns described by RegEx of target libs that we want to skip.
         */
        @Keep
        @JvmField
        var ignoredSOPatterns: Array<String> = emptyArray()

        override fun toString(): String {
            return "Options{" +
                "maxAllocationSize=$maxAllocationSize" +
                ", maxDetectableAllocationCount=$maxDetectableAllocationCount" +
                ", maxSkippedAllocationCount=$maxSkippedAllocationCount" +
                ", percentageOfLeftSideGuard=$percentageOfLeftSideGuard" +
                ", perfectRightSideGuard=$perfectRightSideGuard" +
                ", ignoreOverlappedReading=$ignoreOverlappedReading" +
                ", issueDumpFilePath=$issueDumpFilePath" +
                ", targetSOPatterns=" + Arrays.toString(targetSOPatterns) +
                ", ignoredSOPatterns=" + Arrays.toString(ignoredSOPatterns) +
                '}'
        }

        class Builder(context: Context) {
            private var contextRef: Context = if (context is Activity) {
                context.applicationContext
            } else {
                context
            }

            private var maxAllocationSize = DEFAULT_MAX_ALLOCATION_SIZE
            private var maxDetectableAllocationCount = DEFAULT_MAX_DETECTABLE_ALLOCATION_COUNT
            private var maxSkippedAllocationCount = DEFAULT_MAX_SKIPPED_ALLOCATION_COUNT
            private var percentageOfLeftSideGuard = DEFAULT_PERCENTAGE_OF_LEFT_SIDE_GUARD
            private var perfectRightSideGuard = DEFAULT_PERFECT_RIGHT_SIDE_GUARD
            private var ignoreOverlappedReading = DEFAULT_IGNORE_OVERLAPPED_READING
            private var issueDumpFileDir: String? = getDefaultIssueDumpDir(context)
            private val targetSOPatterns = ArrayList<String>()
            private val ignoredSOPatterns = ArrayList<String>()

            /**
             * @see Options.maxAllocationSize
             */
            fun getMaxAllocationSize(): Int = maxAllocationSize

            /**
             * @see Options.maxAllocationSize
             */
            fun setMaxDetectableSize(value: Int): Builder {
                maxAllocationSize = value
                return this
            }

            /**
             * @see Options.maxDetectableAllocationCount
             */
            fun getMaxDetectableAllocationCount(): Int = maxDetectableAllocationCount

            /**
             * @see Options.maxDetectableAllocationCount
             */
            fun setMaxDetectableAllocationCount(value: Int): Builder {
                maxDetectableAllocationCount = value
                return this
            }

            /**
             * @see Options.maxSkippedAllocationCount
             */
            fun getMaxSkippedAllocationCount(): Int = maxSkippedAllocationCount

            /**
             * @see Options.maxSkippedAllocationCount
             */
            fun setMaxSkippedAllocationCount(value: Int): Builder {
                maxSkippedAllocationCount = value
                return this
            }

            /**
             * @see Options.percentageOfLeftSideGuard
             */
            fun getPercentageOfLeftSideGuard(): Int = percentageOfLeftSideGuard

            /**
             * @see Options.percentageOfLeftSideGuard
             */
            fun setPercentageOfLeftSideGuard(value: Int): Builder {
                percentageOfLeftSideGuard = value
                return this
            }

            /**
             * @see Options.perfectRightSideGuard
             */
            fun isPerfectRightSideGuard(): Boolean = perfectRightSideGuard

            /**
             * @see Options.perfectRightSideGuard
             */
            fun setIsPerfectRightSideGuard(value: Boolean): Builder {
                perfectRightSideGuard = value
                return this
            }

            /**
             * @see Options.ignoreOverlappedReading
             */
            fun isIgnoreOverlappedReading(): Boolean = ignoreOverlappedReading

            /**
             * @see Options.ignoreOverlappedReading
             */
            fun setIsIgnoreOverlappedReading(value: Boolean): Builder {
                ignoreOverlappedReading = value
                return this
            }

            /**
             * @see Options.issueDumpFilePath
             */
            fun getIssueDumpFileDir(): String? = issueDumpFileDir

            /**
             * @see Options.issueDumpFilePath
             */
            fun setIssueDumpFileDir(value: String?): Builder {
                issueDumpFileDir = value
                return this
            }

            /**
             * @see Options.targetSOPatterns
             */
            fun getTargetSOPatterns(): List<String> = Collections.unmodifiableList(targetSOPatterns)

            /**
             * @see Options.targetSOPatterns
             */
            fun setTargetSOPattern(value: String, vararg nextValues: String): Builder {
                targetSOPatterns.clear()
                targetSOPatterns.add(value)
                targetSOPatterns.addAll(nextValues)
                return this
            }

            /**
             * @see Options.ignoredSOPatterns
             */
            fun getIgnoredSOPatterns(): List<String> = Collections.unmodifiableList(ignoredSOPatterns)

            /**
             * @see Options.ignoredSOPatterns
             */
            fun setIgnoredSOPattern(value: String, vararg nextValues: String): Builder {
                ignoredSOPatterns.clear()
                ignoredSOPatterns.add(value)
                ignoredSOPatterns.addAll(nextValues)
                return this
            }

            fun build(): Options {
                val res = Options()
                if (getTargetSOPatterns().isEmpty()) {
                    setTargetSOPattern(DEFAULT_TARGET_SO_PATTERN)
                }
                res.maxAllocationSize = getMaxAllocationSize()
                res.maxDetectableAllocationCount = getMaxDetectableAllocationCount()
                res.maxSkippedAllocationCount = getMaxSkippedAllocationCount()
                res.percentageOfLeftSideGuard = getPercentageOfLeftSideGuard()
                res.perfectRightSideGuard = isPerfectRightSideGuard()
                res.ignoreOverlappedReading = isIgnoreOverlappedReading()
                res.issueDumpFilePath = generateIssueDumpFilePath(contextRef, getIssueDumpFileDir())
                res.targetSOPatterns = getTargetSOPatterns().toTypedArray()
                res.ignoredSOPatterns = getIgnoredSOPatterns().toTypedArray()
                return res
            }
        }

        companion object {
            const val DEFAULT_MAX_ALLOCATION_SIZE = 8 * 1024 // 8K
            const val DEFAULT_MAX_DETECTABLE_ALLOCATION_COUNT = 4096
            const val DEFAULT_MAX_SKIPPED_ALLOCATION_COUNT = 5
            const val DEFAULT_PERCENTAGE_OF_LEFT_SIDE_GUARD = 30
            const val DEFAULT_PERFECT_RIGHT_SIDE_GUARD = false
            const val DEFAULT_IGNORE_OVERLAPPED_READING = false
            const val DEFAULT_TARGET_SO_PATTERN = ".*/lib.*\\.so$"
        }
    }

    companion object {
        private const val TAG = "MemGuard"
        private const val HOOK_COMMON_NATIVE_LIB_NAME = "traceharbor-hookcommon"
        private const val NATIVE_LIB_NAME = "traceharbor-memguard"
        private const val ISSUE_CALLBACK_THREAD_NAME = "MemGuard.IssueCB"
        private const val ISSUE_CALLBACK_TIMEOUT_MS = 5000L
        private const val DEFAULT_DUMP_FILE_EXT = ".txt"

        private val installed = booleanArrayOf(false)

        private var issueCallback: IssueCallback = object : IssueCallback {
            override fun onIssueDumpped(dumpFile: String) {
                val dumpFileObj = File(dumpFile)
                if (!dumpFileObj.exists()) {
                    TraceHarborLog.e(TAG, "Dump file %s does not exist, dump failure ?", dumpFile)
                    return
                }
                var br: BufferedReader? = null
                try {
                    br = BufferedReader(FileReader(dumpFileObj))
                    var line: String?
                    while (br.readLine().also { line = it } != null) {
                        TraceHarborLog.w(TAG, "[DumpedIssue] >> %s", line)
                    }
                } finally {
                    try {
                        br?.close()
                    } catch (_: Throwable) {
                        // Ignored.
                    }
                }
            }
        }

        @JvmStatic
        fun install(opts: Options, issueCallback: IssueCallback?): Boolean {
            return install(opts, null, issueCallback)
        }

        @JvmStatic
        fun install(
            opts: Options,
            soLoader: NativeLibLoader?,
            issueCallback: IssueCallback?
        ): Boolean {
            Objects.requireNonNull(opts)

            synchronized(installed) {
                if (installed[0]) {
                    TraceHarborLog.w(TAG, "Already installed.")
                    return true
                }

                if (MemoryHook.getStatus() == AbsHook.Status.COMMIT_SUCCESS) {
                    TraceHarborLog.w(TAG, "MemoryHook has been committed, skip MemGuard install logic.")
                    return false
                }

                val success = try {
                    if (soLoader != null) {
                        soLoader.loadLibrary(HOOK_COMMON_NATIVE_LIB_NAME)
                        soLoader.loadLibrary(NATIVE_LIB_NAME)
                    } else {
                        System.loadLibrary(HOOK_COMMON_NATIVE_LIB_NAME)
                        System.loadLibrary(NATIVE_LIB_NAME)
                    }

                    if (issueCallback != null) {
                        this.issueCallback = issueCallback
                    }

                    nativeInstall(opts)
                } catch (thr: Throwable) {
                    TraceHarborLog.printErrStackTrace(TAG, thr, "Install MemGuard failed.")
                    false
                }
                if (success) {
                    TraceHarborLog.i(TAG, "Install MemGuard successfully with $opts")
                    MemoryHook.notifyMemGuardInstalled()
                } else {
                    TraceHarborLog.e(TAG, "Install MemGuard failed with $opts")
                }
                installed[0] = success
                return success
            }
        }

        @JvmStatic
        fun isInstalled(): Boolean {
            synchronized(installed) {
                return installed[0]
            }
        }

        @JvmStatic
        fun getLastIssueDumpFilesInDefaultDir(context: Context): List<File> {
            val subFiles = File(getDefaultIssueDumpDir(context)).listFiles(
                FilenameFilter { _: File?, name: String -> name.endsWith(DEFAULT_DUMP_FILE_EXT) }
            )
            return if (subFiles != null) {
                Collections.unmodifiableList(Arrays.asList(*subFiles))
            } else {
                Collections.emptyList()
            }
        }

        @JvmStatic
        private external fun nativeInstall(opts: Options): Boolean

        @JvmStatic
        private external fun nativeGetIssueDumpFilePath(): String?

        @Keep
        @JvmStatic
        private fun c2jNotifyOnIssueDumped(dumpFile: String) {
            val cbThread = Thread(
                {
                    try {
                        issueCallback.onIssueDumpped(dumpFile)
                    } catch (thr: Throwable) {
                        TraceHarborLog.printErrStackTrace(
                            TAG,
                            thr,
                            "Exception was thrown when onIssueDumpped was called."
                        )
                    }
                },
                ISSUE_CALLBACK_THREAD_NAME
            )
            val st = System.currentTimeMillis()
            cbThread.start()
            try {
                cbThread.join(ISSUE_CALLBACK_TIMEOUT_MS)
            } catch (_: InterruptedException) {
                TraceHarborLog.w(TAG, "Issue callback was interrupted.")
            }
            val cost = System.currentTimeMillis() - st
            if (cost > ISSUE_CALLBACK_TIMEOUT_MS) {
                TraceHarborLog.w(TAG, "Timeout when call issue callback.")
            }
        }

        private fun getDefaultIssueDumpDir(context: Context): String {
            val result = File(context.cacheDir, "memguard")
            return result.absolutePath
        }

        private fun getProcessSuffix(context: Context): String {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningProcs = am.runningAppProcesses
            val myUid = Process.myUid()
            val myPid = Process.myPid()
            if (runningProcs != null) {
                for (procInfo in runningProcs) {
                    if (procInfo.uid == myUid && procInfo.pid == myPid) {
                        val colIdx = procInfo.processName.lastIndexOf(':')
                        return if (colIdx >= 0) {
                            procInfo.processName.substring(colIdx + 1)
                        } else {
                            "main"
                        }
                    }
                }
            }
            return "@"
        }

        private fun generateIssueDumpFilePath(context: Context, dirPath: String?): String {
            val safeDir = dirPath ?: getDefaultIssueDumpDir(context)
            return File(
                safeDir,
                "memguard_issue_in_proc_" + getProcessSuffix(context) + "_" + Process.myPid() + ".txt"
            ).absolutePath
        }
    }
}

