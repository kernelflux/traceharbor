package com.kernelflux.traceharbor.sqlitelint

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.kernelflux.traceharbor.sqlitelint.behaviour.report.IssueReportBehaviour
import com.kernelflux.traceharbor.sqlitelint.util.SQLite3ProfileHooker

class SQLiteLint private constructor() {
    enum class SqlExecutionCallbackMode {
        HOOK,
        CUSTOM_NOTIFY,
    }

    class InstallEnv(concernedDbPath: String?, executionDelegate: ISQLiteExecutionDelegate?) {
        private val mConcernedDbPath: String
        private val mSQLiteExecutionDelegate: ISQLiteExecutionDelegate

        init {
            assert(concernedDbPath != null)
            assert(executionDelegate != null)
            mConcernedDbPath = concernedDbPath!!
            mSQLiteExecutionDelegate = executionDelegate!!
        }

        fun getConcernedDbPath(): String {
            return mConcernedDbPath
        }

        fun getSQLiteExecutionDelegate(): ISQLiteExecutionDelegate {
            return mSQLiteExecutionDelegate
        }
    }

    class Options private constructor() {
        private var behaviourMask: Int = 0

        val isAlertBehaviourEnable: Boolean
            get() = (behaviourMask and BEHAVIOUR_ALERT) > 0

        val isReportBehaviourEnable: Boolean
            get() = (behaviourMask and BEHAVIOUR_REPORT) > 0

        class Builder {
            private var mBehaviourMask: Int = 0

            init {
                mBehaviourMask = mBehaviourMask or BEHAVIOUR_ALERT
            }

            fun setAlertBehaviour(enable: Boolean): Builder {
                mBehaviourMask = if (enable) {
                    mBehaviourMask or BEHAVIOUR_ALERT
                } else {
                    mBehaviourMask and BEHAVIOUR_ALERT.inv()
                }
                return this
            }

            fun setReportBehaviour(enable: Boolean): Builder {
                mBehaviourMask = if (enable) {
                    mBehaviourMask or BEHAVIOUR_REPORT
                } else {
                    mBehaviourMask and BEHAVIOUR_REPORT.inv()
                }
                return this
            }

            fun build(): Options {
                val options = Options()
                options.behaviourMask = mBehaviourMask
                return options
            }
        }

        companion object {
            @JvmField
            val LAX: Options = Builder().build()
        }
    }

    companion object {
        private const val BEHAVIOUR_ALERT = 0x1
        private const val BEHAVIOUR_REPORT = 0x2

        private var sSqlExecutionCallbackMode: SqlExecutionCallbackMode? = null

        @JvmField
        var sReportDelegate: IssueReportBehaviour.IReportDelegate? = null

        @JvmField
        var sPackageName: String? = null

        @JvmStatic
        fun init() {
            SQLiteLintNativeBridge.loadLibrary()
        }

        @JvmStatic
        fun setSqlExecutionCallbackMode(sqlExecutionCallbackMode: SqlExecutionCallbackMode?) {
            if (sSqlExecutionCallbackMode != null) {
                return
            }
            sSqlExecutionCallbackMode = sqlExecutionCallbackMode
            if (sSqlExecutionCallbackMode == SqlExecutionCallbackMode.HOOK) {
                SQLite3ProfileHooker.hook()
            }
        }

        @JvmStatic
        fun getSqlExecutionCallbackMode(): SqlExecutionCallbackMode? {
            return sSqlExecutionCallbackMode
        }

        @JvmStatic
        fun install(context: Context, db: SQLiteDatabase?) {
            assert(db != null)
            assert(sSqlExecutionCallbackMode != null) {
                "SqlExecutionCallbackMode not set！setSqlExecutionCallbackMode must be called before install"
            }
            val installEnv = InstallEnv(db!!.path, SimpleSQLiteExecutionDelegate(db))
            SQLiteLintAndroidCoreManager.INSTANCE.install(context, installEnv, Options.LAX)
        }

        @JvmStatic
        fun install(context: Context, installEnv: InstallEnv?, options: Options?) {
            assert(installEnv != null)
            assert(sSqlExecutionCallbackMode != null) {
                "SqlExecutionCallbackMode is UNKNOWN！setSqlExecutionCallbackMode must be called before install"
            }
            val installOptions = options ?: Options.LAX
            SQLiteLintAndroidCoreManager.INSTANCE.install(context, installEnv!!, installOptions)
        }

        @JvmStatic
        fun notifySqlExecution(concernedDbPath: String, sql: String, timeCost: Int) {
            val core = SQLiteLintAndroidCoreManager.INSTANCE.get(concernedDbPath) ?: return
            core.notifySqlExecution(concernedDbPath, sql, timeCost.toLong())
        }

        @JvmStatic
        fun uninstall(concernedDbPath: String) {
            SQLiteLintAndroidCoreManager.INSTANCE.get(concernedDbPath)!!.release()
            SQLiteLintAndroidCoreManager.INSTANCE.remove(concernedDbPath)
        }

        @JvmStatic
        fun setWhiteList(concernedDbPath: String, xmlResId: Int) {
            val core = SQLiteLintAndroidCoreManager.INSTANCE.get(concernedDbPath) ?: return
            core.setWhiteList(xmlResId)
        }

        @JvmStatic
        fun enableCheckers(concernedDbPath: String, enableCheckerList: List<String>?) {
            val core = SQLiteLintAndroidCoreManager.INSTANCE.get(concernedDbPath) ?: return
            if (enableCheckerList == null || enableCheckerList.isEmpty()) {
                return
            }
            core.enableCheckers(enableCheckerList)
        }

        @JvmStatic
        fun setReportDelegate(reportDelegate: IssueReportBehaviour.IReportDelegate?) {
            sReportDelegate = reportDelegate
        }

        @JvmStatic
        fun setPackageName(context: Context) {
            if (sPackageName == null) {
                sPackageName = context.packageName
            }
        }
    }
}
