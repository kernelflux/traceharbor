package com.kernelflux.traceharbor.iocanary.detect

import com.kernelflux.traceharbor.iocanary.config.SharePluginInfo
import com.kernelflux.traceharbor.iocanary.util.IOCanaryUtil
import com.kernelflux.traceharbor.report.Issue
import com.kernelflux.traceharbor.report.IssuePublisher
import com.kernelflux.traceharbor.util.TraceHarborLog
import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method

class IOCloseLeakDetector(
    issueListener: OnIssueDetectListener?,
    private val originalReporter: Any?,
) : IssuePublisher(issueListener), InvocationHandler {

    override fun invoke(proxy: Any?, method: Method, args: Array<Any?>?): Any? {
        TraceHarborLog.i(TAG, "invoke method: %s", method.name)
        if (method.name == "report") {
            if (args == null || args.size != 2) {
                TraceHarborLog.e(TAG, "closeGuard report should has 2 params, current: %d", args?.size ?: 0)
                return null
            }
            if (args[1] !is Throwable) {
                TraceHarborLog.e(TAG, "closeGuard report args 1 should be throwable, current: %s", args[1])
                return null
            }
            val throwable = args[1] as Throwable

            val stackKey = IOCanaryUtil.getThrowableStack(throwable)
            if (isPublished(stackKey)) {
                TraceHarborLog.d(TAG, "close leak issue already published; key:%s", stackKey)
            } else {
                val ioIssue = Issue(SharePluginInfo.IssueType.ISSUE_IO_CLOSABLE_LEAK)
                ioIssue.key = stackKey
                val content = JSONObject()
                try {
                    content.put(SharePluginInfo.ISSUE_FILE_STACK, stackKey)
                } catch (e: JSONException) {
                    TraceHarborLog.e(TAG, "json content error: %s", e)
                }
                ioIssue.content = content
                publishIssue(ioIssue)
                TraceHarborLog.i(TAG, "close leak issue publish, key:%s", stackKey)
                markPublished(stackKey)
            }

            return null
        }
        return method.invoke(originalReporter, *(args ?: emptyArray()))
    }

    companion object {
        private const val TAG = "TraceHarbor.CloseGuardInvocationHandler"
    }
}
