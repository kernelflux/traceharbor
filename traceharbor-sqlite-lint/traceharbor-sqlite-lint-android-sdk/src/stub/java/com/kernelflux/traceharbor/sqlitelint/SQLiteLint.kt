package com.kernelflux.traceharbor.sqlitelint

import android.content.Context
import android.database.sqlite.SQLiteDatabase

class SQLiteLint private constructor() {
    class InstallEnv(concernedDbPath: String, executionDelegate: ISQLiteExecutionDelegate) {
        private val mConcernedDbPath: String
        private val mSQLiteExecutionDelegate: ISQLiteExecutionDelegate

        init {
            mConcernedDbPath = concernedDbPath
            mSQLiteExecutionDelegate = executionDelegate
        }

        fun getConcernedDbPath(): String {
            return mConcernedDbPath
        }

        fun getSQLiteExecutionDelegate(): ISQLiteExecutionDelegate {
            return mSQLiteExecutionDelegate
        }
    }

    enum class SqlExecutionCallbackMode {
        HOOK,
        CUSTOM_NOTIFY
    }

    class Options {
        private var behaviourMask: Int = 0

        fun isAlertBehaviourEnable(): Boolean {
            return behaviourMask and BEHAVIOUR_ALERT > 0
        }

        fun isReportBehaviourEnable(): Boolean {
            return behaviourMask and BEHAVIOUR_REPORT > 0
        }

        class Builder {
            private var mBehaviourMask: Int = 0

            init {
                mBehaviourMask = mBehaviourMask or BEHAVIOUR_ALERT
            }

            fun setAlertBehaviour(enable: Boolean): Builder {
                if (enable) {
                    mBehaviourMask = mBehaviourMask or BEHAVIOUR_ALERT
                } else {
                    mBehaviourMask = mBehaviourMask and BEHAVIOUR_ALERT.inv()
                }
                return this
            }

            fun setReportBehaviour(enable: Boolean): Builder {
                if (enable) {
                    mBehaviourMask = mBehaviourMask or BEHAVIOUR_REPORT
                } else {
                    mBehaviourMask = mBehaviourMask and BEHAVIOUR_REPORT.inv()
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

        @JvmStatic
        fun setSqlExecutionCallbackMode(sqlExecutionCallbackMode: SqlExecutionCallbackMode) {
        }

        @JvmStatic
        fun getSqlExecutionCallbackMode(): SqlExecutionCallbackMode {
            return SqlExecutionCallbackMode.HOOK
        }

        @JvmStatic
        fun install(context: Context, db: SQLiteDatabase) {
        }

        @JvmStatic
        fun install(context: Context, installEnv: InstallEnv, options: Options) {
        }

        @JvmStatic
        fun notifySqlExecution(concernedDbPath: String, sql: String, executeTime: Int) {
        }

        @JvmStatic
        fun uninstall(concernedDbPath: String) {
        }

        @JvmStatic
        fun setWhiteList(concernedDbPath: String, xmlResId: Int) {
        }

        @JvmStatic
        fun enableCheckers(concernedDbPath: String, enableCheckerList: List<String>) {
        }
    }
}
