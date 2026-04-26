package com.kernelflux.traceharbor.resource.processor

import com.kernelflux.traceharbor.resource.analyzer.model.DestroyedActivityInfo
import com.kernelflux.traceharbor.resource.analyzer.model.HeapDump
import com.kernelflux.traceharbor.resource.watcher.ActivityRefWatcher
import com.kernelflux.traceharbor.util.TraceHarborLog

class AutoDumpProcessor(
    watcher: ActivityRefWatcher,
) : BaseLeakProcessor(watcher) {
    override fun process(destroyedActivityInfo: DestroyedActivityInfo): Boolean {
        val hprofFile = heapDumper.dumpHeap(true)
        if (hprofFile != null) {
            watcher.markPublished(destroyedActivityInfo.mActivityName)
            watcher.triggerGc()
            val heapDump = HeapDump(hprofFile, destroyedActivityInfo.mKey, destroyedActivityInfo.mActivityName)
            heapDumpHandler.process(heapDump)
        } else {
            TraceHarborLog.i(
                TAG,
                "heap dump for further analyzing activity with key [%s] was failed, just ignore.",
                destroyedActivityInfo.mKey,
            )
        }
        return true
    }

    companion object {
        private const val TAG = "TraceHarbor.LeakProcessor.AutoDump"
    }
}

