package com.kernelflux.traceharbor.resource.processor

import android.os.Build
import com.kernelflux.traceharbor.TraceHarbor
import com.kernelflux.traceharbor.report.Issue
import com.kernelflux.traceharbor.resource.CanaryWorkerService
import com.kernelflux.traceharbor.resource.ResourcePlugin
import com.kernelflux.traceharbor.resource.analyzer.ActivityLeakAnalyzer
import com.kernelflux.traceharbor.resource.analyzer.model.ActivityLeakResult
import com.kernelflux.traceharbor.resource.analyzer.model.AndroidExcludedRefs
import com.kernelflux.traceharbor.resource.analyzer.model.DestroyedActivityInfo
import com.kernelflux.traceharbor.resource.analyzer.model.ExcludedRefs
import com.kernelflux.traceharbor.resource.analyzer.model.HeapDump
import com.kernelflux.traceharbor.resource.analyzer.model.HeapSnapshot
import com.kernelflux.traceharbor.resource.config.ResourceConfig
import com.kernelflux.traceharbor.resource.config.SharePluginInfo
import com.kernelflux.traceharbor.resource.dumper.AndroidHeapDumper
import com.kernelflux.traceharbor.resource.watcher.ActivityRefWatcher
import com.kernelflux.traceharbor.util.TraceHarborLog
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.jvm.JvmName

abstract class BaseLeakProcessor(
    @JvmField protected val mWatcher: ActivityRefWatcher,
) {
    private var mHeapDumper: AndroidHeapDumper? = null
    private var mHeapDumpHandler: AndroidHeapDumper.HeapDumpHandler? = null

    abstract fun process(destroyedActivityInfo: DestroyedActivityInfo): Boolean

    @Deprecated("Kept for Java compatibility")
    fun getHeapDumper(): AndroidHeapDumper {
        if (mHeapDumper == null) {
            mHeapDumper = AndroidHeapDumper(mWatcher.context)
        }
        return mHeapDumper!!
    }

    protected fun getHeapDumpHandler(): AndroidHeapDumper.HeapDumpHandler {
        if (mHeapDumpHandler == null) {
            mHeapDumpHandler =
                object : AndroidHeapDumper.HeapDumpHandler {
                    override fun process(result: HeapDump) {
                        CanaryWorkerService.shrinkHprofAndReport(mWatcher.context, result)
                    }
                }
        }
        return mHeapDumpHandler!!
    }

    fun getWatcher(): ActivityRefWatcher = mWatcher

    @get:JvmName("getWatcherCompat")
    protected val watcher: ActivityRefWatcher
        get() = mWatcher

    @get:JvmName("getHeapDumperCompat")
    protected val heapDumper: AndroidHeapDumper
        @Suppress("DEPRECATION")
        get() = getHeapDumper()

    @get:JvmName("getHeapDumpHandlerCompat")
    protected val heapDumpHandler: AndroidHeapDumper.HeapDumpHandler
        get() = getHeapDumpHandler()

    open fun onDestroy() {}

    protected fun analyze(hprofFile: File, referenceKey: String): ActivityLeakResult {
        setAnalyzing(true)
        var result: ActivityLeakResult
        val plugin = TraceHarbor.with().getPluginByClass(ResourcePlugin::class.java)
        val manufacture =
            plugin?.getConfig()?.getManufacture() ?: ""
        val excludedRefs: ExcludedRefs =
            AndroidExcludedRefs.createAppDefaults(Build.VERSION.SDK_INT, manufacture).build()
        try {
            val heapSnapshot = HeapSnapshot(hprofFile)
            result = ActivityLeakAnalyzer(referenceKey, excludedRefs).analyze(heapSnapshot)
        } catch (e: IOException) {
            result = ActivityLeakResult.failure(e, 0)
        }
        mWatcher.triggerGc()
        setAnalyzing(false)
        return result
    }

    protected fun publishIssue(
        issueType: Int,
        dumpMode: ResourceConfig.DumpMode,
        activity: String,
        refKey: String,
        detail: String,
        cost: String,
    ) {
        publishIssue(issueType, dumpMode, activity, refKey, detail, cost, 0)
    }

    protected fun publishIssue(
        issueType: Int,
        dumpMode: ResourceConfig.DumpMode,
        activity: String,
        refKey: String,
        detail: String,
        cost: String,
        retryCount: Int,
    ) {
        publishIssue(issueType, dumpMode, activity, refKey, detail, cost, retryCount, null)
    }

    protected fun publishIssue(
        issueType: Int,
        dumpMode: ResourceConfig.DumpMode,
        activity: String,
        refKey: String,
        detail: String,
        cost: String,
        retryCount: Int,
        hprofPath: String?,
    ) {
        val issue = Issue(issueType)
        val content = JSONObject()
        try {
            content.put(SharePluginInfo.ISSUE_DUMP_MODE, dumpMode.name)
            content.put(SharePluginInfo.ISSUE_ACTIVITY_NAME, activity)
            content.put(SharePluginInfo.ISSUE_REF_KEY, refKey)
            content.put(SharePluginInfo.ISSUE_LEAK_DETAIL, detail)
            content.put(SharePluginInfo.ISSUE_COST_MILLIS, cost)
            content.put(SharePluginInfo.ISSUE_RETRY_COUNT, retryCount)
            content.put(SharePluginInfo.ISSUE_HPROF_PATH, hprofPath)
        } catch (jsonException: JSONException) {
            TraceHarborLog.printErrStackTrace(TAG, jsonException, "")
        }
        issue.content = content
        mWatcher.getResourcePlugin().onDetectIssue(issue)
    }

    companion object {
        private const val TAG = "TraceHarbor.LeakProcessor.Base"

        @Volatile
        private var mAnalyzing: Boolean = false

        @JvmStatic
        fun isAnalyzing(): Boolean = mAnalyzing

        private fun setAnalyzing(analyzing: Boolean) {
            mAnalyzing = analyzing
        }
    }
}

