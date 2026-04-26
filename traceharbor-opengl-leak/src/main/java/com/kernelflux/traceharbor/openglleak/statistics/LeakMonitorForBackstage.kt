package com.kernelflux.traceharbor.openglleak.statistics

import android.app.Application
import android.os.Handler
import com.kernelflux.traceharbor.lifecycle.IStateObserver
import com.kernelflux.traceharbor.lifecycle.owners.ProcessExplicitBackgroundOwner
import com.kernelflux.traceharbor.openglleak.statistics.resource.OpenGLInfo
import com.kernelflux.traceharbor.openglleak.statistics.resource.ResRecordManager
import com.kernelflux.traceharbor.openglleak.utils.GlLeakHandlerThread
import java.util.LinkedList

class LeakMonitorForBackstage(
    private val mBackstageCheckTime: Long,
) : LeakMonitorDefault(), IStateObserver {
    private val mH: Handler = Handler(GlLeakHandlerThread.getInstance().looper)
    private var mLeakListener: LeakListener? = null
    private var mLeakListListener: LeakListListener? = null
    private val mLeaksList: MutableList<OpenGLInfo> = LinkedList()

    override fun onLeak(leak: OpenGLInfo) {
        synchronized(mLeaksList) {
            mLeaksList.add(leak)
        }
    }

    fun setLeakListener(l: LeakListener?) {
        mLeakListener = l
    }

    fun setLeakListListener(l: LeakListListener?) {
        mLeakListListener = l
    }

    override fun start(context: Application) {
        ProcessExplicitBackgroundOwner.observeForever(this)
        super.start(context)
    }

    override fun stop(context: Application) {
        ProcessExplicitBackgroundOwner.removeObserver(this)
        super.stop(context)
    }

    private val mRunnable: Runnable =
        Runnable {
            synchronized(mLeaksList) {
                val allInfos: MutableList<OpenGLInfo> = LinkedList()
                val it = mLeaksList.iterator()
                while (it.hasNext()) {
                    val item = it.next()
                    if (mLeakListener != null && !ResRecordManager.getInstance().isGLInfoRelease(item)) {
                        mLeakListener?.onLeak(item)
                        allInfos.add(item)
                    }
                    it.remove()
                }
                mLeakListListener?.onLeak(allInfos)
            }
        }

    override fun on() {
        mH.postDelayed(mRunnable, mBackstageCheckTime)
    }

    override fun off() {
        mH.removeCallbacks(mRunnable)
    }

    interface LeakListener {
        fun onLeak(info: OpenGLInfo)
    }

    interface LeakListListener {
        fun onLeak(infos: List<OpenGLInfo>)
    }
}

