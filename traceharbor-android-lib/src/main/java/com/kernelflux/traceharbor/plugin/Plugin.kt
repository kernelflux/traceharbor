package com.kernelflux.traceharbor.plugin

import android.app.Application
import com.kernelflux.traceharbor.lifecycle.owners.ProcessUILifecycleOwner
import com.kernelflux.traceharbor.listeners.IAppForeground
import com.kernelflux.traceharbor.report.Issue
import com.kernelflux.traceharbor.report.IssuePublisher
import com.kernelflux.traceharbor.util.TraceHarborLog
import com.kernelflux.traceharbor.util.TraceHarborUtil
import org.json.JSONException
import org.json.JSONObject

abstract class Plugin : IPlugin, IssuePublisher.OnIssueDetectListener, IAppForeground {

    private var pluginListener: PluginListener? = null
    private var applicationField: Application? = null

    var status: Int = PLUGIN_CREATE
        private set

    var isSupported: Boolean = true
        private set

    override val application: Application?
        get() = applicationField

    override fun init(application: Application, pluginListener: PluginListener) {
        if (applicationField != null || this.pluginListener != null) {
            throw RuntimeException("plugin duplicate init, application or plugin listener is not null")
        }
        status = PLUGIN_INITED
        this.applicationField = application
        this.pluginListener = pluginListener
        pluginListener.onInit(this)
        ProcessUILifecycleOwner.addListener(this)
    }

    override fun onDetectIssue(issue: Issue) {
        if (issue.tag == null) {
            issue.tag = tag
        }
        issue.plugin = this
        val content: JSONObject = issue.content ?: JSONObject().also { issue.content = it }
        try {
            issue.tag?.let { content.put(Issue.ISSUE_REPORT_TAG, it) }
            if (issue.type != 0) {
                content.put(Issue.ISSUE_REPORT_TYPE, issue.type)
            }
            content.put(Issue.ISSUE_REPORT_PROCESS, TraceHarborUtil.getProcessName(applicationField))
            content.put(Issue.ISSUE_REPORT_TIME, System.currentTimeMillis())
        } catch (e: JSONException) {
            TraceHarborLog.e(TAG, "json error", e)
        }

        pluginListener?.onReportIssue(issue)
    }

    override fun start() {
        if (isPluginDestroyed()) {
            throw RuntimeException("plugin start, but plugin has been already destroyed")
        }

        if (isPluginStarted()) {
            throw RuntimeException("plugin start, but plugin has been already started")
        }

        status = PLUGIN_STARTED

        val listener = pluginListener
            ?: throw RuntimeException("plugin start, plugin listener is null")
        listener.onStart(this)
    }

    override fun stop() {
        if (isPluginDestroyed()) {
            throw RuntimeException("plugin stop, but plugin has been already destroyed")
        }

        if (!isPluginStarted()) {
            throw RuntimeException("plugin stop, but plugin is never started")
        }

        status = PLUGIN_STOPPED

        val listener = pluginListener
            ?: throw RuntimeException("plugin stop, plugin listener is null")
        listener.onStop(this)
    }

    override fun destroy() {
        if (isPluginStarted()) {
            stop()
        }
        if (isPluginDestroyed()) {
            throw RuntimeException("plugin destroy, but plugin has been already destroyed")
        }
        status = PLUGIN_DESTROYED

        val listener = pluginListener
            ?: throw RuntimeException("plugin destroy, plugin listener is null")
        listener.onDestroy(this)
    }

    override val tag: String
        get() = javaClass.name

    override fun onForeground(isForeground: Boolean) {
    }

    open fun isForeground(): Boolean = ProcessUILifecycleOwner.isProcessForeground

    fun isPluginStarted(): Boolean = status == PLUGIN_STARTED

    fun isPluginStopped(): Boolean = status == PLUGIN_STOPPED

    fun isPluginDestroyed(): Boolean = status == PLUGIN_DESTROYED

    fun unSupportPlugin() {
        isSupported = false
    }

    open fun getJsonInfo(): JSONObject = JSONObject()

    companion object {
        private const val TAG = "TraceHarbor.Plugin"

        const val PLUGIN_CREATE = 0x00
        const val PLUGIN_INITED = 0x01
        const val PLUGIN_STARTED = 0x02
        const val PLUGIN_STOPPED = 0x04
        const val PLUGIN_DESTROYED = 0x08
    }
}
