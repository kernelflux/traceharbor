package com.kernelflux.traceharborsample;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * IO Canary verification — exercises the three detectors:
 * <ul>
 *   <li>main-thread IO</li>
 *   <li>small-buffer reads</li>
 *   <li>repeated-read same path</li>
 * </ul>
 */
public class IoTestActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_io);

        findViewById(R.id.io_main_button).setOnClickListener(v -> {
            try { mainThreadWriteRead(); } catch (IOException e) { reportError(e); }
        });
        findViewById(R.id.io_small_buffer_button).setOnClickListener(v -> {
            try { smallBufferRead(); } catch (IOException e) { reportError(e); }
        });
        findViewById(R.id.io_repeated_button).setOnClickListener(v -> {
            try { repeatedReadSamePath(); } catch (IOException e) { reportError(e); }
        });
    }

    private void mainThreadWriteRead() throws IOException {
        IssueRecorder.appendEvent("io", "Main-thread write+read (large file)");
        File f = new File(getCacheDir(), "th-io-large.bin");
        long start = SystemClock.elapsedRealtime();
        try (FileOutputStream out = new FileOutputStream(f, false)) {
            byte[] buf = new byte[4096];
            for (int i = 0; i < 1024; i++) {
                out.write(buf);
            }
            out.flush();
        }
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[4096];
            while (in.read(buf) != -1) { /* drain */ }
        }
        long cost = SystemClock.elapsedRealtime() - start;
        DemoApplication.reportDemoIssue("io_main", "Large IO finished in " + cost + " ms");
    }

    private void smallBufferRead() throws IOException {
        IssueRecorder.appendEvent("io", "Small-buffer read (32-byte buffer)");
        File f = ensureFile("th-io-small.bin", 8 * 1024);
        long start = SystemClock.elapsedRealtime();
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[32];
            int totalReads = 0;
            while (in.read(buf) != -1) {
                totalReads++;
            }
            IssueRecorder.appendEvent("io", "small-buffer reads=" + totalReads);
        }
        long cost = SystemClock.elapsedRealtime() - start;
        DemoApplication.reportDemoIssue("io_small_buffer", "small-buffer read " + cost + " ms");
    }

    private void repeatedReadSamePath() throws IOException {
        IssueRecorder.appendEvent("io", "Repeated read of same file 3 times");
        File f = ensureFile("th-io-repeated.bin", 4 * 1024);
        long start = SystemClock.elapsedRealtime();
        for (int round = 0; round < 3; round++) {
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] buf = new byte[1024];
                while (in.read(buf) != -1) { /* drain */ }
            }
        }
        long cost = SystemClock.elapsedRealtime() - start;
        DemoApplication.reportDemoIssue("io_repeated", "3x same-path read " + cost + " ms");
    }

    private File ensureFile(String name, int size) throws IOException {
        File f = new File(getCacheDir(), name);
        if (f.exists() && f.length() == size) {
            return f;
        }
        try (FileOutputStream out = new FileOutputStream(f, false)) {
            byte[] buf = new byte[Math.min(size, 1024)];
            int written = 0;
            while (written < size) {
                int chunk = Math.min(buf.length, size - written);
                out.write(buf, 0, chunk);
                written += chunk;
            }
            out.flush();
        }
        return f;
    }

    private void reportError(IOException e) {
        IssueRecorder.appendEvent("error", "IO failed: " + e);
    }
}
