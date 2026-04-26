package com.kernelflux.traceharbor.sqlitelint

import android.database.Cursor
import android.database.SQLException
import com.kernelflux.traceharbor.sqlitelint.util.SLog

class SQLiteLintNativeBridge {
    private external fun execSqlCallback(
        execSqlCallbackPtr: Long,
        paraPtr: Long,
        dbName: String,
        nColumn: Int,
        columnValue: Array<String>,
        columnName: Array<String>
    )

    private fun sqliteLintExecSql(
        dbPath: String,
        sql: String,
        needCallBack: Boolean,
        execSqlCallbackPtr: Long,
        paraPtr: Long
    ): Array<String> {
        val retObj = arrayOf("", "-1")
        try {
            SLog.i(TAG, "dbPath %s, sql is %s ,needCallBack: %b", dbPath, sql, needCallBack)
            var executionDelegate: ISQLiteExecutionDelegate? = null
            val core = SQLiteLintAndroidCoreManager.INSTANCE.get(dbPath)
            if (core != null) {
                executionDelegate = core.getSQLiteExecutionDelegate()
            }
            if (executionDelegate == null) {
                SLog.w(TAG, "sqliteLintExecSql mExecSqlImp is null")
                return retObj
            }
            if (needCallBack) {
                try {
                    val cu = executionDelegate.rawQuery(sql)
                    if (cu == null || cu.count < 0) {
                        SLog.w(TAG, "sqliteLintExecSql cu is null")
                        retObj[0] = "Cursor is null"
                    } else {
                        doExecSqlCallback(execSqlCallbackPtr, paraPtr, dbPath, cu)
                        retObj[1] = "0"
                    }
                    cu?.close()
                } catch (e: Exception) {
                    SLog.w(TAG, "sqliteLintExecSql rawQuery exp: %s", e.message)
                    retObj[0] = e.message ?: ""
                }
            } else {
                try {
                    executionDelegate.execSQL(sql)
                    retObj[1] = "0"
                } catch (e: SQLException) {
                    SLog.w(TAG, "sqliteLintExecSql execSQL exp: %s", e.message)
                    retObj[0] = e.message ?: ""
                }
            }
        } catch (e: Throwable) {
            SLog.e(TAG, "sqliteLintExecSql ex ", e.message)
        }
        return retObj
    }

    private fun doExecSqlCallback(execSqlCallbackPtr: Long, paraPtr: Long, dbName: String, cu: Cursor?) {
        if (cu == null) {
            SLog.w(TAG, "doExecSqlCallback cu is null")
            return
        }
        while (cu.moveToNext()) {
            val columnCount = cu.columnCount
            val name = Array(columnCount) { "" }
            val value = Array(columnCount) { "" }
            for (i in 0 until columnCount) {
                name[i] = cu.getColumnName(i)
                value[i] = when (cu.getType(i)) {
                    Cursor.FIELD_TYPE_BLOB -> cu.getBlob(i).toString()
                    Cursor.FIELD_TYPE_INTEGER -> cu.getLong(i).toString()
                    Cursor.FIELD_TYPE_STRING -> cu.getString(i).toString()
                    Cursor.FIELD_TYPE_FLOAT -> cu.getFloat(i).toString()
                    else -> ""
                }
            }
            execSqlCallback(execSqlCallbackPtr, paraPtr, dbName, columnCount, value, name)
        }
    }

    companion object {
        private const val TAG = "SQLiteLint.SQLiteLintNativeBridge"

        @JvmStatic
        fun loadLibrary() {
            System.loadLibrary("SqliteLint-lib")
            SLog.nativeSetLogger(android.util.Log.VERBOSE)
        }

        @JvmStatic
        external fun nativeInstall(dbPath: String)

        @JvmStatic
        external fun nativeUninstall(dbPath: String)

        @JvmStatic
        external fun nativeNotifySqlExecute(dbPath: String, sql: String, executeTime: Long, extInfo: String)

        @JvmStatic
        external fun nativeAddToWhiteList(dbPath: String, checkerArr: Array<String>, whiteListArr: Array<Array<String>>)

        @JvmStatic
        external fun nativeEnableCheckers(dbPath: String, enableCheckerArr: Array<String>)

        @JvmStatic
        private fun onPublishIssue(dbName: String, publishedIssues: ArrayList<SQLiteLintIssue>) {
            try {
                SQLiteLintAndroidCoreManager.INSTANCE.get(dbName)?.onPublish(publishedIssues)
            } catch (e: Throwable) {
                SLog.e(TAG, "onPublishIssue ex ", e.message)
            }
        }
    }
}
