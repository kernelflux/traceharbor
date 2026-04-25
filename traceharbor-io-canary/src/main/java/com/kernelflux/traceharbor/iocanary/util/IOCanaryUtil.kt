package com.kernelflux.traceharbor.iocanary.util

import android.content.Context
import com.kernelflux.traceharbor.iocanary.config.SharePluginInfo
import com.kernelflux.traceharbor.iocanary.core.IOIssue
import com.kernelflux.traceharbor.report.Issue
import org.json.JSONException
import org.json.JSONObject
import java.util.LinkedList

object IOCanaryUtil {
    private const val DEFAULT_MAX_STACK_LAYER = 10

    private var sPackageName: String? = null

    @JvmStatic
    fun setPackageName(context: Context) {
        if (sPackageName == null) {
            sPackageName = context.packageName
        }
    }

    @JvmStatic
    fun stackTraceToString(arr: Array<StackTraceElement>?): String {
        if (arr == null) {
            return ""
        }

        val stacks = LinkedList<StackTraceElement>()
        for (stack in arr) {
            val className = stack.className
            if (className.contains("libcore.io") ||
                className.contains("com.kernelflux.traceharbor.iocanary") ||
                className.contains("java.io") ||
                className.contains("dalvik.system") ||
                className.contains("android.os")
            ) {
                continue
            }
            stacks.add(stack)
        }

        val packageName = sPackageName
        if (stacks.size > DEFAULT_MAX_STACK_LAYER && packageName != null) {
            val iterator = stacks.listIterator(stacks.size)
            while (iterator.hasPrevious()) {
                val stack = iterator.previous()
                if (!stack.className.contains(packageName)) {
                    iterator.remove()
                }
                if (stacks.size <= DEFAULT_MAX_STACK_LAYER) {
                    break
                }
            }
        }

        val sb = StringBuffer(stacks.size)
        for (stackTraceElement in stacks) {
            sb.append(stackTraceElement).append('\n')
        }
        return sb.toString()
    }

    @JvmStatic
    fun getThrowableStack(throwable: Throwable?): String {
        if (throwable == null) {
            return ""
        }
        return stackTraceToString(throwable.stackTrace)
    }

    @JvmStatic
    fun convertIOIssueToReportIssue(ioIssue: IOIssue?): Issue? {
        if (ioIssue == null) {
            return null
        }

        val issue = Issue(ioIssue.type)
        val content = JSONObject()

        try {
            content.put(SharePluginInfo.ISSUE_FILE_PATH, ioIssue.path)
            content.put(SharePluginInfo.ISSUE_FILE_SIZE, ioIssue.fileSize)
            content.put(SharePluginInfo.ISSUE_FILE_OP_TIMES, ioIssue.opCnt)
            content.put(SharePluginInfo.ISSUE_FILE_BUFFER, ioIssue.bufferSize)
            content.put(SharePluginInfo.ISSUE_FILE_COST_TIME, ioIssue.opCostTime)
            content.put(SharePluginInfo.ISSUE_FILE_READ_WRITE_TYPE, ioIssue.opType)
            content.put(SharePluginInfo.ISSUE_FILE_OP_SIZE, ioIssue.opSize)
            content.put(SharePluginInfo.ISSUE_FILE_THREAD, ioIssue.threadName)
            content.put(SharePluginInfo.ISSUE_FILE_STACK, ioIssue.stack)
            content.put(SharePluginInfo.ISSUE_FILE_REPEAT_COUNT, ioIssue.repeatReadCnt)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        issue.content = content
        return issue
    }
}
