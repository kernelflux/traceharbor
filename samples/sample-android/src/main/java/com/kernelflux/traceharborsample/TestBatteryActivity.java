package com.kernelflux.traceharborsample;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import com.kernelflux.traceharbor.batterycanary.BatteryMonitorPlugin;

/**
 * Smoke screen for BatteryMonitorPlugin. Confirms the plugin is started and exposes simple
 * start/stop toggles; full battery metrics chart is intentionally not ported here.
 */
public class TestBatteryActivity extends Activity {

    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battery);

        statusView = findViewById(R.id.battery_status);
        // DemoApplication has already startAllPlugins(); guard against re-entry — Plugin.start()/stop()
        // throws if called when the plugin is already in that state.
        findViewById(R.id.battery_start).setOnClickListener(v -> {
            BatteryMonitorPlugin p = DemoApplication.getBatteryPlugin();
            if (p == null) {
                IssueRecorder.appendEvent("error", "BatteryMonitorPlugin not initialised");
            } else if (p.isPluginStarted()) {
                IssueRecorder.appendEvent("battery", "BatteryMonitorPlugin already started; no-op");
            } else {
                p.start();
                IssueRecorder.appendEvent("battery", "BatteryMonitorPlugin.start()");
            }
            renderStatus();
        });
        findViewById(R.id.battery_stop).setOnClickListener(v -> {
            BatteryMonitorPlugin p = DemoApplication.getBatteryPlugin();
            if (p == null) {
                IssueRecorder.appendEvent("error", "BatteryMonitorPlugin not initialised");
            } else if (!p.isPluginStarted()) {
                IssueRecorder.appendEvent("battery", "BatteryMonitorPlugin already stopped; no-op");
            } else {
                p.stop();
                IssueRecorder.appendEvent("battery", "BatteryMonitorPlugin.stop()");
            }
            renderStatus();
        });

        renderStatus();
    }

    private void renderStatus() {
        BatteryMonitorPlugin p = DemoApplication.getBatteryPlugin();
        boolean started = p != null && p.isPluginStarted();
        statusView.setText("BatteryMonitorPlugin = " + (started ? "started" : "stopped"));
    }
}
