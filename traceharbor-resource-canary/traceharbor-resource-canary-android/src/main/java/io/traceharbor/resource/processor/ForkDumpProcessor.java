package io.traceharbor.resource.processor;

import android.os.Build;

import io.traceharbor.resource.MemoryUtil;
import io.traceharbor.resource.analyzer.model.DestroyedActivityInfo;
import io.traceharbor.resource.analyzer.model.HeapDump;
import io.traceharbor.resource.config.ResourceConfig;
import io.traceharbor.resource.config.SharePluginInfo;
import io.traceharbor.resource.dumper.HprofFileManager;
import io.traceharbor.resource.watcher.ActivityRefWatcher;
import io.traceharbor.util.TraceHarborLog;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * HPROF file dump processor using fork dump.
 *
 * @author aurorani
 * @since 2021/10/25
 */
public class ForkDumpProcessor extends BaseLeakProcessor {

    private static final String TAG = "TraceHarbor.LeakProcessor.ForkDump";

    public ForkDumpProcessor(ActivityRefWatcher watcher) {
        super(watcher);
    }

    @Override
    public boolean process(DestroyedActivityInfo destroyedActivityInfo) {
        publishIssue(SharePluginInfo.IssueType.LEAK_FOUND, ResourceConfig.DumpMode.NO_DUMP, destroyedActivityInfo.mActivityName, destroyedActivityInfo.mKey, "no dump", "0");

        if (Build.VERSION.SDK_INT > ResourceConfig.FORK_DUMP_SUPPORTED_API_GUARD) {
            TraceHarborLog.e(TAG, "unsupported API version " + Build.VERSION.SDK_INT);
            return false;
        }

        final long dumpStart = System.currentTimeMillis();

        File hprof = null;
        try {
            hprof = HprofFileManager.INSTANCE.prepareHprofFile("FDP", true);
        } catch (FileNotFoundException e) {
            TraceHarborLog.printErrStackTrace(TAG, e, "");
        }

        if (hprof == null) {
            TraceHarborLog.e(TAG, "cannot create hprof file, just ignore");
            return true;
        }

        if (!MemoryUtil.dump(hprof.getPath(), 600)) {
            TraceHarborLog.e(TAG, String.format("heap dump for further analyzing activity with key [%s] was failed, just ignore.",
                    destroyedActivityInfo.mKey));
            return true;
        }

        TraceHarborLog.i(TAG, String.format("dump cost=%sms refString=%s path=%s",
                System.currentTimeMillis() - dumpStart, destroyedActivityInfo.mKey, hprof.getPath()));

        getWatcher().markPublished(destroyedActivityInfo.mActivityName);
        getWatcher().triggerGc();

        getHeapDumpHandler().process(
                new HeapDump(hprof, destroyedActivityInfo.mKey, destroyedActivityInfo.mActivityName));

        return true;
    }
}
