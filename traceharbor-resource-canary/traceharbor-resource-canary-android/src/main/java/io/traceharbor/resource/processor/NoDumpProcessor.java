package io.traceharbor.resource.processor;

import io.traceharbor.resource.analyzer.model.DestroyedActivityInfo;
import io.traceharbor.resource.config.ResourceConfig;
import io.traceharbor.resource.config.SharePluginInfo;
import io.traceharbor.resource.watcher.ActivityRefWatcher;
import io.traceharbor.util.TraceHarborLog;

/**
 * Created by Yves on 2021/3/4
 */
public class NoDumpProcessor extends BaseLeakProcessor {

    private static final String TAG = "TraceHarbor.LeakProcessor.NoDump";

    public NoDumpProcessor(ActivityRefWatcher watcher) {
        super(watcher);
    }

    @Override
    public boolean process(DestroyedActivityInfo destroyedActivityInfo) {
        // Lightweight mode, just report leaked activity name.
        TraceHarborLog.i(TAG, "lightweight mode, just report leaked activity name.");
        getWatcher().markPublished(destroyedActivityInfo.mActivityName);

        publishIssue(SharePluginInfo.IssueType.LEAK_FOUND, ResourceConfig.DumpMode.NO_DUMP, destroyedActivityInfo.mActivityName, destroyedActivityInfo.mKey, "no dump", "0");

        return true;
    }
}
