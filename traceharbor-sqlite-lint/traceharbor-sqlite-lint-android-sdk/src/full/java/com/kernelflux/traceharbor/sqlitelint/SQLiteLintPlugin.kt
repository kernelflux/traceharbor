package com.kernelflux.traceharbor.sqlitelint

import android.app.Application
import android.content.Context
import com.kernelflux.traceharbor.plugin.Plugin
import com.kernelflux.traceharbor.plugin.PluginListener
import com.kernelflux.traceharbor.report.Issue
import com.kernelflux.traceharbor.sqlitelint.behaviour.report.IssueReportBehaviour
import com.kernelflux.traceharbor.sqlitelint.config.SQLiteLintConfig
import com.kernelflux.traceharbor.sqlitelint.config.SharePluginInfo
import com.kernelflux.traceharbor.sqlitelint.util.SLog
import com.kernelflux.traceharbor.util.DeviceUtil
import org.json.JSONException
import org.json.JSONObject

class SQLiteLintPlugin(private val mConfig: SQLiteLintConfig) : Plugin() {
    private lateinit var mContext: Context

    override fun init(app: Application, listener: PluginListener) {
        super.init(app, listener)
        SQLiteLint.init()
        SQLiteLint.setPackageName(app)
        mContext = app.applicationContext
    }

    override fun start() {
        super.start()
        if (!isSupported) {
            return
        }

        SQLiteLint.setReportDelegate(object : IssueReportBehaviour.IReportDelegate {
            override fun report(issue: SQLiteLintIssue?) {
                if (issue == null) {
                    return
                }
                reportTraceHarborIssue(issue)
            }
        })

        val concernDbList = mConfig.concernDbList
        for (concernDb in concernDbList) {
            val concernedDbPath = concernDb.installEnv.getConcernedDbPath()
            SQLiteLint.install(mContext, concernDb.installEnv, concernDb.options)
            SQLiteLint.setWhiteList(concernedDbPath, concernDb.whiteListXmlResId)
            SQLiteLint.enableCheckers(concernedDbPath, concernDb.enableCheckerList)
        }
    }

    override fun stop() {
        super.stop()
        if (!isSupported) {
            return
        }

        val concernDbList = mConfig.concernDbList
        for (concernDb in concernDbList) {
            SQLiteLint.uninstall(concernDb.installEnv.getConcernedDbPath())
        }

        SQLiteLint.setReportDelegate(null)
    }

    override fun destroy() {
        super.destroy()
    }

    override val tag: String
        get() = SharePluginInfo.TAG_PLUGIN

    fun notifySqlExecution(concernedDbPath: String, sql: String, timeCost: Int) {
        if (!isPluginStarted()) {
            SLog.i(TAG, "notifySqlExecution isPluginStarted not")
            return
        }
        SQLiteLint.notifySqlExecution(concernedDbPath, sql, timeCost)
    }

    fun addConcernedDB(concernDb: SQLiteLintConfig.ConcernDb?) {
        if (!isPluginStarted()) {
            SLog.i(TAG, "addConcernedDB isPluginStarted not")
            return
        }
        if (concernDb == null) {
            return
        }

        mConfig.addConcernDB(concernDb)
        val concernedDbPath = concernDb.installEnv.getConcernedDbPath()
        SQLiteLint.install(mContext, concernDb.installEnv, concernDb.options)
        SQLiteLint.setWhiteList(concernedDbPath, concernDb.whiteListXmlResId)
        SQLiteLint.enableCheckers(concernedDbPath, concernDb.enableCheckerList)
    }

    private fun reportTraceHarborIssue(lintIssue: SQLiteLintIssue) {
        SLog.i(TAG, "reportTraceHarborIssue type:%d, isNew %b", lintIssue.type, lintIssue.isNew)
        if (!lintIssue.isNew) {
            return
        }

        val issue = Issue(lintIssue.type)
        issue.key = lintIssue.id
        val content = JSONObject()
        issue.content = content
        try {
            val app = application ?: return
            content.put(DeviceUtil.DEVICE_MACHINE, DeviceUtil.getLevel(app))
            content.put(SharePluginInfo.ISSUE_KEY_ID, lintIssue.id)
            content.put(SharePluginInfo.ISSUE_KEY_DB_PATH, lintIssue.dbPath)
            content.put(SharePluginInfo.ISSUE_KEY_LEVEL, lintIssue.level)
            content.put(SharePluginInfo.ISSUE_KEY_SQL, lintIssue.sql)
            content.put(SharePluginInfo.ISSUE_KEY_TABLE, lintIssue.table)
            content.put(SharePluginInfo.ISSUE_KEY_DESC, lintIssue.desc)
            content.put(SharePluginInfo.ISSUE_KEY_DETAIL, lintIssue.detail)
            content.put(SharePluginInfo.ISSUE_KEY_ADVICE, lintIssue.advice)
            content.put(SharePluginInfo.ISSUE_KEY_CREATE_TIME, lintIssue.createTime)
            content.put(SharePluginInfo.ISSUE_KEY_STACK, lintIssue.extInfo)
            content.put(SharePluginInfo.ISSUE_KEY_SQL_TIME_COST, lintIssue.sqlTimeCost)
            content.put(SharePluginInfo.ISSUE_KEY_IS_IN_MAIN_THREAD, lintIssue.isInMainThread)
        } catch (e: JSONException) {
            SLog.i(TAG, "reportTraceHarborIssue e:%s", e.localizedMessage)
        }
        onDetectIssue(issue)
    }

    companion object {
        private const val TAG = "TraceHarbor.SQLiteLintPlugin"
    }
}
