package com.kernelflux.traceharborsample;

import android.text.TextUtils;
import android.util.Log;

import com.kernelflux.traceharbor.report.Issue;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Central in-memory store for plugin lifecycle events and {@link Issue} reports raised by all
 * TraceHarbor plugins. Used by the demo screens to provide on-device verification of monitoring
 * results without needing to scrape Logcat.
 */
public final class IssueRecorder {
    public static final String TAG = "TraceHarborSample";

    /** Tags that we track explicitly so the verification screen can show per-category hit counts. */
    public static final String[] TRACKED_TAGS = {
            "Trace_FPS",
            "Trace_EvilMethod",
            "Trace_LooperAnr",
            "Trace_SignalAnr",
            "Trace_StartUp",
            "Trace_IdleHandler",
            "Trace_TouchEventLag",
            "io",
            "ResourceCanary",
            "DemoPlugin"
    };

    private static final int MAX_EVENT_LINES = 200;
    private static final int MAX_ISSUE_LINES = 80;

    private static final Object LOCK = new Object();
    private static final ArrayDeque<String> EVENTS = new ArrayDeque<>();
    private static final ArrayDeque<String> ISSUES = new ArrayDeque<>();
    /** Captured Issue payloads in newest-last order, parallel to ISSUES. */
    private static final ArrayDeque<IssueSnapshot> ISSUE_PAYLOADS = new ArrayDeque<>();
    private static final Map<String, Integer> COUNTERS = new LinkedHashMap<>();
    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    /** Light copy of the parts of {@link Issue} we want to render later (Issue itself isn't snapshot-friendly). */
    public static final class IssueSnapshot {
        public final String tag;
        public final int type;
        public final String key;
        public final String prettyJson;
        public final long capturedAtMs;

        IssueSnapshot(String tag, int type, String key, String prettyJson, long capturedAtMs) {
            this.tag = tag;
            this.type = type;
            this.key = key;
            this.prettyJson = prettyJson;
            this.capturedAtMs = capturedAtMs;
        }
    }

    private IssueRecorder() {}

    /** Append a free-form event (user actions, plugin lifecycle, etc.). */
    public static void appendEvent(String type, String message) {
        String line = TIME_FORMAT.format(new Date()) + " [" + type + "] " + message;
        synchronized (LOCK) {
            EVENTS.addLast(line);
            while (EVENTS.size() > MAX_EVENT_LINES) {
                EVENTS.removeFirst();
            }
        }
        Log.i(TAG, line);
    }

    /** Record an Issue raised by any TraceHarbor plugin. */
    public static void onIssue(Issue issue) {
        String tag = issue == null ? "null" : (issue.getTag() == null ? "?" : issue.getTag());
        synchronized (LOCK) {
            Integer prev = COUNTERS.get(tag);
            COUNTERS.put(tag, (prev == null ? 0 : prev) + 1);
            String summary = formatIssueSummary(issue);
            ISSUES.addLast(TIME_FORMAT.format(new Date()) + " " + summary);
            while (ISSUES.size() > MAX_ISSUE_LINES) {
                ISSUES.removeFirst();
            }
            ISSUE_PAYLOADS.addLast(snapshotIssue(issue));
            while (ISSUE_PAYLOADS.size() > MAX_ISSUE_LINES) {
                ISSUE_PAYLOADS.removeFirst();
            }
        }
        appendEvent("issue", "[" + tag + "] " + extractIssueDigest(issue));
    }

    /** Clears events, issues, and per-tag counters. Used by the "Clear" button. */
    public static void clearAll() {
        synchronized (LOCK) {
            EVENTS.clear();
            ISSUES.clear();
            ISSUE_PAYLOADS.clear();
            COUNTERS.clear();
        }
        appendEvent("status", "IssueRecorder cleared");
    }

    /** Newest-first snapshot of recent Issue payloads, capped at the same MAX as {@link #renderIssues(int)}. */
    public static java.util.List<IssueSnapshot> snapshotPayloads(int limit) {
        java.util.ArrayList<IssueSnapshot> out = new java.util.ArrayList<>();
        synchronized (LOCK) {
            int taken = 0;
            for (java.util.Iterator<IssueSnapshot> it = ISSUE_PAYLOADS.descendingIterator();
                    it.hasNext() && taken < limit; taken++) {
                out.add(it.next());
            }
        }
        return out;
    }

