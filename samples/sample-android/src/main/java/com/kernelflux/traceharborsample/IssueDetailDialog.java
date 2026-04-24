package com.kernelflux.traceharborsample;

import android.app.AlertDialog;
import android.content.Context;
import android.text.method.ScrollingMovementMethod;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Shows a single Issue snapshot's raw JSON in a scrollable, selectable dialog. The recent-issues
 * picker first lists the most recent N entries; tapping one reveals its content (the structure
 * documented in article §5.2.4 / §5.3 — {@code stack}, {@code dropLevel}, {@code threadStack}, …).
 */
public final class IssueDetailDialog {

    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private IssueDetailDialog() {}

    /** Opens a chooser of the latest issues, then shows the chosen one. */
    public static void open(Context ctx) {
        List<IssueRecorder.IssueSnapshot> snaps = IssueRecorder.snapshotPayloads(15);
        if (snaps.isEmpty()) {
            new AlertDialog.Builder(ctx)
                    .setTitle("No issues yet")
                    .setMessage("Run a test (Trace / IO / Resource Leak) first; this dialog will then list raw issue payloads.")
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        String[] labels = new String[snaps.size()];
        for (int i = 0; i < snaps.size(); i++) {
            IssueRecorder.IssueSnapshot s = snaps.get(i);
            labels[i] = TIME_FORMAT.format(new Date(s.capturedAtMs))
                    + "  [" + (s.tag == null ? "?" : s.tag) + "]"
                    + (s.key == null ? "" : " key=" + s.key);
        }
        new AlertDialog.Builder(ctx)
                .setTitle("Pick an issue to inspect")
                .setItems(labels, (d, which) -> showRawJson(ctx, snaps.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void showRawJson(Context ctx, IssueRecorder.IssueSnapshot snap) {
        TextView text = new TextView(ctx);
        text.setText(snap.prettyJson);
        text.setTextIsSelectable(true);
        text.setTypeface(android.graphics.Typeface.MONOSPACE);
        text.setPadding(48, 24, 48, 24);
        text.setMovementMethod(new ScrollingMovementMethod());
        ScrollView scroll = new ScrollView(ctx);
        scroll.addView(text);
        new AlertDialog.Builder(ctx)
                .setTitle("[" + snap.tag + "] type=" + snap.type
                        + (snap.key == null ? "" : " key=" + snap.key))
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
