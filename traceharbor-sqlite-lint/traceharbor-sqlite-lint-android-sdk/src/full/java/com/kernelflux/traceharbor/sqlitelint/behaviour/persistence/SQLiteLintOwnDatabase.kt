package com.kernelflux.traceharbor.sqlitelint.behaviour.persistence

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import com.kernelflux.traceharbor.sqlitelint.util.SLog
import com.kernelflux.traceharbor.sqlitelint.util.SQLiteLintUtil

@Deprecated("temperately")
enum class SQLiteLintOwnDatabase {
    INSTANCE;

    @Volatile
    private var mDatabase: SQLiteDatabase? = null
    private var mIsInitializing: Boolean = false

    fun getDatabase(): SQLiteDatabase {
        if (mDatabase == null || !mDatabase!!.isOpen) {
            synchronized(this) {
                if (mDatabase == null || !mDatabase!!.isOpen) {
                    mDatabase = openOrCreateDatabase()
                }
            }
        }
        return mDatabase!!
    }

    @Synchronized
    fun closeDatabase() {
        if (mIsInitializing) {
            throw IllegalStateException("Closed during initialization")
        }
        if (mDatabase != null && mDatabase!!.isOpen) {
            mDatabase!!.close()
            mDatabase = null
        }
    }

    private fun onCreate(db: SQLiteDatabase) {
        SLog.i(TAG, "onCreate")
        db.execSQL(IssueStorage.DB_VERSION_1_CREATE_SQL)
        for (indexSql in IssueStorage.DB_VERSION_1_CREATE_INDEX) {
            db.execSQL(indexSql)
        }
    }

    private fun onUpgrade(db: SQLiteDatabase, oldVersion: Int) {
        SLog.i(TAG, "onUpgrade oldVersion=%d, newVersion=%d", oldVersion, NEW_VERSION)
    }

    private fun openOrCreateDatabase(): SQLiteDatabase {
        if (mIsInitializing) {
            throw IllegalStateException("getDatabase called recursively")
        }
        if (SQLiteLintUtil.isNullOrNil(sOwnDbDirectory)) {
            throw IllegalStateException("OwnDbDirectory not init")
        }
        try {
            mIsInitializing = true
            val databasePath = String.format("%s/%s", sOwnDbDirectory, DATABASE_NAME)
            SLog.i(TAG, "openOrCreateDatabase path=%s", databasePath)
            SQLiteLintUtil.mkdirs(databasePath)
            val db = SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.CREATE_IF_NECESSARY, null)
            val version = db.version
            if (version != NEW_VERSION) {
                db.beginTransaction()
                try {
                    if (version == 0) {
                        onCreate(db)
                    } else if (version != NEW_VERSION) {
                        onUpgrade(db, version)
                    }
                    db.version = NEW_VERSION
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
            return db
        } finally {
            mIsInitializing = false
        }
    }

    companion object {
        private const val TAG = "SQLiteLint.SQLiteLintOwnDatabase"
        private val ROOT_PATH =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        private const val DATABASE_DIRECTORY = "database"
        private const val DATABASE_NAME = "own.db"
        private const val VERSION_1 = 1
        private const val NEW_VERSION = VERSION_1

        private var sOwnDbDirectory: String = ""

        @JvmStatic
        fun setOwnDbDirectory(context: Context) {
            if (!SQLiteLintUtil.isNullOrNil(sOwnDbDirectory)) {
                return
            }
            sOwnDbDirectory =
                String.format("%s/SQLiteLint-%s/%s/", ROOT_PATH, context.packageManager, DATABASE_DIRECTORY)
        }
    }
}
