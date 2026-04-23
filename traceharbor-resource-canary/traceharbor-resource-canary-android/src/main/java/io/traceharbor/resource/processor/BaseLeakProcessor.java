package io.traceharbor.resource.processor;

import android.os.Build;

import io.traceharbor.TraceHarbor;
import io.traceharbor.report.Issue;
import io.traceharbor.resource.CanaryWorkerService;
import io.traceharbor.resource.ResourcePlugin;
import io.traceharbor.resource.analyzer.ActivityLeakAnalyzer;
import io.traceharbor.resource.analyzer.model.ActivityLeakResult;
import io.traceharbor.resource.analyzer.model.AndroidExcludedRefs;
import io.traceharbor.resource.analyzer.model.DestroyedActivityInfo;
import io.traceharbor.resource.analyzer.model.ExcludedRefs;
import io.traceharbor.resource.analyzer.model.HeapDump;
import io.traceharbor.resource.analyzer.model.HeapSnapshot;
import io.traceharbor.resource.config.ResourceConfig;
import io.traceharbor.resource.config.SharePluginInfo;
import io.traceharbor.resource.dumper.AndroidHeapDumper;
import io.traceharbor.resource.watcher.ActivityRefWatcher;
import io.traceharbor.util.TraceHarborLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

/**
 * Created by Yves on 2021/2/25
 */
public abstract class BaseLeakProcessor {
    private static final String TAG = "TraceHarbor.LeakProcessor.Base";

    private final ActivityRefWatcher mWatcher;

    private AndroidHeapDumper                 mHeapDumper;
    private AndroidHeapDumper.HeapDumpHandler mHeapDumpHandler;

    public BaseLeakProcessor(ActivityRefWatcher watcher) {
        mWatcher = watcher;
    }

    public abstract boolean process(DestroyedActivityInfo destroyedActivityInfo);

    @Deprecated
    public AndroidHeapDumper getHeapDumper() {
        if (mHeapDumper == null) {
            mHeapDumper = new AndroidHeapDumper(mWatcher.getContext());
        }
        return mHeapDumper;
    }

    protected AndroidHeapDumper.HeapDumpHandler getHeapDumpHandler() {
        if (mHeapDumpHandler == null) {
            mHeapDumpHandler = new AndroidHeapDumper.HeapDumpHandler() {
                @Override
                public void process(HeapDump result) {
                    CanaryWorkerService.shrinkHprofAndReport(mWatcher.getContext(), result);
                }
            };
        }

        return mHeapDumpHandler;
    }

    public ActivityRefWatcher getWatcher() {
        return mWatcher;
    }

    public void onDestroy() {
    }

    private static volatile boolean mAnalyzing = false;

    public static boolean isAnalyzing() {
        return mAnalyzing;
    }

    private static void setAnalyzing(boolean analyzing) {
        mAnalyzing = analyzing;
    }

    protected ActivityLeakResult analyze(File hprofFile, String referenceKey) {
        setAnalyzing(true);
        final HeapSnapshot heapSnapshot;
        ActivityLeakResult result;
        String manufacture = TraceHarbor.with().getPluginByClass(ResourcePlugin.class).getConfig().getManufacture();
        final ExcludedRefs excludedRefs = AndroidExcludedRefs.createAppDefaults(Build.VERSION.SDK_INT, manufacture).build();
        try {
            heapSnapshot = new HeapSnapshot(hprofFile);
            result = new ActivityLeakAnalyzer(referenceKey, excludedRefs).analyze(heapSnapshot);
        } catch (IOException e) {
            result = ActivityLeakResult.failure(e, 0);
        }
        getWatcher().triggerGc();
        setAnalyzing(false);
        return result;
    }

    final protected void publishIssue(int issueType, ResourceConfig.DumpMode dumpMode, String activity, String refKey, String detail, String cost) {
        publishIssue(issueType, dumpMode, activity, refKey, detail, cost, 0);
    }

    final protected void publishIssue(int issueType, ResourceConfig.DumpMode dumpMode, String activity, String refKey, String detail, String cost, int retryCount) {
        publishIssue(issueType, dumpMode, activity, refKey, detail, cost, retryCount, null);
    }

    final protected void publishIssue(int issueType, ResourceConfig.DumpMode dumpMode, String activity, String refKey, String detail, String cost, int retryCount, String hprofPath) {
        Issue issue = new Issue(issueType);
        JSONObject content = new JSONObject();
        try {
            content.put(SharePluginInfo.ISSUE_DUMP_MODE, dumpMode.name());
            content.put(SharePluginInfo.ISSUE_ACTIVITY_NAME, activity);
            content.put(SharePluginInfo.ISSUE_REF_KEY, refKey);
            content.put(SharePluginInfo.ISSUE_LEAK_DETAIL, detail);
            content.put(SharePluginInfo.ISSUE_COST_MILLIS, cost);
            content.put(SharePluginInfo.ISSUE_RETRY_COUNT, retryCount);
            content.put(SharePluginInfo.ISSUE_HPROF_PATH, hprofPath);
        } catch (JSONException jsonException) {
            TraceHarborLog.printErrStackTrace(TAG, jsonException, "");
        }
        issue.setContent(content);
        getWatcher().getResourcePlugin().onDetectIssue(issue);
    }
}
