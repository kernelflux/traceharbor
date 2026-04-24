package com.kernelflux.traceharborsample;

import android.app.Activity;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.TextView;

import com.kernelflux.traceharbor.sqlitelint.SQLiteLint;

import java.io.File;

/**
 * Smoke screen for SQLiteLintPlugin. Creates a tiny SQLite database, runs a deliberately
 * inefficient query, and notifies SQLiteLint via the CUSTOM_NOTIFY callback so the plugin's
 * checkers run on it.
 */
public class TestSqliteLintActivity extends Activity {

    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sqlite_lint);

        statusView = findViewById(R.id.sqlite_status);
        findViewById(R.id.sqlite_run).setOnClickListener(v -> runProbe());

        renderStatus("Tap RUN to create the demo DB and notify SQLiteLint with a slow SELECT.");
    }

    private void runProbe() {
        File dir = new File(getCacheDir(), "th-sqlite-lint");
        if (!dir.exists() && !dir.mkdirs()) {
            renderStatus("Cannot create work dir " + dir);
            return;
        }
        File dbFile = new File(dir, "demo.db");
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
            // install() must precede notifySqlExecution so the checkers register against this DB path.
            SQLiteLint.install(this, db);
            db.execSQL("CREATE TABLE IF NOT EXISTS person (id INTEGER PRIMARY KEY, name TEXT, age INTEGER)");
            db.execSQL("DELETE FROM person");
            for (int i = 0; i < 200; i++) {
                db.execSQL("INSERT INTO person(name, age) VALUES (?, ?)",
                        new Object[]{"name_" + i, i % 80});
            }

            String badSql = "SELECT * FROM person WHERE name LIKE '%name_%'";
            long start = android.os.SystemClock.elapsedRealtime();
            android.database.Cursor c = db.rawQuery(badSql, null);
            int rows = 0;
            while (c.moveToNext()) {
                rows++;
            }
            c.close();
            long cost = android.os.SystemClock.elapsedRealtime() - start;

            SQLiteLint.notifySqlExecution(dbFile.getAbsolutePath(), badSql, (int) cost);
            IssueRecorder.appendEvent("sqlitelint",
                    "Notified SQLiteLint: SELECT * with LIKE prefix wildcard, " + rows + " rows, " + cost + "ms");
            renderStatus("Notified SQLiteLint of an inefficient SELECT (" + rows + " rows, " + cost + "ms).");
        } catch (Throwable t) {
            IssueRecorder.appendEvent("error", "SQLiteLint probe failed: " + t);
            renderStatus("FAILED: " + t);
        } finally {
            if (db != null) {
                db.close();
            }
        }
    }

    private void renderStatus(String msg) {
        statusView.setText(msg);
    }
}