    private static IssueSnapshot snapshotIssue(Issue issue) {
        if (issue == null) {
            return new IssueSnapshot("null", 0, null, "(null issue)", System.currentTimeMillis());
        }
        String pretty;
        try {
            pretty = issue.getContent() == null ? "(no content)" : issue.getContent().toString(2);
        } catch (Exception e) {
            pretty = issue.getContent() == null ? "(no content)" : issue.getContent().toString();
        }
        return new IssueSnapshot(issue.getTag(), issue.getType(), issue.getKey(),
                pretty, System.currentTimeMillis());
    }

    /** Snapshot of per-tag issue counts (in insertion order, then known tags pinned to top). */
    public static Map<String, Integer> snapshotCounters() {
        Map<String, Integer> out = new LinkedHashMap<>();
        synchronized (LOCK) {
            for (String tag : TRACKED_TAGS) {
                out.put(tag, COUNTERS.containsKey(tag) ? COUNTERS.get(tag) : 0);
            }
            for (Map.Entry<String, Integer> e : COUNTERS.entrySet()) {
                if (!out.containsKey(e.getKey())) {
                    out.put(e.getKey(), e.getValue());
                }
            }
        }
        return out;
    }

    /** Newest-first list of recent events (capped). */
    public static String renderEvents(int limit) {
        StringBuilder sb = new StringBuilder();
        synchronized (LOCK) {
            int taken = 0;
            for (java.util.Iterator<String> it = EVENTS.descendingIterator(); it.hasNext() && taken < limit; taken++) {
                sb.append(it.next()).append('\n');
            }
        }
        return sb.length() == 0 ? "(no events yet)" : sb.toString();
    }

    /** Newest-first list of recent Issue summaries (capped). */
    public static String renderIssues(int limit) {
        StringBuilder sb = new StringBuilder();
        synchronized (LOCK) {
            int taken = 0;
            for (java.util.Iterator<String> it = ISSUES.descendingIterator(); it.hasNext() && taken < limit; taken++) {
                sb.append(it.next()).append('\n');
            }
        }
        return sb.length() == 0 ? "(no issues raised yet)" : sb.toString();
    }

    private static String formatIssueSummary(Issue issue) {
        if (issue == null) {
            return "[null issue]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(safe(issue.getTag())).append(']')
                .append(" type=").append(issue.getType());
        if (!TextUtils.isEmpty(issue.getKey())) {
            sb.append(" key=").append(issue.getKey());
        }
        JSONObject c = issue.getContent();
        if (c != null) {
            String d = extractIssueDigest(issue);
            if (!TextUtils.isEmpty(d)) {
                sb.append(' ').append(d);
            }
        }
        return sb.toString();
    }

    /** Best-effort short digest used in the events log to keep it readable. */
    private static String extractIssueDigest(Issue issue) {
        if (issue == null || issue.getContent() == null) {
            return "(empty)";
        }
        JSONObject c = issue.getContent();
        Map<String, String> first = new HashMap<>();
        // Common Matrix/TraceHarbor fields
        for (String key : new String[]{"detail", "scene", "cost", "fps", "process",
                "stackKey", "key", "subType", "memory", "is_warm_start_up",
                "startup_duration", "application_create", "first_activity_create"}) {
            if (c.has(key)) {
                first.put(key, c.opt(key) == null ? "null" : c.opt(key).toString());
            }
        }
        if (first.isEmpty()) {
            String s = c.toString();
            return s.length() <= 120 ? s : s.substring(0, 117) + "...";
        }
        StringBuilder out = new StringBuilder();
        boolean firstEntry = true;
        for (Map.Entry<String, String> e : first.entrySet()) {
            if (!firstEntry) {
                out.append(' ');
            }
            firstEntry = false;
            out.append(e.getKey()).append('=');
            String v = e.getValue();
            out.append(v.length() <= 60 ? v : v.substring(0, 57) + "...");
        }
        return out.toString();
    }

    private static String safe(String s) {
        return s == null ? "?" : s;
    }
}
