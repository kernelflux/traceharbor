package com.kernelflux.traceharbor.resource.processor;

import com.kernelflux.traceharbor.resource.analyzer.model.DestroyedActivityInfo;
import com.kernelflux.traceharbor.resource.config.ResourceConfig;
import com.kernelflux.traceharbor.resource.config.SharePluginInfo;
import com.kernelflux.traceharbor.resource.watcher.ActivityRefWatcher;
import com.kernelflux.traceharbor.util.TraceHarborLog;

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
