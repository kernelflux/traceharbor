package com.kernelflux.traceharbor.openglleak.statistics

import android.app.Application
import android.os.Handler
import com.kernelflux.traceharbor.openglleak.statistics.resource.OpenGLInfo
import com.kernelflux.traceharbor.openglleak.statistics.resource.ResRecordManager
import com.kernelflux.traceharbor.openglleak.utils.GlLeakHandlerThread
import java.util.LinkedList

class LeakMonitorForDoubleCheck(
    private val mDoubleCheckTime: Long,
) : LeakMonitorDefault() {
    private val mH: Handler = Handler(GlLeakHandlerThread.getInstance().looper)
    private val mMaybeLeakList: MutableList<MaybeLeakOpenGLInfo> = LinkedList()
    private var mLeakListener: LeakListener? = null
    private var mLastDoubleCheckTime: Long = 0

    private val mDoubleCheck: Runnable =
        Runnable {
            doubleCheck()
        }

    override fun start(context: Application) {
        super.start(context)
    }

    override fun stop(context: Application) {
        mH.removeCallbacks(mDoubleCheck)
        super.stop(context)
    }

    override fun onLeak(leak: OpenGLInfo) {
        val now = System.currentTimeMillis()
        val leakItem = MaybeLeakOpenGLInfo(leak)
        leakItem.setMaybeLeakTime(now)

        mLeakListener?.onMaybeLeak(leakItem)
        synchronized(mMaybeLeakList) {
            // 可能泄漏，需要做 double check
            mMaybeLeakList.add(leakItem)
            if ((now - mLastDoubleCheckTime) > DOUBLE_CHECK_LOOPER) {
                mH.removeCallbacks(mDoubleCheck)
                mH.post(mDoubleCheck)
            }
        }
    }

    fun setLeakListener(l: LeakListener?) {
        mLeakListener = l
    }

    private fun doubleCheck() {
        val now = System.currentTimeMillis()
        mLastDoubleCheckTime = now
        synchronized(mMaybeLeakList) {
            val it = mMaybeLeakList.iterator()
            while (it.hasNext()) {
                val item = it.next()
                if ((now - item.getMaybeLeakTime()) > mDoubleCheckTime) {
                    it.remove()
                    if (mLeakListener != null && !ResRecordManager.getInstance().isGLInfoRelease(item)) {
                        mLeakListener?.onLeak(item)
                    }
                }
            }

            if (mMaybeLeakList.size > 0) {
                mH.removeCallbacks(mDoubleCheck)
                mH.postDelayed(mDoubleCheck, DOUBLE_CHECK_LOOPER)
            }
        }
    }

    inner class MaybeLeakOpenGLInfo(
        clone: OpenGLInfo,
    ) : OpenGLInfo(clone) {
        private var mMaybeLeakTime: Long = 0

        fun setMaybeLeakTime(t: Long) {
            mMaybeLeakTime = t
        }

        fun getMaybeLeakTime(): Long = mMaybeLeakTime
    }

    interface LeakListener {
        fun onMaybeLeak(info: OpenGLInfo)

        fun onLeak(info: OpenGLInfo)
    }

    companion object {
        private const val TAG = "traceharbor.LeakMonitorForActivityLifecycle"
        private const val DOUBLE_CHECK_LOOPER: Long = 1000L * 30
    }
}

