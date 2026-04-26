package com.kernelflux.traceharbor.sqlitelint

import android.database.Cursor
import android.database.SQLException

interface ISQLiteExecutionDelegate {
    @Throws(SQLException::class)
    fun rawQuery(selection: String, vararg selectionArgs: String): Cursor?

    @Throws(SQLException::class)
    fun execSQL(sql: String)
}
