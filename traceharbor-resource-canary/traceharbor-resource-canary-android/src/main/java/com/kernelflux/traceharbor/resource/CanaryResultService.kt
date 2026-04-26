package com.kernelflux.traceharbor.resource

import android.content.Context
import android.content.Intent
import com.kernelflux.traceharbor.TraceHarbor
import com.kernelflux.traceharbor.plugin.Plugin
import com.kernelflux.traceharbor.report.Issue
import com.kernelflux.traceharbor.resource.config.SharePluginInfo
import com.kernelflux.traceharbor.util.TraceHarborLog
import org.json.JSONObject

class CanaryResultService : TraceHarborJobIntentService() {
    override fun onHandleWork(intent: Intent) {
        val action = intent.action
        if (ACTION_REPORT_HPROF_RESULT == action) {
            val resultPath = intent.getStringExtra(EXTRA_PARAM_RESULT_PATH)
            val activityName = intent.getStringExtra(EXTRA_PARAM_ACTIVITY)
            if (!resultPath.isNullOrEmpty() && !activityName.isNullOrEmpty()) {
                doReportHprofResult(resultPath, activityName)
            } else {
                TraceHarborLog.e(TAG, "resultPath or activityName is null or empty, skip reporting.")
            }
        }
    }

    // notice: compatible
    private fun doReportHprofResult(resultPath: String, activityName: String) {
        val issue = Issue(SharePluginInfo.IssueType.LEAK_FOUND)
        val resultJson = JSONObject()
        try {
            resultJson.put(SharePluginInfo.ISSUE_RESULT_PATH, resultPath)
            resultJson.put(SharePluginInfo.ISSUE_ACTIVITY_NAME, activityName)
            issue.content = resultJson
        } catch (thr: Throwable) {
            TraceHarborLog.printErrStackTrace(TAG, thr, "unexpected exception, skip reporting.")
        }

        val plugin: Plugin? = TraceHarbor.with().getPluginByClass(ResourcePlugin::class.java)
        plugin?.onDetectIssue(issue)
    }

    companion object {
        private const val TAG = "TraceHarbor.CanaryResultService"
        private const val JOB_ID = 0xFAFBFCFE.toInt()
        private const val ACTION_REPORT_HPROF_RESULT =
            "com.kernelflux.traceharbor.resource.result.action.REPORT_HPROF_RESULT"
        private const val EXTRA_PARAM_RESULT_PATH = "RESULT_PATH"
        private const val EXTRA_PARAM_ACTIVITY = "RESULT_ACTIVITY"

        @JvmStatic
        fun reportHprofResult(context: Context, resultPath: String, activityName: String) {
            val intent = Intent(context, CanaryResultService::class.java)
            intent.action = ACTION_REPORT_HPROF_RESULT
            intent.putExtra(EXTRA_PARAM_RESULT_PATH, resultPath)
            intent.putExtra(EXTRA_PARAM_ACTIVITY, activityName)
            enqueueWork(context, CanaryResultService::class.java, JOB_ID, intent)
        }
    }
}

