package com.kernelflux.traceharbor.resource.processor

import com.kernelflux.traceharbor.resource.analyzer.model.DestroyedActivityInfo
import com.kernelflux.traceharbor.resource.config.ResourceConfig
import com.kernelflux.traceharbor.resource.config.SharePluginInfo
import com.kernelflux.traceharbor.resource.watcher.ActivityRefWatcher
import com.kernelflux.traceharbor.util.TraceHarborLog

class NoDumpProcessor(
    watcher: ActivityRefWatcher,
) : BaseLeakProcessor(watcher) {
    override fun process(destroyedActivityInfo: DestroyedActivityInfo): Boolean {
        // Lightweight mode, just report leaked activity name.
        TraceHarborLog.i(TAG, "lightweight mode, just report leaked activity name.")
        watcher.markPublished(destroyedActivityInfo.mActivityName)

        publishIssue(
            SharePluginInfo.IssueType.LEAK_FOUND,
            ResourceConfig.DumpMode.NO_DUMP,
            destroyedActivityInfo.mActivityName,
            destroyedActivityInfo.mKey,
            "no dump",
            "0",
        )
        return true
    }

    companion object {
        private const val TAG = "TraceHarbor.LeakProcessor.NoDump"
    }
}

