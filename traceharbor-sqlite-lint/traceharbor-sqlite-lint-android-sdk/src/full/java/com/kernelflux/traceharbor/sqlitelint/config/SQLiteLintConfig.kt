package com.kernelflux.traceharbor.sqlitelint.config

import android.database.sqlite.SQLiteDatabase
import android.text.TextUtils
import com.kernelflux.traceharbor.sqlitelint.SQLiteLint
import com.kernelflux.traceharbor.sqlitelint.SimpleSQLiteExecutionDelegate

class SQLiteLintConfig(sqlExecutionCallbackMode: SQLiteLint.SqlExecutionCallbackMode) {
    private val sConcernDbList = ArrayList<ConcernDb>()

    init {
        SQLiteLint.setSqlExecutionCallbackMode(sqlExecutionCallbackMode)
    }

    fun addConcernDB(concernDB: ConcernDb?) {
        if (concernDB == null) {
            return
        }
        if (concernDB.mInstallEnv == null) {
            return
        }

        val concernDbPath = concernDB.mInstallEnv!!.getConcernedDbPath()
        if (TextUtils.isEmpty(concernDbPath)) {
            return
        }

        for (i in sConcernDbList.indices) {
            if (concernDbPath == concernDB.mInstallEnv!!.getConcernedDbPath()) {
                return
            }
        }

        sConcernDbList.add(concernDB)
    }

    val concernDbList: List<ConcernDb>
        get() = sConcernDbList

    class ConcernDb {
        internal val mInstallEnv: SQLiteLint.InstallEnv?
        private val mOptions: SQLiteLint.Options
        private var mWhiteListXmlResId: Int = 0
        private val mEnableCheckerList: MutableList<String> = ArrayList()

        constructor(installEnv: SQLiteLint.InstallEnv, options: SQLiteLint.Options) {
            mInstallEnv = installEnv
            mOptions = options
        }

        constructor(db: SQLiteDatabase?) {
            assert(db != null)
            mInstallEnv = SQLiteLint.InstallEnv(db!!.path, SimpleSQLiteExecutionDelegate(db))
            mOptions = SQLiteLint.Options.LAX
        }

        fun setWhiteListXml(xmlResId: Int): ConcernDb {
            mWhiteListXmlResId = xmlResId
            return this
        }

        val installEnv: SQLiteLint.InstallEnv
            get() = mInstallEnv!!

        val options: SQLiteLint.Options
            get() = mOptions

        val whiteListXmlResId: Int
            get() = mWhiteListXmlResId

        fun enableAllCheckers(): ConcernDb {
            return enableExplainQueryPlanChecker()
                .enableAvoidSelectAllChecker()
                .enableWithoutRowIdBetterChecker()
                .enableAvoidAutoIncrementChecker()
                .enablePreparedStatementBetterChecker()
                .enableRedundantIndexChecker()
        }

        fun enableExplainQueryPlanChecker(): ConcernDb {
            return enableChecker(EXPLAIN_QUERY_PLAN_CHECKER_NAME)
        }

        fun enableAvoidSelectAllChecker(): ConcernDb {
            return enableChecker(AVOID_SELECT_ALL_CHECKER_NAME)
        }

        fun enableWithoutRowIdBetterChecker(): ConcernDb {
            return enableChecker(WITHOUT_ROWID_BETTER_CHECKER_NAME)
        }

        fun enableAvoidAutoIncrementChecker(): ConcernDb {
            return enableChecker(AVOID_AUTO_INCREMENT_CHECKER_NAME)
        }

        fun enablePreparedStatementBetterChecker(): ConcernDb {
            return enableChecker(PREPARED_STATEMENT_BETTER_CHECKER_NAME)
        }

        fun enableRedundantIndexChecker(): ConcernDb {
            return enableChecker(REDUNDANT_INDEX_CHECKER_NAME)
        }

        val enableCheckerList: List<String>
            get() = mEnableCheckerList

        private fun enableChecker(checkerName: String): ConcernDb {
            mEnableCheckerList.add(checkerName)
            return this
        }

        companion object {
            private const val EXPLAIN_QUERY_PLAN_CHECKER_NAME = "ExplainQueryPlanChecker"
            private const val AVOID_SELECT_ALL_CHECKER_NAME = "AvoidSelectAllChecker"
            private const val WITHOUT_ROWID_BETTER_CHECKER_NAME = "WithoutRowIdBetterChecker"
            private const val AVOID_AUTO_INCREMENT_CHECKER_NAME = "AvoidAutoIncrementChecker"
            private const val PREPARED_STATEMENT_BETTER_CHECKER_NAME = "PreparedStatementBetterChecker"
            private const val REDUNDANT_INDEX_CHECKER_NAME = "RedundantIndexChecker"
        }
    }
}
