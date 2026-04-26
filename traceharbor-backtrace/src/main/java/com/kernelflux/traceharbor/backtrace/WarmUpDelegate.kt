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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.OperationCanceledException
import android.os.Process
import android.system.ErrnoException
import android.system.Os
import com.kernelflux.traceharbor.backtrace.WarmUpScheduler.TaskType
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.io.File
import java.io.FileFilter
import java.io.IOException
import java.util.HashMap
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.CancellationException

class WarmUpDelegate {
    private var isolateRemote = false

    @JvmField
    var mSavingPath: String? = null

    private var warmedUpReceiver: WarmedUpReceiver? = null
    private var threadTaskExecutor: ThreadTaskExecutor? = null
    private lateinit var warmUpScheduler: WarmUpScheduler
    private lateinit var configuration: TraceHarborBacktrace.Configuration
    private val prepared = booleanArrayOf(false)

    fun prepare(configuration: TraceHarborBacktrace.Configuration) {
        synchronized(prepared) {
            if (prepared[0]) {
                return
            }
            prepared[0] = true
        }

        this.configuration = configuration
        isolateRemote = configuration.mWarmUpInIsolateProcess
        threadTaskExecutor = ThreadTaskExecutor("TraceHarborBacktraceTask")
        warmUpScheduler = WarmUpScheduler(this, configuration.mContext, configuration.mWarmUpTiming, configuration.mWarmUpDelay)

        if (configuration.mIsWarmUpProcess) {
            val context = configuration.mContext
            if (!WarmUpUtility.hasWarmedUp(context)) {
                TraceHarborLog.i(TAG, "Has not been warmed up")
                warmUpScheduler.scheduleTask(TaskType.WarmUp)
            }
            if (WarmUpUtility.needCleanUp(context)) {
                TraceHarborLog.i(TAG, "Need clean up")
                warmUpScheduler.scheduleTask(TaskType.CleanUp)
            }
            if (WarmUpUtility.shouldComputeDiskUsage(context)) {
                TraceHarborLog.i(TAG, "Should schedule disk usage task.")
                warmUpScheduler.scheduleTask(TaskType.DiskUsage)
            }
        }
    }

    fun requestConsuming() {
        if (!WarmUpUtility.hasWarmedUp(configuration.mContext)) {
            return
        }
        warmUpScheduler.scheduleTask(TaskType.RequestConsuming)
    }

    fun isBacktraceThreadBlocked(): Boolean {
        return threadTaskExecutor?.isThreadBlocked() ?: true
    }

    fun setSavingPath(savingPath: String?) {
        mSavingPath = savingPath
        TraceHarborBacktraceNative.setSavingPath(savingPath)
    }

    private class RemoteWarmUpInvoker(private val savingPath: String?) : WarmUpInvoker, WarmUpService.RemoteConnection {
        private val impl = WarmUpService.RemoteInvokerImpl()
        private lateinit var context: Context
        private lateinit var args: Bundle

        override fun isConnected(): Boolean = impl.isConnected()

        override fun connect(context: Context, args: Bundle): Boolean {
            this.context = context
            this.args = args
            return impl.connect(context, args)
        }

        override fun disconnect(context: Context) {
            impl.disconnect(context)
        }

        override fun warmUp(pathOfElf: String?, offset: Int): Boolean {
            if (!isConnected()) {
                if (!connect(context, args)) {
                    return false
                }
            }
            val invokeArgs = Bundle().apply {
                putString(WarmUpService.ARGS_WARM_UP_SAVING_PATH, savingPath)
                putString(WarmUpService.ARGS_WARM_UP_PATH_OF_ELF, pathOfElf)
                putInt(WarmUpService.ARGS_WARM_UP_ELF_START_OFFSET, offset)
            }
            val result = impl.call(WarmUpService.CMD_WARM_UP_SINGLE_ELF_FILE, invokeArgs)
            val retCode = result?.getInt(WarmUpService.RESULT_OF_WARM_UP) ?: -100
            val ret = retCode == WarmUpService.OK
            if (ret) {
                TraceHarborBacktraceNative.notifyWarmedUp(pathOfElf, offset)
            }
            TraceHarborLog.i(TAG, "Warm-up %s:%s - retCode %s", pathOfElf, offset, retCode)
            return ret
        }
    }

    private class LocalWarmUpInvoker : WarmUpInvoker {
        override fun warmUp(pathOfElf: String?, offset: Int): Boolean {
            return internalWarmUpSoPath(pathOfElf, offset, false)
        }
    }

