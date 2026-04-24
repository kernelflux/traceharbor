package com.kernelflux.traceharborsample;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;

/**
 * §5.2 慢方法 (EvilMethod). Nested chain mirrors the upstream Matrix sample's A→B→C→… so the
 * Trace_EvilMethod.stack column has real call depth (decode IDs via methodMapping.txt).
 */
public class EvilMethodTestActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_evil);

        findViewById(R.id.evil_call_chain_button).setOnClickListener(v -> runCallChain());
        findViewById(R.id.evil_300_button).setOnClickListener(v -> sleepOnMain(300L));
        findViewById(R.id.evil_800_button).setOnClickListener(v -> sleepOnMain(800L));
        findViewById(R.id.evil_2000_button).setOnClickListener(v -> sleepOnMain(2_000L));
        findViewById(R.id.evil_compute_button).setOnClickListener(v -> heavyCompute());
    }

    private void runCallChain() {
        IssueRecorder.appendEvent("evil", "EvilMethod: nested A→B→C→… call chain");
        long start = SystemClock.elapsedRealtime();
        A();
        long actual = SystemClock.elapsedRealtime() - start;
        DemoApplication.reportDemoIssue("evil_call_chain", "Nested chain finished in " + actual + " ms");
    }

    private void A() { B(); H(); SystemClock.sleep(120); }
    private void B() { C(); G(); SystemClock.sleep(80);  }
    private void C() { D(); E(); F(); SystemClock.sleep(40); }
    private void D() { SystemClock.sleep(20); }
    private void E() { SystemClock.sleep(20); }
    private void F() { SystemClock.sleep(20); }
    private void G() { SystemClock.sleep(20); }
    private void H() { I(); J(); K(); SystemClock.sleep(20); }
    private void I() { SystemClock.sleep(20); }
    private void J() { SystemClock.sleep(20); }
    private void K() { SystemClock.sleep(20); }

    private void sleepOnMain(long ms) {
        IssueRecorder.appendEvent("evil", "EvilMethod: SystemClock.sleep(" + ms + ") on main");
        long start = SystemClock.elapsedRealtime();
        SystemClock.sleep(ms);
        long actual = SystemClock.elapsedRealtime() - start;
        DemoApplication.reportDemoIssue("evil_sleep", "Sleep " + ms + " ms (actual " + actual + ")");
    }

    private void heavyCompute() {
        IssueRecorder.appendEvent("evil", "EvilMethod: heavy CPU compute on main thread");
        long start = SystemClock.elapsedRealtime();
        double sink = 0.0;
        for (int i = 0; i < 4_000_000; i++) {
            sink += Math.sin(i) * Math.cos(i) + Math.sqrt(i + 1.0);
        }
        long actual = SystemClock.elapsedRealtime() - start;
        DemoApplication.reportDemoIssue("evil_compute",
                "Heavy compute finished in " + actual + " ms (sink=" + ((long) sink) + ")");
    }
}
