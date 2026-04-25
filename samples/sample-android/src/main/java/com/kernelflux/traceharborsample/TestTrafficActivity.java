package com.kernelflux.traceharborsample;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import com.kernelflux.traceharbor.traffic.TrafficPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Smoke screen for TrafficPlugin. After the plugin's native init in DemoApplication, this screen
 * just dumps the current rx/tx counters returned by {@code TrafficPlugin#getTrafficInfoMap}.
 */
public class TestTrafficActivity extends Activity {

    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_traffic);

        statusView = findViewById(R.id.traffic_status);
        findViewById(R.id.traffic_dump).setOnClickListener(v -> dump());
        renderInitial();
    }

    private void renderInitial() {
        TrafficPlugin p = DemoApplication.getTrafficPlugin();
        statusView.setText("TrafficPlugin = " + (p != null && p.isPluginStarted() ? "started" : "stopped")
                + "\n\nTap DUMP to read current rx/tx counters.");
    }

    private void dump() {
        TrafficPlugin p = DemoApplication.getTrafficPlugin();
        if (p == null) {
            statusView.setText("TrafficPlugin not registered");
            return;
        }
        try {
            Map<String, String> rx = p.getTrafficInfoMap(TrafficPlugin.TYPE_GET_TRAFFIC_RX);
            Map<String, String> tx = p.getTrafficInfoMap(TrafficPlugin.TYPE_GET_TRAFFIC_TX);
            StringBuilder sb = new StringBuilder();
            sb.append("RX entries: ").append(rx == null ? 0 : rx.size()).append('\n');
            sb.append("TX entries: ").append(tx == null ? 0 : tx.size()).append('\n');
            for (Map.Entry<String, String> e : safe(rx).entrySet()) {
                sb.append("RX ").append(e.getKey()).append(" -> ").append(e.getValue()).append('\n');
            }
            for (Map.Entry<String, String> e : safe(tx).entrySet()) {
                sb.append("TX ").append(e.getKey()).append(" -> ").append(e.getValue()).append('\n');
            }
            statusView.setText(sb);
            IssueRecorder.appendEvent("traffic",
                    "Dumped traffic counters (rx=" + (rx == null ? 0 : rx.size())
                            + " tx=" + (tx == null ? 0 : tx.size()) + ")");
        } catch (Throwable t) {
            IssueRecorder.appendEvent("error", "Traffic dump failed: " + t);
            statusView.setText("FAILED: " + t);
        }
    }

    private static Map<String, String> safe(Map<String, String> m) {
        return m == null ? new HashMap<>() : m;
    }
}