    private fun acquireWarmUpInvoker(): WarmUpInvoker? {
        return if (isolateRemote) {
            val invoker = RemoteWarmUpInvoker(mSavingPath)
            val args = Bundle().apply {
                putBoolean(WarmUpService.BIND_ARGS_ENABLE_LOGGER, configuration.mEnableIsolateProcessLog)
                putString(WarmUpService.BIND_ARGS_PATH_OF_XLOG_SO, configuration.mPathOfXLogSo)
            }
            if (invoker.connect(configuration.mContext, args)) invoker else null
        } else {
            LocalWarmUpInvoker()
        }
    }

    private fun releaseWarmUpInvoker(invoker: WarmUpInvoker) {
        if (isolateRemote) {
            (invoker as RemoteWarmUpInvoker).disconnect(configuration.mContext)
        }
    }

    @Synchronized
    fun registerWarmedUpReceiver(configuration: TraceHarborBacktrace.Configuration, mode: TraceHarborBacktrace.Mode) {
        if (WarmUpUtility.hasWarmedUp(configuration.mContext)) {
            return
        }

        if (warmedUpReceiver == null) {
            warmedUpReceiver = WarmedUpReceiver(mode)
        } else {
            return
        }

        TraceHarborLog.i(TAG, "Register warm-up receiver.")
        val filter = IntentFilter().apply { addAction(ACTION_WARMED_UP) }
        configuration.mContext.registerReceiver(
            warmedUpReceiver,
            filter,
            configuration.mContext.packageName + PERMISSION_WARMED_UP,
            null
        )
    }

    private fun broadcastWarmedUp(context: Context) {
        try {
            val warmedUpFile = WarmUpUtility.warmUpMarkedFile(context)
            warmedUpFile.createNewFile()
            WarmUpUtility.writeContentToFile(warmedUpFile, context.applicationInfo.nativeLibraryDir)
        } catch (e: IOException) {
            TraceHarborLog.printErrStackTrace(TAG, e, "")
        }

        TraceHarborBacktraceNative.setWarmedUp(true)
        updateBacktraceMode(configuration.mBacktraceMode)

        TraceHarborLog.i(TAG, "Broadcast warmed up message to other processes.")
        val intent = Intent(ACTION_WARMED_UP).apply {
            putExtra("pid", Process.myPid())
        }
        context.sendBroadcast(intent, context.packageName + PERMISSION_WARMED_UP)

        sReporter?.onReport(WarmUpReporter.ReportEvent.WarmedUp)
    }

    fun warmingUp(cs: CancellationSignal) {
        threadTaskExecutor?.arrangeTask(
            Runnable {
                TraceHarborLog.i(TAG, "Going to warm up.")
                var cancelled = false
                var invoker: WarmUpInvoker? = null
                try {
                    if (!File(WarmUpUtility.validateSavingPath(configuration)).isDirectory) {
                        TraceHarborLog.w(TAG, "Saving path is not a directory.")
                        warmUpScheduler.taskFinished(TaskType.WarmUp)
                        return@Runnable
                    }

                    invoker = acquireWarmUpInvoker()
                    if (invoker == null) {
                        TraceHarborLog.w(TAG, "Failed to acquire warm-up invoker")
                        return@Runnable
                    }

                    val invokerFinal = invoker
                    for (directory in configuration.mWarmUpDirectoriesList) {
                        val dir = File(directory)
                        WarmUpUtility.iterateTargetDirectory(dir, cs, FileFilter { file ->
                            val absolutePath = file.absolutePath
                            val offset = 0
                            if (file.exists() &&
                                !warmUpBlocked(absolutePath, offset) &&
                                (absolutePath.endsWith(".so")
                                    || absolutePath.endsWith(".odex")
                                    || absolutePath.endsWith(".oat")
                                    || absolutePath.endsWith(".dex"))
                            ) {
                                TraceHarborLog.i(TAG, "Warming up so %s", absolutePath)
                                val ret = invokerFinal.warmUp(absolutePath, offset)
                                if (!ret) {
                                    warmUpFailed(absolutePath, offset)
                                }
                            }
                            false
                        })
                    }
                } catch (_: OperationCanceledException) {
                    cancelled = true
                } catch (t: Throwable) {
                    TraceHarborLog.printErrStackTrace(TAG, t, "")
                } finally {
                    if (invoker != null) {
                        releaseWarmUpInvoker(invoker)
                    }
                }

                if (!cancelled) {
                    warmUpScheduler.taskFinished(TaskType.WarmUp)
                    broadcastWarmedUp(configuration.mContext)
                    TraceHarborLog.i(TAG, "Warm-up done.")
                } else {
                    TraceHarborLog.i(TAG, "Warm-up cancelled.")
                }
            },
            TASK_TAG_WARM_UP
        )
    }

