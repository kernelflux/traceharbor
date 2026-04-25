package com.kernelflux.traceharbor.iocanary.detect

import android.annotation.SuppressLint
import com.kernelflux.traceharbor.report.IssuePublisher
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.lang.reflect.Proxy

class CloseGuardHooker(private val issueListener: IssuePublisher.OnIssueDetectListener?) {
    @Volatile
    private var mIsTryHook = false

    fun hook() {
        TraceHarborLog.i(TAG, "hook sIsTryHook=%b", mIsTryHook)
        if (!mIsTryHook) {
            val hookRet = tryHook()
            TraceHarborLog.i(TAG, "hook hookRet=%b", hookRet)
            mIsTryHook = true
        }
    }

    fun unHook() {
        val unHookRet = tryUnHook()
        TraceHarborLog.i(TAG, "unHook unHookRet=%b", unHookRet)
        mIsTryHook = false
    }

    private fun tryHook(): Boolean {
        try {
            val closeGuardCls = Class.forName("dalvik.system.CloseGuard")
            val closeGuardReporterCls = Class.forName("dalvik.system.CloseGuard\$Reporter")
            @SuppressLint("SoonBlockedPrivateApi")
            val methodGetReporter = closeGuardCls.getDeclaredMethod("getReporter")
            val methodSetReporter = closeGuardCls.getDeclaredMethod("setReporter", closeGuardReporterCls)
            val methodSetEnabled = closeGuardCls.getDeclaredMethod("setEnabled", java.lang.Boolean.TYPE)

            sOriginalReporter = methodGetReporter.invoke(null)

            methodSetEnabled.invoke(null, true)
            TraceHarborCloseGuard.setEnabled(true)

            val classLoader = closeGuardReporterCls.classLoader ?: return false

            methodSetReporter.invoke(
                null,
                Proxy.newProxyInstance(
                    classLoader,
                    arrayOf(closeGuardReporterCls),
                    IOCloseLeakDetector(issueListener, sOriginalReporter),
                ),
            )

            return true
        } catch (e: Throwable) {
            TraceHarborLog.e(TAG, "tryHook exp=%s", e)
        }

        return false
    }

    private fun tryUnHook(): Boolean {
        try {
            val closeGuardCls = Class.forName("dalvik.system.CloseGuard")
            val closeGuardReporterCls = Class.forName("dalvik.system.CloseGuard\$Reporter")
            val methodSetReporter = closeGuardCls.getDeclaredMethod("setReporter", closeGuardReporterCls)
            val methodSetEnabled = closeGuardCls.getDeclaredMethod("setEnabled", java.lang.Boolean.TYPE)

            methodSetReporter.invoke(null, sOriginalReporter)

            methodSetEnabled.invoke(null, false)
            TraceHarborCloseGuard.setEnabled(false)

            return true
        } catch (e: Throwable) {
            TraceHarborLog.e(TAG, "tryHook exp=%s", e)
        }

        return false
    }

    companion object {
        private const val TAG = "TraceHarbor.CloseGuardHooker"

        @Volatile
        private var sOriginalReporter: Any? = null
    }
}
