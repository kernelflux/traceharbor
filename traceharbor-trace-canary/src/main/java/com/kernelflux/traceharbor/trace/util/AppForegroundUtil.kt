package com.kernelflux.traceharbor.trace.util

import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.util.ArrayMap
import java.util.HashMap

/**
 * Original Java was a single-value `enum AppForegroundUtil { INSTANCE; ... }`.
 *
 * Kotlin's `enum class` preserves both the public `INSTANCE` access path
 * (used by `SignalAnrTracer.AppForegroundUtil.INSTANCE.init()`) and the
 * Java byte-for-byte semantics — in particular the `static` helpers stay on
 * the synthetic companion under the same `AppForegroundUtil.<name>` access
 * pattern thanks to `@JvmStatic`.
 */
enum class AppForegroundUtil {
    INSTANCE;

    private var isAppForegroundInternal: Boolean = false
    private var visibleScene: String = "default"
    private val controller: Controller = Controller()
    private var isInit: Boolean = false
    private var currentFragmentNameInternal: String? = null
    private var handler: Handler? = null

    fun init() {
        if (isInit) {
            return
        }
        this.isInit = true
        // application.registerComponentCallbacks(controller);
        // application.registerActivityLifecycleCallbacks(controller);
    }

    fun getCurrentFragmentName(): String? = currentFragmentNameInternal

    fun setCurrentFragmentName(fragmentName: String?) {
        this.currentFragmentNameInternal = fragmentName
        updateScene(fragmentName)
    }

    fun getVisibleScene(): String = visibleScene

    private fun onDispatchForeground(visibleScene: String) {
        isAppForegroundInternal = true
        if (isAppForegroundInternal || !isInit) {
            return
        }
    }

    private fun onDispatchBackground(visibleScene: String) {
        isAppForegroundInternal = false
        if (!isAppForegroundInternal || !isInit) {
            return
        }
    }

    fun isAppForeground(): Boolean = isAppForegroundInternal

    private fun updateScene(activity: Activity) {
        visibleScene = activity.javaClass.name
    }

    private fun updateScene(currentFragmentName: String?) {
        val ss = StringBuilder()
        ss.append(if (TextUtils.isEmpty(currentFragmentName)) "?" else currentFragmentName)
        visibleScene = ss.toString()
    }

    private inner class Controller : Application.ActivityLifecycleCallbacks, ComponentCallbacks2 {

        override fun onActivityStarted(activity: Activity) {
            updateScene(activity)
            onDispatchForeground(getVisibleScene())
        }

        override fun onActivityStopped(activity: Activity) {
            if (getTopActivityName() == null) {
                onDispatchBackground(getVisibleScene())
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

        override fun onActivityDestroyed(activity: Activity) {}

        override fun onActivityResumed(activity: Activity) {}

        override fun onActivityPaused(activity: Activity) {}

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

        override fun onConfigurationChanged(newConfig: Configuration) {}

        override fun onLowMemory() {}

        override fun onTrimMemory(level: Int) {
            if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && isAppForegroundInternal) { // fallback
                onDispatchBackground(visibleScene)
            }
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.AppActiveDelegate"

        /**
         * Reflective inspection of `ActivityThread.mActivities` — replicates
         * the original Java logic verbatim. Returns `null` if no resumed
         * activity exists.
         */
        @JvmStatic
        fun getTopActivityName(): String? {
            val start = System.currentTimeMillis()
            try {
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val activityThread = activityThreadClass
                    .getMethod("currentActivityThread").invoke(null)
                val activitiesField = activityThreadClass.getDeclaredField("mActivities")
                activitiesField.isAccessible = true

                @Suppress("UNCHECKED_CAST")
                val activities: Map<Any, Any> =
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                        activitiesField.get(activityThread) as HashMap<Any, Any>
                    } else {
                        activitiesField.get(activityThread) as ArrayMap<Any, Any>
                    }

                if (activities.size < 1) {
                    return null
                }
                for (activityRecord in activities.values) {
                    val activityRecordClass = activityRecord.javaClass
                    val pausedField = activityRecordClass.getDeclaredField("paused")
                    pausedField.isAccessible = true
                    if (!pausedField.getBoolean(activityRecord)) {
                        val activityField = activityRecordClass.getDeclaredField("activity")
                        activityField.isAccessible = true
                        val activity = activityField.get(activityRecord) as Activity
                        return activity.javaClass.name
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                @Suppress("UNUSED_VARIABLE")
                val cost = System.currentTimeMillis() - start
            }
            return null
        }

        @JvmStatic
        fun isInterestingToUser(): Boolean = isActivityInterestingToUser()

        @JvmStatic
        private fun isServiceInterestingToUser(): Boolean {
            try {
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val activityThread = activityThreadClass
                    .getMethod("currentActivityThread").invoke(null)
                val servicesField = activityThreadClass.getDeclaredField("mServices")
                servicesField.isAccessible = true

                @Suppress("UNCHECKED_CAST")
                val services: Map<Any, Any> =
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                        servicesField.get(activityThread) as HashMap<Any, Any>
                    } else {
                        servicesField.get(activityThread) as ArrayMap<Any, Any>
                    }

                if (services.size < 1) {
                    return false
                }
                for (serviceObj in services.values) {
                    val serviceClass = serviceObj.javaClass
                    @Suppress("UNUSED_VARIABLE")
                    val service = serviceObj as Service
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return false
        }

        @JvmStatic
        private fun isActivityInterestingToUser(): Boolean {
            try {
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val activityThread = activityThreadClass
                    .getMethod("currentActivityThread").invoke(null)
                val activitiesField = activityThreadClass.getDeclaredField("mActivities")
                activitiesField.isAccessible = true

                @Suppress("UNCHECKED_CAST")
                val activities: Map<Any, Any> =
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                        activitiesField.get(activityThread) as HashMap<Any, Any>
                    } else {
                        activitiesField.get(activityThread) as ArrayMap<Any, Any>
                    }

                if (activities.size < 1) {
                    return false
                }
                for (activityRecord in activities.values) {
                    val activityRecordClass = activityRecord.javaClass
                    val pausedField = activityRecordClass.getDeclaredField("paused")
                    pausedField.isAccessible = true
                    if (!pausedField.getBoolean(activityRecord)) {
                        return true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return false
        }
    }
}