    fun cleaningUp(cs: CancellationSignal) {
        threadTaskExecutor?.arrangeTask(
            Runnable {
                val savingDir = File(WarmUpUtility.validateSavingPath(configuration))
                TraceHarborLog.i(TAG, "Going to clean up saving path(%s)..", savingDir.absoluteFile)

                if (!savingDir.isDirectory) {
                    warmUpScheduler.taskFinished(TaskType.CleanUp)
                    return@Runnable
                }

                val visitedFiles = HashMap<String, Pair<File, Long>>()
                var cancelled = false
                try {
                    WarmUpUtility.iterateTargetDirectory(savingDir, cs, FileFilter { pathname ->
                        try {
                            val filename = pathname.name
                            val absolutePath = pathname.absolutePath

                            if (filename.contains("_malformed_") || filename.contains("_temp_")) {
                                if (System.currentTimeMillis() - pathname.lastModified() >= WarmUpUtility.DURATION_CLEAN_UP_EXPIRED) {
                                    TraceHarborLog.i(TAG, "Delete malformed and temp file %s", absolutePath)
                                    pathname.delete()
                                }
                            } else {
                                val stat = Os.lstat(absolutePath)
                                val lastAccessTime = maxOf(stat.st_atime, stat.st_mtime) * 1000L
                                TraceHarborLog.i(TAG, "File(%s) last access time %s", absolutePath, lastAccessTime)

                                if ((System.currentTimeMillis() - lastAccessTime) > WarmUpUtility.DURATION_LAST_ACCESS_EXPIRED) {
                                    pathname.delete()
                                    TraceHarborLog.i(TAG, "Delete long time no access file(%s)", absolutePath)
                                } else if (lastAccessTime >= System.currentTimeMillis()) {
                                    if ((lastAccessTime - System.currentTimeMillis()) >= WarmUpUtility.DURATION_LAST_ACCESS_FAR_FUTURE) {
                                        pathname.delete()
                                        TraceHarborLog.i(TAG, "Delete future file(%s)", absolutePath)
                                    }
                                } else {
                                    val indexOfDot = filename.lastIndexOf('.')
                                    if (indexOfDot == -1) {
                                        return@FileFilter false
                                    }
                                    val elfName = filename.substring(0, indexOfDot)
                                    if (filename.endsWith(".hash")) {
                                        return@FileFilter false
                                    }
                                    val pair = visitedFiles[elfName]
                                    if (pair != null) {
                                        if (lastAccessTime > pair.second) {
                                            if (System.currentTimeMillis() - pair.second >= WarmUpUtility.DURATION_CLEAN_UP_EXPIRED) {
                                                pair.first.delete()
                                                TraceHarborLog.i(
                                                    TAG,
                                                    "Delete file(%s) cause %s is newer(%s vs %s).",
                                                    pair.first.name,
                                                    filename,
                                                    pair.second,
                                                    lastAccessTime
                                                )
                                            }
                                            visitedFiles[elfName] = Pair(pathname, lastAccessTime)
                                        } else {
                                            if (System.currentTimeMillis() - lastAccessTime >= WarmUpUtility.DURATION_CLEAN_UP_EXPIRED) {
                                                pathname.delete()
                                                TraceHarborLog.i(
                                                    TAG,
                                                    "Delete file(%s) cause %s is newer(%s vs %s).",
                                                    filename,
                                                    pair.first.name,
                                                    lastAccessTime,
                                                    pair.second
                                                )
                                            }
                                        }
                                    } else {
                                        visitedFiles[elfName] = Pair(pathname, lastAccessTime)
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            TraceHarborLog.printErrStackTrace(TAG, e, "")
                        }
                        false
                    })
                } catch (_: OperationCanceledException) {
                    cancelled = true
                } catch (t: Throwable) {
                    TraceHarborLog.printErrStackTrace(TAG, t, "")
                }

                if (!cancelled) {
                    WarmUpUtility.markCleanUpTimestamp(configuration.mContext)
                    warmUpScheduler.taskFinished(TaskType.CleanUp)
                    TraceHarborLog.i(TAG, "Clean up saving path(%s) done.", savingDir.absoluteFile)
                    sReporter?.onReport(WarmUpReporter.ReportEvent.CleanedUp)
                } else {
                    TraceHarborLog.i(TAG, "Clean up saving path(%s) cancelled.", savingDir.absoluteFile)
                }
            },
            TASK_TAG_CLEAN_UP
        )
    }

    fun consumingRequestedQut(cs: CancellationSignal?) {
        threadTaskExecutor?.arrangeTask(
            Runnable {
                TraceHarborLog.i(TAG, "Going to consume requested QUT.")

                val arrayOfRequesting = TraceHarborBacktraceNative.consumeRequestedQut() ?: emptyArray()
                val invoker = acquireWarmUpInvoker()
                if (invoker == null) {
                    warmUpScheduler.taskFinished(TaskType.RequestConsuming)
                    TraceHarborLog.w(TAG, "Failed to acquire warm-up invoker.")
                    return@Runnable
                }

                try {
                    for (path in arrayOfRequesting) {
                        val index = path.lastIndexOf(':')
                        var pathOfElf = path
                        var offset = 0
                        if (index != -1) {
                            try {
                                pathOfElf = path.substring(0, index)
                                offset = path.substring(index + 1).toInt()
                            } catch (_: Throwable) {
                            }
                        }
                        var ret = false
                        if (!warmUpBlocked(pathOfElf, offset)) {
                            ret = invoker.warmUp(pathOfElf, offset)
                            if (!ret) {
                                warmUpFailed(pathOfElf, offset)
                            }
                        }
                        TraceHarborLog.i(TAG, "Consumed requested QUT -> %s, ret = %s.", path, ret)
                        if (cs?.isCanceled == true) {
                            TraceHarborLog.i(TAG, "Consume requested QUT canceled.")
                            break
                        }
                    }
                    TraceHarborLog.i(TAG, "Consume requested QUT done.")
                } finally {
                    releaseWarmUpInvoker(invoker)
                    warmUpScheduler.taskFinished(TaskType.RequestConsuming)
                }
            },
            TASK_TAG_CONSUMING_UP
        )
    }

    fun computeDiskUsage(cs: CancellationSignal) {
        threadTaskExecutor?.arrangeTask(
            Runnable {
                val file = File(mSavingPath.orEmpty())
                if (!file.isDirectory) {
                    warmUpScheduler.taskFinished(TaskType.DiskUsage)
                    return@Runnable
                }

                val count = longArrayOf(0L, 0L)
                try {
                    WarmUpUtility.iterateTargetDirectory(file, cs, FileFilter { pathname ->
                        try {
                            val stat = Os.lstat(pathname.absolutePath)
                            count[0] = count[0] + 1L
                            count[1] = count[1] + stat.st_blocks * stat.st_blksize
                        } catch (e: ErrnoException) {
                            TraceHarborLog.printErrStackTrace(TAG, e, "")
                        }
                        false
                    })
                } catch (_: CancellationException) {
                    return@Runnable
                } catch (_: OperationCanceledException) {
                    return@Runnable
                } finally {
                    warmUpScheduler.taskFinished(TaskType.DiskUsage)
                    WarmUpUtility.markComputeDiskUsageTimestamp(configuration.mContext)
                    TraceHarborLog.i(TAG, "Compute disk usage, file count(%s), disk usage(%s)", count[0], count[1])
                }

                sReporter?.onReport(WarmUpReporter.ReportEvent.DiskUsage, count[0], count[1])
            },
            TASK_TAG_COMPUTE_DISK_USAGE
        )
    }

    private fun warmUpBlocked(pathOfElf: String, offset: Int): Boolean {
        val blocked = !WarmUpUtility.UnfinishedManagement.check(configuration.mContext, pathOfElf, offset)
        if (blocked) {
            TraceHarborLog.w(TAG, "Elf file %s:%s has blocked and will not do warm-up.", pathOfElf, offset)
        }
        return blocked
    }

    private fun warmUpFailed(pathOfElf: String, offset: Int) {
        sReporter?.onReport(WarmUpReporter.ReportEvent.WarmUpFailed, pathOfElf, offset)
    }

    private class WarmedUpReceiver(private val currentBacktraceMode: TraceHarborBacktrace.Mode) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            TraceHarborLog.i(TAG, "Warm-up received.")
            val action = intent.action ?: return
            if (action == ACTION_WARMED_UP) {
                TraceHarborBacktraceNative.setWarmedUp(true)
                updateBacktraceMode(currentBacktraceMode)
                try {
                    context.unregisterReceiver(this)
                } catch (e: Throwable) {
                    TraceHarborLog.printErrStackTrace(TAG, e, "Unregister receiver twice.")
                }
            }
        }
    }

    class ThreadTaskExecutor(private val threadName: String) : Runnable, Handler.Callback {
        private var threadExecutor: Thread? = null
        private val runnableTasks: HashMap<String, Runnable> = HashMap()
        private val taskQueue: Queue<String> = LinkedList()
        private val blockedChecker = Handler(Looper.getMainLooper(), this)
        private var threadBlocked = false
        private val taskStartTs = longArrayOf(0)

        fun isThreadBlocked(): Boolean = threadBlocked

        fun arrangeTask(runnable: Runnable, tag: String) {
            synchronized(taskQueue) {
                if (taskQueue.contains(tag)) {
                    return
                }
                taskQueue.add(tag)
                runnableTasks[tag] = runnable
            }

            synchronized(this) {
                if (threadExecutor == null || !threadExecutor!!.isAlive) {
                    threadExecutor = Thread(this, threadName).also { it.start() }
                    blockedChecker.removeMessages(MSG_BLOCKED_CHECK)
                    blockedChecker.sendEmptyMessageDelayed(MSG_BLOCKED_CHECK, BLOCKED_CHECK_INTERVAL)
                }
            }
        }

        override fun run() {
            threadBlocked = false
            synchronized(taskStartTs) {
                taskStartTs[0] = System.currentTimeMillis()
            }
            try {
                var runnable: Runnable? = null
                var tag: String? = null
                do {
                    if (runnable != null && tag != null) {
                        val start = System.currentTimeMillis()
                        TraceHarborLog.i(TAG, "Before '%s' task execution..", tag)
                        runnable.run()
                        TraceHarborLog.i(TAG, "After '%s' task execution..", tag)

                        val duration = System.currentTimeMillis() - start
                        val callback = sReporter
                        if (callback != null) {
                            if (TASK_TAG_WARM_UP.equals(tag, ignoreCase = true)) {
                                callback.onReport(WarmUpReporter.ReportEvent.WarmUpDuration, duration)
                            } else if (TASK_TAG_CONSUMING_UP.equals(tag, ignoreCase = true)) {
                                callback.onReport(WarmUpReporter.ReportEvent.ConsumeRequestDuration, duration)
                            }
                        }
                    }

                    synchronized(taskQueue) {
                        tag = taskQueue.poll()
                        if (tag == null) {
                            return
                        }
                        runnable = runnableTasks.remove(tag)
                    }
                } while (runnable != null)
            } finally {
                synchronized(taskStartTs) {
                    taskStartTs[0] = 0
                }
                blockedChecker.removeMessages(MSG_BLOCKED_CHECK)
            }
        }

        override fun handleMessage(msg: Message): Boolean {
            if (msg.what == MSG_BLOCKED_CHECK) {
                synchronized(taskStartTs) {
                    if (taskStartTs[0] == 0L) {
                        return false
                    }
                }
                threadBlocked = true
                sReporter?.onReport(WarmUpReporter.ReportEvent.WarmUpThreadBlocked)
            }
            return false
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.WarmUpDelegate"
        private const val ACTION_WARMED_UP = "action.backtrace.warmed-up"
        private const val PERMISSION_WARMED_UP = ".backtrace.warmed_up"

        private const val TASK_TAG_WARM_UP = "warm-up"
        private const val TASK_TAG_CLEAN_UP = "clean-up"
        private const val TASK_TAG_CONSUMING_UP = "consuming-up"
        private const val TASK_TAG_COMPUTE_DISK_USAGE = "compute-disk-usage"

        private const val MSG_BLOCKED_CHECK = 1
        private const val BLOCKED_CHECK_INTERVAL = 300 * 1000L

        @JvmField
        var sReporter: WarmUpReporter? = null

        @JvmStatic
        fun internalWarmUpSoPath(pathOfSo: String?, elfStartOffset: Int, onlySaveFile: Boolean): Boolean {
            return TraceHarborBacktraceNative.warmUp(pathOfSo, elfStartOffset, onlySaveFile)
        }

        @JvmStatic
        private fun updateBacktraceMode(current: TraceHarborBacktrace.Mode) {
            if (current == TraceHarborBacktrace.Mode.FpUntilQuickenWarmedUp ||
                current == TraceHarborBacktrace.Mode.DwarfUntilQuickenWarmedUp
            ) {
                TraceHarborBacktraceNative.setBacktraceMode(TraceHarborBacktrace.Mode.Quicken.value)
            }
        }
    }
}

