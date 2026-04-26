package com.kernelflux.traceharbor.sqlitelint.behaviour.persistence

import android.database.Cursor
import android.database.sqlite.SQLiteStatement
import com.kernelflux.traceharbor.sqlitelint.SQLiteLintIssue
import com.kernelflux.traceharbor.sqlitelint.util.SLog
import com.kernelflux.traceharbor.sqlitelint.util.SQLiteLintUtil

class IssueStorage private constructor() {
    companion object {
        private const val TAG = "SQLiteLint.IssueStorage"

        const val TABLE_NAME = "Issue"
        const val COLUMN_ID = "id"
        const val COLUMN_DB_PATH = "dbPath"
        const val COLUMN_LEVEL = "level"
        const val COLUMN_DESC = "desc"
        const val COLUMN_DETAIL = "detail"
        const val COLUMN_ADVICE = "advice"
        const val COLUMN_CREATE_TIME = "createTime"
        const val COLUMN_EXT_INFO = "extInfo"
        const val COLUMN_SQL_TIME_COST = "sqlTimeCost"

        const val DB_VERSION_1_CREATE_SQL =
            "CREATE TABLE IF NOT EXISTS $TABLE_NAME ($COLUMN_ID TEXT PRIMARY KEY NOT NULL, " +
                "$COLUMN_DB_PATH TEXT NOT NULL, $COLUMN_LEVEL INTEGER, $COLUMN_DESC TEXT, " +
                "$COLUMN_DETAIL TEXT, $COLUMN_ADVICE TEXT, $COLUMN_CREATE_TIME INTEGER, " +
                "$COLUMN_EXT_INFO TEXT, $COLUMN_SQL_TIME_COST INTEGER)"

        @JvmField
        val DB_VERSION_1_CREATE_INDEX = arrayOf(
            "CREATE INDEX IF NOT EXISTS DbLabel_Index ON $TABLE_NAME($COLUMN_DB_PATH)",
            "CREATE INDEX IF NOT EXISTS DbLabel_CreateTime_Index ON $TABLE_NAME($COLUMN_DB_PATH,$COLUMN_CREATE_TIME)"
        )

        private var sInsertAllSqlStatement: SQLiteStatement? = null

        @JvmStatic
        fun saveIssue(issue: SQLiteLintIssue): Boolean {
            if (hasIssue(issue.id)) {
                SLog.i(TAG, "saveIssue already recorded id=%s", issue.id)
                return false
            }
            return doInsertIssue(issue)
        }

        @JvmStatic
        fun saveIssues(issues: List<SQLiteLintIssue>) {
            SQLiteLintDbHelper.INSTANCE.getDatabase().beginTransaction()
            try {
                for (issue in issues) {
                    saveIssue(issue)
                }
                SQLiteLintDbHelper.INSTANCE.getDatabase().setTransactionSuccessful()
            } finally {
                SQLiteLintDbHelper.INSTANCE.getDatabase().endTransaction()
            }
        }

        @JvmStatic
        fun hasIssue(id: String?): Boolean {
            val querySql = "SELECT $COLUMN_ID FROM $TABLE_NAME WHERE $COLUMN_ID='$id' limit 1"
            val cursor = SQLiteLintDbHelper.INSTANCE.getDatabase().rawQuery(querySql, null)
            return try {
                cursor.count > 0
            } finally {
                cursor.close()
            }
        }

        private fun doInsertIssue(issue: SQLiteLintIssue): Boolean {
            val insertSql = getInsertAllSqlStatement()
            insertSql.bindString(1, issue.id!!)
            insertSql.bindString(2, issue.dbPath!!)
            insertSql.bindLong(3, issue.level.toLong())
            insertSql.bindString(4, SQLiteLintUtil.nullAsNil(issue.desc))
            insertSql.bindString(5, SQLiteLintUtil.nullAsNil(issue.detail))
            insertSql.bindString(6, SQLiteLintUtil.nullAsNil(issue.advice))
            insertSql.bindLong(7, issue.createTime)
            insertSql.bindString(8, issue.extInfo!!)
            insertSql.bindLong(9, issue.sqlTimeCost)
            val r = insertSql.executeInsert()

            SLog.d(TAG, "saveIssue insert ret=%s, id=%s", r, issue.id)
            if (r == -1L) {
                SLog.e(TAG, "addIssue failed")
                return false
            }
            return true
        }

        @JvmStatic
        fun getIssueListByDb(dbLabel: String?): List<SQLiteLintIssue> {
            val issueList = ArrayList<SQLiteLintIssue>()
            if (SQLiteLintUtil.isNullOrNil(dbLabel)) {
                return issueList
            }

            val querySql =
                "SELECT * FROM $TABLE_NAME where $COLUMN_DB_PATH=? ORDER BY $COLUMN_CREATE_TIME DESC"
            val cursor = SQLiteLintDbHelper.INSTANCE.getDatabase().rawQuery(querySql, arrayOf(dbLabel))
            try {
                while (cursor.moveToNext()) {
                    issueList.add(issueConvertFromCursor(cursor))
                }
            } finally {
                cursor.close()
            }
            return issueList
        }

        @JvmStatic
        fun getDbPathList(): List<String> {
            val dbPathList = ArrayList<String>()
            val querySql = "SELECT DISTINCT($COLUMN_DB_PATH) FROM $TABLE_NAME"
            val cursor = SQLiteLintDbHelper.INSTANCE.getDatabase().rawQuery(querySql, null)
            try {
                while (cursor.moveToNext()) {
                    dbPathList.add(cursor.getString(cursor.getColumnIndex(COLUMN_DB_PATH)))
                }
            } finally {
                cursor.close()
            }
            return dbPathList
        }

        @JvmStatic
        fun getLastRowId(): Long {
            val querySql = "SELECT rowid FROM $TABLE_NAME order by rowid desc limit 1"
            val cursor = SQLiteLintDbHelper.INSTANCE.getDatabase().rawQuery(querySql, null)
            return try {
                if (cursor.count > 0) {
                    cursor.moveToFirst()
                    return cursor.getLong(0)
                }
                -1
            } finally {
                cursor.close()
            }
        }

        @JvmStatic
        fun clearData() {
            val sql = "delete from $TABLE_NAME"
            SQLiteLintDbHelper.INSTANCE.getDatabase().execSQL(sql)
        }

        private fun getInsertAllSqlStatement(): SQLiteStatement {
            if (sInsertAllSqlStatement == null) {
                sInsertAllSqlStatement = SQLiteLintDbHelper.INSTANCE.getDatabase()
                    .compileStatement("INSERT INTO $TABLE_NAME VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)")
            }
            return sInsertAllSqlStatement!!
        }

        private fun issueConvertFromCursor(cursor: Cursor): SQLiteLintIssue {
            val issue = SQLiteLintIssue()
            issue.id = cursor.getString(cursor.getColumnIndex(COLUMN_ID))
            issue.dbPath = cursor.getString(cursor.getColumnIndex(COLUMN_DB_PATH))
            issue.level = cursor.getInt(cursor.getColumnIndex(COLUMN_LEVEL))
            issue.desc = cursor.getString(cursor.getColumnIndex(COLUMN_DESC))
            issue.detail = cursor.getString(cursor.getColumnIndex(COLUMN_DETAIL))
            issue.advice = cursor.getString(cursor.getColumnIndex(COLUMN_ADVICE))
            issue.createTime = cursor.getLong(cursor.getColumnIndex(COLUMN_CREATE_TIME))
            issue.extInfo = cursor.getString(cursor.getColumnIndex(COLUMN_EXT_INFO))
            issue.sqlTimeCost = cursor.getLong(cursor.getColumnIndex(COLUMN_SQL_TIME_COST))
            return issue
        }
    }
}
