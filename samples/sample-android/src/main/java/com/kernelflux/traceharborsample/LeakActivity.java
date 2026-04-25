package com.kernelflux.traceharborsample;

import android.app.Activity;
import android.os.Bundle;

/**
 * §5 (resource section) — Activity leak verification for ResourcePlugin.
 */
public class LeakActivity extends Activity {
    private boolean shouldLeak = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leak);

        findViewById(R.id.finish_with_leak_button).setOnClickListener(v -> {
            IssueRecorder.appendEvent("action", "LeakActivity will finish AND keep a strong reference");
            finish();
        });

        findViewById(R.id.finish_without_leak_button).setOnClickListener(v -> {
            IssueRecorder.appendEvent("action", "LeakActivity will finish without keeping a reference");
            shouldLeak = false;
            LeakStore.clear();
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!isFinishing()) {
            return;
        }
        if (shouldLeak) {
            LeakStore.hold(this);
            IssueRecorder.appendEvent("leak", "LeakActivity destroyed but leaked through LeakStore");
        }
    }
}
