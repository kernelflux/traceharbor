package com.kernelflux.traceharbor.resource.processor

import android.os.Build
import com.kernelflux.traceharbor.resource.MemoryUtil
import com.kernelflux.traceharbor.resource.analyzer.model.DestroyedActivityInfo
import com.kernelflux.traceharbor.resource.analyzer.model.HeapDump
import com.kernelflux.traceharbor.resource.config.ResourceConfig
import com.kernelflux.traceharbor.resource.config.SharePluginInfo
import com.kernelflux.traceharbor.resource.dumper.HprofFileManager
import com.kernelflux.traceharbor.resource.watcher.ActivityRefWatcher
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.io.File
import java.io.FileNotFoundException

class ForkDumpProcessor(
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
            TraceHarborLog.e(TAG, "unsupported API version ${Build.VERSION.SDK_INT}")
            return false
        }

        val dumpStart = System.currentTimeMillis()
        var hprof: File? = null
        try {
            hprof = HprofFileManager.prepareHprofFile("FDP", true)
        } catch (e: FileNotFoundException) {
            TraceHarborLog.printErrStackTrace(TAG, e, "")
        }

        if (hprof == null) {
            TraceHarborLog.e(TAG, "cannot create hprof file, just ignore")
            return true
        }

        if (!MemoryUtil.dump(hprof.path, 600)) {
            TraceHarborLog.e(
                TAG,
                "heap dump for further analyzing activity with key [%s] was failed, just ignore.",
                destroyedActivityInfo.mKey,
            )
            return true
        }

        TraceHarborLog.i(
            TAG,
            "dump cost=%sms refString=%s path=%s",
            System.currentTimeMillis() - dumpStart,
            destroyedActivityInfo.mKey,
            hprof.path,
        )

        watcher.markPublished(destroyedActivityInfo.mActivityName)
        watcher.triggerGc()
        heapDumpHandler.process(HeapDump(hprof, destroyedActivityInfo.mKey, destroyedActivityInfo.mActivityName))
        return true
    }

    companion object {
        private const val TAG = "TraceHarbor.LeakProcessor.ForkDump"
    }
}

