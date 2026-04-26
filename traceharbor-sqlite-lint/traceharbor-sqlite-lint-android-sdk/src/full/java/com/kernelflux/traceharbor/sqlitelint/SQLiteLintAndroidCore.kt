package com.kernelflux.traceharbor.sqlitelint

import android.content.Context
import com.kernelflux.traceharbor.sqlitelint.behaviour.BaseBehaviour
import com.kernelflux.traceharbor.sqlitelint.behaviour.alert.IssueAlertBehaviour
import com.kernelflux.traceharbor.sqlitelint.behaviour.persistence.IssueStorage
import com.kernelflux.traceharbor.sqlitelint.behaviour.persistence.PersistenceBehaviour
import com.kernelflux.traceharbor.sqlitelint.behaviour.persistence.SQLiteLintDbHelper
import com.kernelflux.traceharbor.sqlitelint.behaviour.report.IssueReportBehaviour
import com.kernelflux.traceharbor.sqlitelint.util.SQLite3ProfileHooker
import com.kernelflux.traceharbor.sqlitelint.util.SQLiteLintUtil

class SQLiteLintAndroidCore(
    private val mContext: Context,
    installEnv: SQLiteLint.InstallEnv,
    options: SQLiteLint.Options
) : IOnIssuePublishListener {

    private val mConcernedDbPath: String = installEnv.getConcernedDbPath()
    private val mSQLiteExecutionDelegate: ISQLiteExecutionDelegate = installEnv.getSQLiteExecutionDelegate()
    private val mBehaviors = ArrayList<BaseBehaviour>()

    init {
        SQLiteLintDbHelper.INSTANCE.initialize(mContext)

        if (SQLiteLint.getSqlExecutionCallbackMode() == SQLiteLint.SqlExecutionCallbackMode.HOOK) {
            SQLite3ProfileHooker.hook()
        }

        SQLiteLintNativeBridge.nativeInstall(mConcernedDbPath)
        mBehaviors.add(PersistenceBehaviour())
        if (options.isAlertBehaviourEnable) {
            mBehaviors.add(IssueAlertBehaviour(mContext, mConcernedDbPath))
        }
        if (options.isReportBehaviourEnable) {
            mBehaviors.add(IssueReportBehaviour(SQLiteLint.sReportDelegate))
        }
    }

    fun addBehavior(behaviour: BaseBehaviour) {
        if (!mBehaviors.contains(behaviour)) {
            mBehaviors.add(behaviour)
        }
    }

    fun removeBehavior(behaviour: BaseBehaviour) {
        mBehaviors.remove(behaviour)
    }

    fun release() {
        if (SQLiteLint.getSqlExecutionCallbackMode() == SQLiteLint.SqlExecutionCallbackMode.HOOK) {
            SQLite3ProfileHooker.unHook()
        }
        SQLiteLintNativeBridge.nativeUninstall(mConcernedDbPath)
    }

    fun getSQLiteExecutionDelegate(): ISQLiteExecutionDelegate {
        return mSQLiteExecutionDelegate
    }

    fun notifySqlExecution(dbPath: String, sql: String, timeCost: Long) {
        var extInfoStack = "null"
        if (timeCost >= 8) {
            extInfoStack = SQLiteLintUtil.getThrowableStack(Throwable())
        }
        SQLiteLintNativeBridge.nativeNotifySqlExecute(dbPath, sql, timeCost, extInfoStack)
    }

    fun setWhiteList(xmlResId: Int) {
        CheckerWhiteListLogic.setWhiteList(mContext, mConcernedDbPath, xmlResId)
    }

    fun enableCheckers(enableCheckers: List<String>) {
        val enableCheckerArr = Array(enableCheckers.size) { i -> enableCheckers[i] }
        SQLiteLintNativeBridge.nativeEnableCheckers(mConcernedDbPath, enableCheckerArr)
    }

    override fun onPublish(publishedIssues: List<SQLiteLintIssue>?) {
        if (publishedIssues == null) {
            return
        }
        for (issue in publishedIssues) {
            issue.isNew = !IssueStorage.hasIssue(issue.id!!)
        }
        for (behavior in mBehaviors) {
            behavior.onPublish(publishedIssues)
        }
    }
}
