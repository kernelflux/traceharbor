package com.kernelflux.traceharborsample;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.kernelflux.traceharbor.trace.TracePlugin;
import com.kernelflux.traceharbor.trace.core.AppMethodBeat;
import com.kernelflux.traceharbor.trace.tracer.SignalAnrTracer;

/**
 * Trace plugin sub-hub. Mirrors the layout of Matrix's {@code TestTraceMainActivity}.
 *
 * <ul>
 *   <li>Toggle AppMethodBeat — turns the bytecode method beat on/off; verifies the trace
 *       insertion path is alive.</li>
 *   <li>Print main-thread trace — calls {@link SignalAnrTracer#printTrace()}.</li>
 * </ul>
 */
public class TraceTestsActivity extends Activity {

    private boolean appMethodBeatOff = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trace_tests);

        findViewById(R.id.trace_nav_anr).setOnClickListener(v ->
                startActivity(new Intent(this, AnrTestActivity.class)));
        findViewById(R.id.trace_nav_evil).setOnClickListener(v ->
                startActivity(new Intent(this, EvilMethodTestActivity.class)));
        findViewById(R.id.trace_nav_fps).setOnClickListener(v ->
                startActivity(new Intent(this, FpsTestActivity.class)));

        findViewById(R.id.trace_toggle_beat).setOnClickListener(v -> toggleAppMethodBeat());
        findViewById(R.id.trace_print_trace).setOnClickListener(v -> printTrace());
    }

    private void toggleAppMethodBeat() {
        TracePlugin tracePlugin = DemoApplication.getTracePlugin();
        if (tracePlugin == null) {
            IssueRecorder.appendEvent("error", "TracePlugin is not initialised yet");
            return;
        }
        AppMethodBeat beat = tracePlugin.getAppMethodBeat();
        if (beat == null) {
            IssueRecorder.appendEvent("error", "AppMethodBeat instance is null");
            return;
        }
        if (appMethodBeatOff) {
            beat.onStart();
            appMethodBeatOff = false;
            IssueRecorder.appendEvent("trace", "AppMethodBeat resumed");
            Toast.makeText(this, "AppMethodBeat: ON", Toast.LENGTH_SHORT).show();
        } else {
            beat.onStop();
            appMethodBeatOff = true;
            IssueRecorder.appendEvent("trace", "AppMethodBeat stopped");
            Toast.makeText(this, "AppMethodBeat: OFF", Toast.LENGTH_SHORT).show();
        }
    }

    private void printTrace() {
        try {
            SignalAnrTracer.printTrace();
            IssueRecorder.appendEvent("trace", "SignalAnrTracer.printTrace() invoked");
            Toast.makeText(this, "printTrace() invoked", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            IssueRecorder.appendEvent("error", "printTrace failed: " + t);
        }
    }
}
