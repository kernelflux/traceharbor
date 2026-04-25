package com.kernelflux.traceharborsample;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * §5.1 ANR — sleep on the main thread to trigger LooperAnrTracer / SignalAnrTracer.
 */
public class AnrTestActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_anr);

        findViewById(R.id.anr_5s_button).setOnClickListener(v -> blockMainThread(5_000L));
        findViewById(R.id.anr_8s_button).setOnClickListener(v -> blockMainThread(8_000L));
        findViewById(R.id.anr_20s_button).setOnClickListener(v -> blockMainThread(20_000L));
        findViewById(R.id.anr_loop_3s_button).setOnClickListener(v -> postLoopBlock());
    }

    private void blockMainThread(long durationMs) {
        IssueRecorder.appendEvent("anr", "Main thread will sleep for " + durationMs + " ms");
        long start = SystemClock.elapsedRealtime();
        SystemClock.sleep(durationMs);
        long actual = SystemClock.elapsedRealtime() - start;
        IssueRecorder.appendEvent("anr", "Main thread resumed after " + actual + " ms");
        DemoApplication.reportDemoIssue("anr_block", "Blocked main thread for " + actual + " ms");
    }

    private void postLoopBlock() {
        IssueRecorder.appendEvent("anr", "Posting 3 main-thread tasks of 3s each (Looper backlog)");
        Handler handler = new Handler(Looper.getMainLooper());
        for (int i = 1; i <= 3; i++) {
            final int idx = i;
            handler.post(() -> {
                IssueRecorder.appendEvent("anr", "Looper task " + idx + "/3 starts (will block 3s)");
                SystemClock.sleep(3_000L);
                IssueRecorder.appendEvent("anr", "Looper task " + idx + "/3 done");
            });
        }
    }
}
