package com.kernelflux.traceharbor.resource.processor

import android.os.Build
import com.kernelflux.traceharbor.resource.MemoryUtil
import com.kernelflux.traceharbor.resource.analyzer.model.ActivityLeakResult
import com.kernelflux.traceharbor.resource.analyzer.model.DestroyedActivityInfo
import com.kernelflux.traceharbor.resource.config.ResourceConfig
import com.kernelflux.traceharbor.resource.config.SharePluginInfo
import com.kernelflux.traceharbor.resource.dumper.HprofFileManager
import com.kernelflux.traceharbor.resource.watcher.ActivityRefWatcher
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.io.File
import java.io.FileNotFoundException

class ForkAnalyseProcessor(
    watcher: ActivityRefWatcher,
) : BaseLeakProcessor(watcher) {
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
                ResourceConfig.DumpMode.FORK_ANALYSE,
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
            hprof = HprofFileManager.prepareHprofFile("FAP", true)
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
                ResourceConfig.DumpMode.FORK_ANALYSE,
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

        try {
            val analyseStart = System.currentTimeMillis()
            val leaks: ActivityLeakResult = analyze(hprof, key)
            TraceHarborLog.i(
                TAG,
                "analyze cost=%sms refString=%s",
                System.currentTimeMillis() - analyseStart,
                key,
            )
            if (leaks.mLeakFound) {
                val leakChain = leaks.toString()
                publishIssue(
                    SharePluginInfo.IssueType.LEAK_FOUND,
                    ResourceConfig.DumpMode.FORK_ANALYSE,
                    activity,
                    key,
                    leakChain,
                    (System.currentTimeMillis() - dumpStart).toString(),
                )
                TraceHarborLog.i(TAG, leakChain)
            } else {
                TraceHarborLog.i(TAG, "leak not found")
            }
        } catch (error: OutOfMemoryError) {
            publishIssue(
                SharePluginInfo.IssueType.ERR_ANALYSE_OOM,
                ResourceConfig.DumpMode.FORK_ANALYSE,
                activity,
                key,
                "OutOfMemoryError",
                "0",
            )
            TraceHarborLog.printErrStackTrace(TAG, error, "")
        } finally {
            hprof.delete()
        }
        return true
    }

    companion object {
        private const val TAG = "TraceHarbor.LeakProcessor.ForkAnalyse"
    }
}

