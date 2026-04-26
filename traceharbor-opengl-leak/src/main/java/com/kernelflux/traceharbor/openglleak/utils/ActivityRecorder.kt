package com.kernelflux.traceharbor.openglleak.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.kernelflux.traceharbor.openglleak.hook.OpenGLHook
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.lang.reflect.Field
import java.util.Objects

class ActivityRecorder private constructor() : Application.ActivityLifecycleCallbacks {
    private var currentActivityInfo: ActivityInfo? = null

    fun start(context: Application) {
        val activity = getActivity()
        if (activity != null) {
            currentActivityInfo = ActivityInfo(activity.hashCode(), activity.localClassName)
            OpenGLHook.getInstance().updateCurrActivity(currentActivityInfo.toString())
        }
        context.registerActivityLifecycleCallbacks(this)
    }

    fun stop(context: Application) {
        context.unregisterActivityLifecycleCallbacks(this)
    }

    fun getCurrentActivityInfo(): ActivityInfo? = currentActivityInfo

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        currentActivityInfo = ActivityInfo(activity.hashCode(), activity.localClassName)
        OpenGLHook.getInstance().updateCurrActivity(currentActivityInfo.toString())
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivityInfo = ActivityInfo(activity.hashCode(), activity.localClassName)
        OpenGLHook.getInstance().updateCurrActivity(currentActivityInfo.toString())
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    class ActivityInfo internal constructor(
        @JvmField var activityHashcode: Int,
        @JvmField var name: String,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false
            other as ActivityInfo
            return activityHashcode == other.activityHashcode && Objects.equals(name, other.name)
        }

        override fun hashCode(): Int {
            return Objects.hash(activityHashcode, name)
        }

        override fun toString(): String {
            return "$activityHashcode : $name"
        }
    }

    companion object {
        private const val TAG = "traceharbor.ActivityRecorder"

        @JvmField
        val mInstance: ActivityRecorder = ActivityRecorder()

        @JvmStatic
        fun getInstance(): ActivityRecorder = mInstance

        @JvmStatic
        fun getActivity(): Activity? {
            return try {
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null)
                val activitiesField: Field = activityThreadClass.getDeclaredField("mActivities")
                activitiesField.isAccessible = true
                val activities = activitiesField.get(activityThread) as? Map<*, *> ?: return null
                for (activityRecord in activities.values) {
                    val activityRecordClass = activityRecord?.javaClass ?: continue
                    val pausedField = activityRecordClass.getDeclaredField("paused")
                    pausedField.isAccessible = true
                    if (!pausedField.getBoolean(activityRecord)) {
                        val activityField = activityRecordClass.getDeclaredField("activity")
                        activityField.isAccessible = true
                        return activityField.get(activityRecord) as? Activity
                    }
                }
                null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        @JvmStatic
        fun revertActivityInfo(infoStr: String?): ActivityInfo {
            if (infoStr.isNullOrEmpty()) {
                return ActivityInfo(-1, "null")
            }
            return try {
                val result = infoStr.split(" : ")
                val hash = result[0].toInt()
                val name = result[1]
                ActivityInfo(hash, name)
            } catch (t: Throwable) {
                TraceHarborLog.printErrStackTrace(TAG, t, "")
                ActivityInfo(-1, "")
            }
        }
    }
}

