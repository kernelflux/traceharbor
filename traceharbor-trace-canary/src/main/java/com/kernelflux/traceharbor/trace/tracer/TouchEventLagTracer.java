package com.kernelflux.traceharbor.trace.tracer;

import static com.kernelflux.traceharbor.trace.constants.Constants.DEFAULT_TOUCH_EVENT_LAG;

import androidx.annotation.Keep;

import com.kernelflux.traceharbor.AppActiveTraceHarborDelegate;
import com.kernelflux.traceharbor.TraceHarbor;
import com.kernelflux.traceharbor.report.Issue;
import com.kernelflux.traceharbor.trace.TracePlugin;
import com.kernelflux.traceharbor.trace.config.SharePluginInfo;
import com.kernelflux.traceharbor.trace.config.TraceConfig;
import com.kernelflux.traceharbor.trace.constants.Constants;
import com.kernelflux.traceharbor.trace.util.AppForegroundUtil;
import com.kernelflux.traceharbor.trace.util.Utils;
import com.kernelflux.traceharbor.util.DeviceUtil;
import com.kernelflux.traceharbor.util.TraceHarborHandlerThread;
import com.kernelflux.traceharbor.util.TraceHarborLog;

import org.json.JSONObject;

public class TouchEventLagTracer extends Tracer {
    private static final String TAG = "TraceHarbor.TouchEventLagTracer";
    private static TraceConfig traceConfig;
    private static long lastLagTime = 0;
    private static String currentLagFdStackTrace;

    static {
        System.loadLibrary("trace-canary");
    }

    public TouchEventLagTracer(TraceConfig config) {
        traceConfig = config;
    }

    @Override
    public synchronized void onAlive() {
        super.onAlive();
        if (traceConfig.isTouchEventTraceEnable()) {
            nativeInitTouchEventLagDetective(traceConfig.touchEventLagThreshold);
        }
    }

    @Override
    public void onDead() {
        super.onDead();

    }

    public static native void nativeInitTouchEventLagDetective(int lagThreshold);

    @Keep
    private static void onTouchEventLagDumpTrace(int fd) {
        TraceHarborLog.e(TAG, "onTouchEventLagDumpTrace, fd = " + fd);
        currentLagFdStackTrace = Utils.getMainThreadJavaStackTrace();
    }
    @Keep
    private static void onTouchEventLag(final int fd) {
        TraceHarborLog.e(TAG, "onTouchEventLag, fd = " + fd);
        TraceHarborHandlerThread.getDefaultHandler().post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (System.currentTimeMillis() - lastLagTime < DEFAULT_TOUCH_EVENT_LAG * 2) {
                        return;
                    }
                    TraceHarborLog.i(TAG, "onTouchEventLag report");

                    lastLagTime = System.currentTimeMillis();

                    TracePlugin plugin = TraceHarbor.with().getPluginByClass(TracePlugin.class);
                    if (null == plugin) {
                        return;
                    }

                    String stackTrace = currentLagFdStackTrace;
                    boolean currentForeground = AppForegroundUtil.isInterestingToUser();
                    String scene = AppActiveTraceHarborDelegate.INSTANCE.getVisibleScene();

                    JSONObject jsonObject = new JSONObject();
                    jsonObject = DeviceUtil.getDeviceInfo(jsonObject, TraceHarbor.with().getApplication());
                    jsonObject.put(SharePluginInfo.ISSUE_STACK_TYPE, Constants.Type.LAG_TOUCH);
                    jsonObject.put(SharePluginInfo.ISSUE_SCENE, scene);
                    jsonObject.put(SharePluginInfo.ISSUE_THREAD_STACK, stackTrace);
                    jsonObject.put(SharePluginInfo.ISSUE_PROCESS_FOREGROUND, currentForeground);

                    Issue issue = new Issue();
                    issue.setTag(SharePluginInfo.TAG_PLUGIN_EVIL_METHOD);
                    issue.setContent(jsonObject);
                    plugin.onDetectIssue(issue);

                } catch (Throwable t) {
                    TraceHarborLog.e(TAG, "TraceHarbor error, error = " + t.getMessage());
                }
            }
        });
    }
}
