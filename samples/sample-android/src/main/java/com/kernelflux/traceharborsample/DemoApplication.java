package com.kernelflux.traceharborsample;

import android.app.Application;
import android.text.TextUtils;

import com.tencent.mrs.plugin.IDynamicConfig;

import com.kernelflux.traceharbor.TraceHarbor;
import com.kernelflux.traceharbor.batterycanary.BatteryEventDelegate;
import com.kernelflux.traceharbor.batterycanary.BatteryMonitorPlugin;
import com.kernelflux.traceharbor.batterycanary.monitor.BatteryMonitorConfig;
import com.kernelflux.traceharbor.iocanary.IOCanaryPlugin;
import com.kernelflux.traceharbor.iocanary.config.IOConfig;
import com.kernelflux.traceharbor.plugin.Plugin;
import com.kernelflux.traceharbor.plugin.PluginListener;
import com.kernelflux.traceharbor.report.Issue;
import com.kernelflux.traceharbor.resource.ResourcePlugin;
import com.kernelflux.traceharbor.resource.config.ResourceConfig;
import com.kernelflux.traceharbor.sqlitelint.SQLiteLint;
import com.kernelflux.traceharbor.sqlitelint.SQLiteLintPlugin;
import com.kernelflux.traceharbor.sqlitelint.config.SQLiteLintConfig;
import com.kernelflux.traceharbor.trace.TracePlugin;
import com.kernelflux.traceharbor.trace.config.TraceConfig;
import com.kernelflux.traceharbor.traffic.TrafficConfig;
import com.kernelflux.traceharbor.traffic.TrafficPlugin;

/**
 * Demo Application that boots TraceHarbor with all major plugins enabled, and routes every plugin
 * lifecycle event + Issue into {@link IssueRecorder} so the verification UI can show what was
 * detected without having to scrape Logcat.
 */
public class DemoApplication extends Application {

    private static DemoPlugin demoPlugin;
    private static TracePlugin tracePlugin;
    private static IOCanaryPlugin ioCanaryPlugin;
    private static ResourcePlugin resourcePlugin;
    private static BatteryMonitorPlugin batteryPlugin;
    private static SQLiteLintPlugin sqliteLintPlugin;
    private static TrafficPlugin trafficPlugin;

    private static long appOnCreateAtMs;

    @Override
    public void onCreate() {
        appOnCreateAtMs = System.currentTimeMillis();
        super.onCreate();
        IssueRecorder.appendEvent("app", "DemoApplication.onCreate()");

        DemoDynamicConfig dynamicConfig = new DemoDynamicConfig();
        demoPlugin = new DemoPlugin();
        ResourcePlugin.activityLeakFixer(this);

        tracePlugin = new TracePlugin(new TraceConfig.Builder()
                .dynamicConfig(dynamicConfig)
                .enableFPS(true)
                .enableEvilMethodTrace(true)
                .enableAnrTrace(true)
                .enableSignalAnrTrace(true) // article §5.1 — Signal ANR is a key Matrix capability
                .enableStartup(true)
                .enableIdleHandlerTrace(true)
                .enableTouchEventTrace(true)
                .splashActivities("com.kernelflux.traceharborsample.MainActivity;")
                .isDebug(true)
                .isDevEnv(true)
                .build());

        ioCanaryPlugin = new IOCanaryPlugin(new IOConfig.Builder()
                .dynamicConfig(dynamicConfig)
                .build());

        resourcePlugin = new ResourcePlugin(new ResourceConfig.Builder()
                .dynamicConfig(dynamicConfig)
                .setAutoDumpHprofMode(ResourceConfig.DumpMode.NO_DUMP)
                .setDetectDebuger(true)
                .build());

        if (!BatteryEventDelegate.isInit()) {
            BatteryEventDelegate.init(this);
        }
        batteryPlugin = new BatteryMonitorPlugin(new BatteryMonitorConfig.Builder()
                .enableForegroundMode(true)
                .enableBackgroundMode(false)
                .build());

        sqliteLintPlugin = new SQLiteLintPlugin(
                new SQLiteLintConfig(SQLiteLint.SqlExecutionCallbackMode.CUSTOM_NOTIFY));

        trafficPlugin = new TrafficPlugin(new TrafficConfig(true, true, true));

        TraceHarbor.init(new TraceHarbor.Builder(this)
                .pluginListener(new RecordingPluginListener())
                .plugin(demoPlugin)
                .plugin(tracePlugin)
                .plugin(ioCanaryPlugin)
                .plugin(resourcePlugin)
                .plugin(batteryPlugin)
                .plugin(sqliteLintPlugin)
                .plugin(trafficPlugin)
                .build());
        TraceHarbor.with().startAllPlugins();

        IssueRecorder.appendEvent("status",
                "TraceHarbor installed=" + TraceHarbor.isInstalled());
    }

