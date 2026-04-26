package com.kernelflux.traceharbor.sqlitelint.config

import android.database.sqlite.SQLiteDatabase
import com.kernelflux.traceharbor.sqlitelint.SQLiteLint
import java.util.ArrayList

class SQLiteLintConfig(sqlExecutionCallbackMode: SQLiteLint.SqlExecutionCallbackMode?) {
    init {
        if (sqlExecutionCallbackMode != null) {
        }
    }

    fun addConcernDB(concernDB: ConcernDb) {
    }

    fun getConcernDbList(): List<ConcernDb> {
        return ArrayList()
    }

    class ConcernDb {
        constructor(installEnv: SQLiteLint.InstallEnv?, options: SQLiteLint.Options?) {
            if (installEnv != null && options != null) {
            }
        }

        constructor(db: SQLiteDatabase?) {
            if (db != null) {
            }
        }

        fun setWhiteListXml(xmlResId: Int): ConcernDb {
            return this
        }

        fun getInstallEnv(): SQLiteLint.InstallEnv? {
            return null
        }

        fun getOptions(): SQLiteLint.Options? {
            return null
        }

        fun getWhiteListXmlResId(): Int {
            return -1
        }

        fun enableAllCheckers(): ConcernDb {
            return this
        }

        fun enableExplainQueryPlanChecker(): ConcernDb {
            return this
        }

        fun enableAvoidSelectAllChecker(): ConcernDb {
            return this
        }

        fun enableWithoutRowIdBetterChecker(): ConcernDb {
            return this
        }

        fun enableAvoidAutoIncrementChecker(): ConcernDb {
            return this
        }

        fun enablePreparedStatementBetterChecker(): ConcernDb {
            return this
        }

        fun enableRedundantIndexChecker(): ConcernDb {
            return this
        }

        fun getEnableCheckerList(): List<String> {
            return ArrayList()
        }
    }
}
