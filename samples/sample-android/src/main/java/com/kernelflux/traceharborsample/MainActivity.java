package com.kernelflux.traceharborsample;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.kernelflux.traceharbor.trace.view.FrameDecorator;

/**
 * Hub screen mirroring the upstream Matrix sample's {@code MainActivity}: 10 plugin / utility
 * entries in the same order, same labels, same XML onClick handlers
 * ({@link #testSupervisor}, {@link #testFgService}, {@link #testOverlayWindow}).
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.test_trace).setOnClickListener(v ->
                startActivity(new Intent(this, TraceTestsActivity.class)));
        findViewById(R.id.test_leak).setOnClickListener(v ->
                startActivity(new Intent(this, LeakActivity.class)));
        findViewById(R.id.test_hooks).setOnClickListener(v ->
                startActivity(new Intent(this, TestHooksActivity.class)));
        findViewById(R.id.test_battery).setOnClickListener(v ->
                startActivity(new Intent(this, TestBatteryActivity.class)));
        findViewById(R.id.test_io).setOnClickListener(v ->
                startActivity(new Intent(this, IoTestActivity.class)));
        findViewById(R.id.test_sqlite_lint).setOnClickListener(v ->
                startActivity(new Intent(this, TestSqliteLintActivity.class)));
        findViewById(R.id.test_traffic_enter).setOnClickListener(v ->
                startActivity(new Intent(this, TestTrafficActivity.class)));
        // test_supervisor / test_fg_service / test_overlay_window are wired via android:onClick.
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_overflow, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_inspect_issue) {
            IssueDetailDialog.open(this);
            return true;
        }
        if (id == R.id.menu_clear_issues) {
            IssueRecorder.clearAll();
            Toast.makeText(this, R.string.menu_clear_issues_toast, Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** android:onClick handler for the "Start Supervisor" button. */
    public void testSupervisor(View view) {
        startService(new Intent(this, SupervisorStubService.class));
        Toast.makeText(this, R.string.toast_supervisor_started, Toast.LENGTH_SHORT).show();
    }

    /**
     * android:onClick handler for "test FgService". Foreground service infrastructure (channel,
     * notification, ForegroundServiceType permissions) is intentionally NOT ported into this
     * sample — the button exists for layout parity with upstream and prints what would happen.
     */
    public void testFgService(View view) {
        Toast.makeText(this, R.string.toast_fg_service_omitted, Toast.LENGTH_LONG).show();
    }

    /**
     * android:onClick handler for "test Overlay Window". Toggles the FrameTracer's on-screen
     * frame-rate overlay; requires SYSTEM_ALERT_WINDOW which the user must grant on Android M+.
     */
    public void testOverlayWindow(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            Toast.makeText(this, R.string.toast_overlay_permission_required, Toast.LENGTH_LONG).show();
            return;
        }
        FrameDecorator decorator = FrameDecorator.getInstance(this);
        if (decorator.isShowing()) {
            decorator.dismiss();
        } else {
            decorator.setEnable(true);
            decorator.show();
        }
    }
}