    public static long getAppOnCreateAtMs() {
        return appOnCreateAtMs;
    }

    public static DemoPlugin getDemoPlugin() {
        return demoPlugin;
    }

    public static TracePlugin getTracePlugin() {
        return tracePlugin;
    }

    public static IOCanaryPlugin getIoCanaryPlugin() {
        return ioCanaryPlugin;
    }

    public static ResourcePlugin getResourcePlugin() {
        return resourcePlugin;
    }

    public static BatteryMonitorPlugin getBatteryPlugin() {
        return batteryPlugin;
    }

    public static SQLiteLintPlugin getSqliteLintPlugin() {
        return sqliteLintPlugin;
    }

    public static TrafficPlugin getTrafficPlugin() {
        return trafficPlugin;
    }

    public static void reportDemoIssue(String action, String detail) {
        if (demoPlugin == null) {
            IssueRecorder.appendEvent("error", "DemoPlugin is not initialized yet");
            return;
        }
        demoPlugin.reportIssue(action, detail);
    }

    /**
     * Routes plugin lifecycle and every Issue into {@link IssueRecorder}.
     */
    private static final class RecordingPluginListener implements PluginListener {
        @Override
        public void onInit(Plugin plugin) {
            IssueRecorder.appendEvent("plugin", "init -> " + plugin.getTag());
        }

        @Override
        public void onStart(Plugin plugin) {
            IssueRecorder.appendEvent("plugin", "start -> " + plugin.getTag());
        }

        @Override
        public void onStop(Plugin plugin) {
            IssueRecorder.appendEvent("plugin", "stop -> " + plugin.getTag());
        }

        @Override
        public void onDestroy(Plugin plugin) {
            IssueRecorder.appendEvent("plugin", "destroy -> " + plugin.getTag());
        }

        @Override
        public void onReportIssue(Issue issue) {
            IssueRecorder.onIssue(issue);
        }
    }

    /**
     * Mirrors the article's `DynamicConfigImplDemo` pattern (§3 init step). Only overrides values
     * the demo cares about; everything else passes through to the SDK default.
     */
    private static final class DemoDynamicConfig implements IDynamicConfig {
        @Override
        public String get(String key, String defStr) {
            if (TextUtils.equals(key, ExptEnum.clicfg_traceharbor_trace_care_scene_set.name())) {
                return "com.kernelflux.traceharborsample.MainActivity";
            }
            return defStr;
        }

        @Override
        public int get(String key, int defInt) {
            // Lower IO-canary thresholds so the demo screen reliably triggers issues.
            if (TextUtils.equals(key, ExptEnum.clicfg_traceharbor_io_main_thread_enable_threshold.name())) {
                return 1;   // ms
            }
            if (TextUtils.equals(key, ExptEnum.clicfg_traceharbor_io_small_buffer_threshold.name())) {
                return 512; // bytes
            }
            if (TextUtils.equals(key, ExptEnum.clicfg_traceharbor_io_small_buffer_operator_times.name())) {
                return 8;
            }
            if (TextUtils.equals(key, ExptEnum.clicfg_traceharbor_io_repeated_read_threshold.name())) {
                return 2;
            }
            // Tighten EvilMethod threshold so the demo's 800ms tap reliably triggers a slow-method issue.
            if (TextUtils.equals(key, ExptEnum.clicfg_traceharbor_trace_evil_method_threshold.name())) {
                return 200; // ms (default is 700)
            }
            // Resource plugin (Activity leak detection)
            if (TextUtils.equals(key, ExptEnum.clicfg_traceharbor_resource_detect_interval_millis.name())) {
                return 2000;
            }
            if (TextUtils.equals(key, ExptEnum.clicfg_traceharbor_resource_detect_interval_millis_bg.name())) {
                return 3000;
            }
            if (TextUtils.equals(key, ExptEnum.clicfg_traceharbor_resource_max_detect_times.name())) {
                return 2;
            }
            return defInt;
        }

        @Override
        public long get(String key, long defLong) {
            return defLong;
        }

        @Override
        public boolean get(String key, boolean defBool) {
            return true;
        }

        @Override
        public float get(String key, float defFloat) {
            return defFloat;
        }
    }
}
