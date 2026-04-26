package com.kernelflux.traceharbor.openglleak.statistics

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.kernelflux.traceharbor.openglleak.statistics.resource.OpenGLInfo
import com.kernelflux.traceharbor.openglleak.utils.ActivityRecorder
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.util.ArrayList
import java.util.LinkedList

abstract class LeakMonitorDefault : Application.ActivityLifecycleCallbacks {
    private val mActivityLeakMonitor: MutableList<ActivityLeakMonitor> = LinkedList()

    open fun start(context: Application) {
        context.registerActivityLifecycleCallbacks(this)
        TraceHarborLog.i(TAG, "start")

        val currentActivity = ActivityRecorder.getActivity()
        if (currentActivity != null) {
            val activityLeakMonitor = ActivityLeakMonitor(currentActivity.hashCode(), CustomizeLeakMonitor())
            activityLeakMonitor.start()
            synchronized(mActivityLeakMonitor) {
                mActivityLeakMonitor.add(activityLeakMonitor)
            }
        }
    }

    open fun stop(context: Application) {
        context.unregisterActivityLifecycleCallbacks(this)
        TraceHarborLog.i(TAG, "stop")
    }

    abstract fun onLeak(leak: OpenGLInfo)

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        val activityLeakMonitor = ActivityLeakMonitor(activity.hashCode(), CustomizeLeakMonitor())
        activityLeakMonitor.start()
        TraceHarborLog.i(TAG, "onActivityCreated $activityLeakMonitor")
        synchronized(mActivityLeakMonitor) {
            mActivityLeakMonitor.add(activityLeakMonitor)
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        val activityHashCode = activity.hashCode()
        TraceHarborLog.i(TAG, "onActivityDestroyed $activityHashCode")
        synchronized(mActivityLeakMonitor) {
            val it = mActivityLeakMonitor.iterator()
            while (it.hasNext()) {
                val activityLeakMonitor = it.next()
                if (activityLeakMonitor.getActivityHashCode() == activityHashCode) {
                    it.remove()
                    val leaks = activityLeakMonitor.end()
                    for (leakItem in leaks) {
                        if (leakItem != null) {
                            val activityInfo = leakItem.getActivityInfo()
                            if (activityInfo != null && activityInfo.activityHashcode == activityLeakMonitor.mActivityHashCode) {
                                onLeak(leakItem)
                            }
                        }
                    }
                    break
                }
            }
        }
    }

    override fun onActivityStarted(activity: Activity) {
        TraceHarborLog.i(TAG, "onActivityStarted ${activity.hashCode()}")
    }

    override fun onActivityResumed(activity: Activity) {
        TraceHarborLog.i(TAG, "onActivityResumed ${activity.hashCode()}")
    }

    override fun onActivityPaused(activity: Activity) {
        TraceHarborLog.i(TAG, "onActivityPaused ${activity.hashCode()}")
    }

    override fun onActivityStopped(activity: Activity) {
        TraceHarborLog.i(TAG, "onActivityStopped ${activity.hashCode()}")
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {
    }

    inner class ActivityLeakMonitor(
        val mActivityHashCode: Int,
        private val mMonitor: CustomizeLeakMonitor?,
    ) {
        fun start() {
            mMonitor?.checkStart()
        }

        fun end(): List<OpenGLInfo?> {
            if (mMonitor == null) {
                return ArrayList()
            }
            return mMonitor.checkEnd()
        }

        fun getActivityHashCode(): Int = mActivityHashCode

        override fun toString(): String {
            return "ActivityLeakMonitor{" +
                "mActivityHashCode=$mActivityHashCode" +
                ", mMonitor=$mMonitor" +
                '}'
        }
    }

    companion object {
        private const val TAG = "traceharbor.LeakMonitorDefault"
    }
}

