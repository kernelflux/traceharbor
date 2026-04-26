package com.kernelflux.traceharbor.sqlitelint.behaviour.persistence

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.kernelflux.traceharbor.sqlitelint.util.SLog

enum class SQLiteLintDbHelper {
    INSTANCE;

    @Volatile
    private var mHelper: InternalDbHelper? = null

    fun getDatabase(): SQLiteDatabase {
        val helper = mHelper ?: throw IllegalStateException("getIssueStorage db not ready")
        return helper.writableDatabase
    }

    fun initialize(context: Context) {
        if (mHelper == null) {
            synchronized(this) {
                if (mHelper == null) {
                    mHelper = InternalDbHelper(context)
                }
            }
        }
    }

    private class InternalDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, VERSION_1) {
        override fun onCreate(db: SQLiteDatabase) {
            SLog.i(TAG, "onCreate")
            db.execSQL(IssueStorage.DB_VERSION_1_CREATE_SQL)
            for (indexSql in IssueStorage.DB_VERSION_1_CREATE_INDEX) {
                db.execSQL(indexSql)
            }
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        }
    }

    companion object {
        private const val TAG = "SQLiteLint.SQLiteLintOwnDatabase"
        private const val DB_NAME = "SQLiteLintInternal.db"
        private const val VERSION_1 = 1
    }
}
