package io.traceharbor.resource.processor;

import io.traceharbor.resource.analyzer.model.DestroyedActivityInfo;
import io.traceharbor.resource.analyzer.model.HeapDump;
import io.traceharbor.resource.watcher.ActivityRefWatcher;
import io.traceharbor.util.TraceHarborLog;

import java.io.File;

/**
 * Created by Yves on 2021/3/4
 */
public class AutoDumpProcessor extends BaseLeakProcessor {

    private static final String TAG = "TraceHarbor.LeakProcessor.AutoDump";

    public AutoDumpProcessor(ActivityRefWatcher watcher) {
        super(watcher);
    }

    @Override
    public boolean process(DestroyedActivityInfo destroyedActivityInfo) {
        final File hprofFile = getHeapDumper().dumpHeap(true);
        if (hprofFile != null) {
            getWatcher().markPublished(destroyedActivityInfo.mActivityName);
            getWatcher().triggerGc();
            final HeapDump heapDump = new HeapDump(hprofFile, destroyedActivityInfo.mKey, destroyedActivityInfo.mActivityName);
            getHeapDumpHandler().process(heapDump);
        } else {
            TraceHarborLog.i(TAG, "heap dump for further analyzing activity with key [%s] was failed, just ignore.",
                    destroyedActivityInfo.mKey);
        }
        return true;
    }
}
