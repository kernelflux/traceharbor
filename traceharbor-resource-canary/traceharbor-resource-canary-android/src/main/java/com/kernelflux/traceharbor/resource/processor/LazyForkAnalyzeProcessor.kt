package com.kernelflux.traceharbor.resource.processor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.kernelflux.traceharbor.resource.MemoryUtil
import com.kernelflux.traceharbor.resource.analyzer.model.ActivityLeakResult
import com.kernelflux.traceharbor.resource.analyzer.model.DestroyedActivityInfo
import com.kernelflux.traceharbor.resource.config.ResourceConfig
import com.kernelflux.traceharbor.resource.config.SharePluginInfo
import com.kernelflux.traceharbor.resource.dumper.HprofFileManager
import com.kernelflux.traceharbor.resource.watcher.ActivityRefWatcher
import com.kernelflux.traceharbor.util.TraceHarborHandlerThread
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.io.File
import java.io.FileNotFoundException
import java.util.Queue
import java.util.concurrent.LinkedBlockingQueue

class LazyForkAnalyzeProcessor(
    watcher: ActivityRefWatcher,
) : BaseLeakProcessor(watcher) {
    private class AnalyzeTask(
        val hprof: File,
        val referenceActivity: String,
        val referenceKey: String,
        val dumpStart: Long,
    )

    @Volatile
    private var isInBackground = false
    private val lazyTasks: Queue<AnalyzeTask> = LinkedBlockingQueue()

    private val analyzeProcessTask =
        Runnable {
            TraceHarborLog.v(
                TAG,
                "analyze task start. background: $isInBackground, queue empty: ${lazyTasks.isEmpty()}",
            )
            while (isInBackground) {
                val task = lazyTasks.poll()
                if (task == null) {
                    TraceHarborLog.v(TAG, "task queue is cleared")
                    break
                }
                analyze(task)
            }
            TraceHarborLog.v(
                TAG,
                "analyze task complete. background: $isInBackground, queue empty: ${lazyTasks.isEmpty()}",
            )
        }

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (Intent.ACTION_SCREEN_OFF == intent.action) {
                    TraceHarborLog.v(TAG, "action screen off")
                    isInBackground = true
                    TraceHarborHandlerThread.getDefaultHandler().post(analyzeProcessTask)
                } else if (Intent.ACTION_SCREEN_ON == intent.action) {
                    TraceHarborLog.v(TAG, "action screen on")
                    isInBackground = false
                    TraceHarborHandlerThread.getDefaultHandler().removeCallbacks(analyzeProcessTask)
                }
            }
        }

    init {
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        watcher.getResourcePlugin().application?.registerReceiver(receiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        watcher.getResourcePlugin().application?.unregisterReceiver(receiver)
    }

    override fun process(destroyedActivityInfo: DestroyedActivityInfo): Boolean {
        publishIssue(
            SharePluginInfo.IssueType.LEAK_FOUND,
            ResourceConfig.DumpMode.NO_DUMP,
            destroyedActivityInfo.mActivityName,
            destroyedActivityInfo.mKey,
            "no dump",
            "0",
        )

        if (Build.VERSION.SDK_INT > ResourceConfig.FORK_DUMP_SUPPORTED_API_GUARD) {
            TraceHarborLog.e(TAG, "cannot fork-dump with unsupported API version ${Build.VERSION.SDK_INT}")
            publishIssue(
                SharePluginInfo.IssueType.ERR_UNSUPPORTED_API,
                ResourceConfig.DumpMode.LAZY_FORK_ANALYZE,
                destroyedActivityInfo.mActivityName,
                destroyedActivityInfo.mKey,
                "Unsupported API",
                "0",
            )
            return false
        }

        watcher.triggerGc()
        return if (dumpAndAnalyse(destroyedActivityInfo.mActivityName, destroyedActivityInfo.mKey)) {
            watcher.markPublished(destroyedActivityInfo.mActivityName, false)
            true
        } else {
            false
        }
    }

    private fun dumpAndAnalyse(activity: String, key: String): Boolean {
        val dumpStart = System.currentTimeMillis()
        var hprof: File? = null
        try {
            hprof = HprofFileManager.prepareHprofFile("LFAP", true)
        } catch (e: FileNotFoundException) {
            TraceHarborLog.printErrStackTrace(TAG, e, "")
        }

        if (hprof != null) {
            if (!MemoryUtil.dump(hprof.path, 600)) {
                TraceHarborLog.e(
                    TAG,
                    "heap dump for further analyzing activity with key [%s] was failed, just ignore.",
                    key,
                )
                return false
            }
        }

        if (hprof == null || hprof.length() == 0L) {
            publishIssue(
                SharePluginInfo.IssueType.ERR_FILE_NOT_FOUND,
                ResourceConfig.DumpMode.LAZY_FORK_ANALYZE,
                activity,
                key,
                "FileNull",
                "0",
            )
            TraceHarborLog.e(TAG, "cannot create hprof file")
            return false
        }

        TraceHarborLog.i(
            TAG,
            "dump cost=%sms refString=%s path=%s",
            System.currentTimeMillis() - dumpStart,
            key,
            hprof.path,
        )
        TraceHarborLog.i(TAG, "dump complete, push task into lazy analyze task queue")
        lazyTasks.add(AnalyzeTask(hprof, activity, key, dumpStart))
        return true
    }

    private fun analyze(task: AnalyzeTask) {
        try {
            val analyseStart = System.currentTimeMillis()
            val leaks: ActivityLeakResult = analyze(task.hprof, task.referenceKey)
            TraceHarborLog.i(
                TAG,
                "analyze cost=%sms refString=%s",
                System.currentTimeMillis() - analyseStart,
                task.referenceKey,
            )

            if (leaks.mLeakFound) {
                val leakChain = leaks.toString()
                publishIssue(
                    SharePluginInfo.IssueType.LEAK_FOUND,
                    ResourceConfig.DumpMode.LAZY_FORK_ANALYZE,
                    task.referenceActivity,
                    task.referenceKey,
                    leakChain,
                    (System.currentTimeMillis() - task.dumpStart).toString(),
                )
                TraceHarborLog.i(TAG, leakChain)
            } else {
                TraceHarborLog.e(TAG, "leak not found")
            }
        } catch (error: OutOfMemoryError) {
            publishIssue(
                SharePluginInfo.IssueType.ERR_ANALYSE_OOM,
                ResourceConfig.DumpMode.LAZY_FORK_ANALYZE,
                task.referenceActivity,
                task.referenceKey,
                "OutOfMemoryError",
                "0",
            )
            TraceHarborLog.printErrStackTrace(TAG, error, "")
        } finally {
            TraceHarborLog.i(TAG, "analyze complete")
            task.hprof.delete()
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.LeakProcessor.LazyForkAnalyze"
    }
}

