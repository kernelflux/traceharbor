package com.kernelflux.traceharborsample;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import com.kernelflux.traceharbor.hook.HookManager;
import com.kernelflux.traceharbor.hook.memory.MemoryHook;
import com.kernelflux.traceharbor.hook.pthread.PthreadHook;

/**
 * Smoke screen for the TraceHarbor Hooks module. Tap the buttons to commit MemoryHook /
 * PthreadHook with the simplest config; verify in Logcat that the hookcommon native library
 * loads and the hook installs without crashing.
 */
public class TestHooksActivity extends Activity {

    private TextView statusView;
    private boolean memoryHookCommitted = false;
    private boolean pthreadHookCommitted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hooks);

        statusView = findViewById(R.id.hooks_status);
        findViewById(R.id.hooks_install_memory).setOnClickListener(v -> commitMemoryHook());
        findViewById(R.id.hooks_install_pthread).setOnClickListener(v -> commitPthreadHook());

        renderStatus();
    }

    private void commitMemoryHook() {
        if (memoryHookCommitted) {
            Toast.makeText(this, "MemoryHook already committed", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            HookManager.INSTANCE.addHook(MemoryHook.INSTANCE
                    .addHookSo(".*libtraceharbor-sample\\.so$")
                    .enableStacktrace(true));
            HookManager.INSTANCE.commitHooks();
            memoryHookCommitted = true;
            IssueRecorder.appendEvent("hooks", "MemoryHook committed");
        } catch (Throwable t) {
            IssueRecorder.appendEvent("error", "MemoryHook commit failed: " + t);
        }
        renderStatus();
    }

    private void commitPthreadHook() {
        if (pthreadHookCommitted) {
            Toast.makeText(this, "PthreadHook already committed", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            HookManager.INSTANCE.addHook(PthreadHook.INSTANCE
                    .setThreadStackShrinkConfig(new PthreadHook.ThreadStackShrinkConfig()
                            .setEnabled(true)));
            HookManager.INSTANCE.commitHooks();
            pthreadHookCommitted = true;
            IssueRecorder.appendEvent("hooks", "PthreadHook committed");
        } catch (Throwable t) {
            IssueRecorder.appendEvent("error", "PthreadHook commit failed: " + t);
        }
        renderStatus();
    }

    private void renderStatus() {
        statusView.setText("MemoryHook  = " + (memoryHookCommitted ? "committed" : "pending")
                + "\nPthreadHook = " + (pthreadHookCommitted ? "committed" : "pending"));
    }
}
