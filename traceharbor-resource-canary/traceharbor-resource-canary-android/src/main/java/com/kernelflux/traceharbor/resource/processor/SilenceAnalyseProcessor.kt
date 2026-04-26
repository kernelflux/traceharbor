package com.kernelflux.traceharbor.resource.processor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.kernelflux.traceharbor.resource.analyzer.model.ActivityLeakResult
import com.kernelflux.traceharbor.resource.analyzer.model.DestroyedActivityInfo
import com.kernelflux.traceharbor.resource.config.ResourceConfig
import com.kernelflux.traceharbor.resource.config.SharePluginInfo
import com.kernelflux.traceharbor.resource.watcher.ActivityRefWatcher
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.io.File

class SilenceAnalyseProcessor(
    watcher: ActivityRefWatcher,
) : BaseLeakProcessor(watcher) {
    private val mReceiver: BroadcastReceiver
    private var isScreenOff = false
    private var isProcessing = false

    init {
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        filter.addAction(Intent.ACTION_SCREEN_ON)
        mReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (Intent.ACTION_SCREEN_OFF == intent.action) {
                        isScreenOff = true
                        TraceHarborLog.i(TAG, "[ACTION_SCREEN_OFF]")
                    } else if (Intent.ACTION_SCREEN_ON == intent.action) {
                        isScreenOff = false
                        TraceHarborLog.i(TAG, "[ACTION_SCREEN_ON]")
                    }
                }
            }

        try {
            watcher.getResourcePlugin().application?.registerReceiver(mReceiver, filter)
        } catch (e: Throwable) {
            TraceHarborLog.printErrStackTrace(TAG, e, "")
        }
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
        return onLeak(destroyedActivityInfo.mActivityName, destroyedActivityInfo.mKey)
    }

    override fun onDestroy() {
        TraceHarborLog.i(TAG, "onDestroy: unregister receiver")
        watcher.getResourcePlugin().application?.unregisterReceiver(mReceiver)
    }

    private fun onLeak(activity: String, refString: String): Boolean {
        TraceHarborLog.i(
            TAG,
            "[onLeak] activity=%s isScreenOff=%s isProcessing=%s",
            activity,
            isScreenOff,
            isProcessing,
        )

        if (watcher.isPublished(activity)) {
            TraceHarborLog.i(TAG, "this activity has been dumped! %s", activity)
            return true
        }

        if (!isProcessing && isScreenOff) {
            isProcessing = true
            watcher.triggerGc()
            val res = dumpAndAnalyse(activity, refString)
            if (res) {
                watcher.markPublished(activity, false)
            }
            isProcessing = false
            return res
        }
        return false
    }

    private fun dumpAndAnalyse(activity: String, refString: String): Boolean {
        val dumpBegin = System.currentTimeMillis()
        val file: File? = heapDumper.dumpHeap(false)
        if (file == null || file.length() <= 0) {
            publishIssue(SharePluginInfo.IssueType.ERR_FILE_NOT_FOUND, activity, refString, "file is null", "0")
            TraceHarborLog.e(TAG, "file is null!")
            return true
        }

        TraceHarborLog.i(
            TAG,
            "dump cost=%sms refString=%s path=%s",
            System.currentTimeMillis() - dumpBegin,
            refString,
            file.absolutePath,
        )

        val analyseBegin = System.currentTimeMillis()
        try {
            val result: ActivityLeakResult = analyze(file, refString)
            TraceHarborLog.i(
                TAG,
                "analyze cost=%sms refString=%s",
                System.currentTimeMillis() - analyseBegin,
                refString,
            )
            val refChain = result.toString()
            if (result.mLeakFound) {
                publishIssue(
                    SharePluginInfo.IssueType.LEAK_FOUND,
                    activity,
                    refString,
                    refChain,
                    (System.currentTimeMillis() - dumpBegin).toString(),
                )
                TraceHarborLog.i(TAG, refChain)
            } else {
                TraceHarborLog.i(TAG, "leak not found")
            }
        } catch (error: OutOfMemoryError) {
            publishIssue(SharePluginInfo.IssueType.ERR_ANALYSE_OOM, activity, refString, "OutOfMemoryError", "0")
            TraceHarborLog.printErrStackTrace(TAG, error, "")
        } finally {
            file.delete()
        }
        return true
    }

    private fun publishIssue(issueType: Int, activity: String, refKey: String, detail: String, cost: String) {
        publishIssue(issueType, ResourceConfig.DumpMode.SILENCE_ANALYSE, activity, refKey, detail, cost)
    }

    companion object {
        private const val TAG = "TraceHarbor.LeakProcessor.SilenceAnalyse"
    }
}

