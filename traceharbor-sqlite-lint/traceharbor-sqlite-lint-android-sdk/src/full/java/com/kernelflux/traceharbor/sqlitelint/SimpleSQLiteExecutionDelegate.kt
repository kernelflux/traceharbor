package com.kernelflux.traceharbor.sqlitelint

import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import com.kernelflux.traceharbor.sqlitelint.util.SLog

class SimpleSQLiteExecutionDelegate(db: SQLiteDatabase?) : ISQLiteExecutionDelegate {
    private val mDb: SQLiteDatabase

    init {
        assert(db != null)
        mDb = db!!
    }

    @Throws(SQLException::class)
    override fun rawQuery(selection: String, vararg selectionArgs: String): Cursor? {
        if (!mDb.isOpen) {
            SLog.w(TAG, "rawQuery db close")
            return null
        }
        return mDb.rawQuery(selection, selectionArgs)
    }

    @Throws(SQLException::class)
    override fun execSQL(sql: String) {
        if (!mDb.isOpen) {
            SLog.w(TAG, "rawQuery db close")
            return
        }
        mDb.execSQL(sql)
    }

    companion object {
        private const val TAG = "SQLiteLint.SimpleSQLiteExecutionDelegate"
    }
}
