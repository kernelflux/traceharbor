package com.kernelflux.traceharbor.trace.tracer

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock.uptimeMillis
import android.util.Log
import com.kernelflux.traceharbor.TraceHarbor
import com.kernelflux.traceharbor.report.Issue
import com.kernelflux.traceharbor.trace.TracePlugin
import com.kernelflux.traceharbor.trace.config.SharePluginInfo
import com.kernelflux.traceharbor.trace.config.TraceConfig
import com.kernelflux.traceharbor.trace.constants.Constants
import com.kernelflux.traceharbor.trace.core.AppMethodBeat
import com.kernelflux.traceharbor.trace.hacker.ActivityThreadHacker
import com.kernelflux.traceharbor.trace.items.MethodItem
import com.kernelflux.traceharbor.trace.listeners.IAppMethodBeatListener
import com.kernelflux.traceharbor.trace.util.TraceDataUtils
import com.kernelflux.traceharbor.util.DeviceUtil
import com.kernelflux.traceharbor.util.TraceHarborHandlerThread
import com.kernelflux.traceharbor.util.TraceHarborLog
import org.json.JSONException
import org.json.JSONObject
import java.util.LinkedList

/**
 * Detects cold-start / warm-start latency by combining
 * [AppMethodBeat] focus events, [ActivityThreadHacker] application
 * create-time hooks, and [Application.ActivityLifecycleCallbacks].
 *
 * ```
 * firstMethod.i  LAUNCH_ACTIVITY  onWindowFocusChange  LAUNCH_ACTIVITY  onWindowFocusChange
 *      ^               ^                   ^                  ^                  ^
 *      |               |                   |                  |                  |
 *      |---app---|-----|---firstActivity---|---------…--------|---careActivity---|
 *      |<-applicationCost->|
 *      |<--------firstScreenCost--------->|
 *      |<------------------------------coldCost------------------------------>|
 *      .                  |<---warmCost--->|
 * ```
 */
class StartupTracer(private val config: TraceConfig) :
    Tracer(),
    IAppMethodBeatListener,
    ActivityThreadHacker.IApplicationCreateListener,
    Application.ActivityLifecycleCallbacks {

    private var firstScreenCost: Long = 0
    private var coldCost: Long = 0
    private var activeActivityCount: Int = 0
    private var isWarmStartUp: Boolean = false
    private var hasShowSplashActivity: Boolean = false
    private val isStartupEnable: Boolean = config.isStartupEnable()
    private val splashActivities: Set<String> = config.getSplashActivities()
    private val coldStartupThresholdMs: Long = config.getColdStartupThresholdMs().toLong()
    private val warmStartupThresholdMs: Long = config.getWarmStartupThresholdMs().toLong()
    private val isHasActivity: Boolean = config.isHasActivity()

    private var lastCreateActivity: Long = 0L
    private val createdTimeMap: HashMap<String, Long> = HashMap()
    private val isShouldRecordCreateTime: Boolean = true

    init {
        ActivityThreadHacker.addListener(this)
    }

    override fun onAlive() {
        super.onAlive()
        TraceHarborLog.i(TAG, "[onAlive] isStartupEnable:%s", isStartupEnable)
        if (isStartupEnable) {
            AppMethodBeat.getInstance().addListener(this)
            TraceHarbor.with().application.registerActivityLifecycleCallbacks(this)
        }
    }

    override fun onDead() {
        super.onDead()
        if (isStartupEnable) {
            AppMethodBeat.getInstance().removeListener(this)
            TraceHarbor.with().application.unregisterActivityLifecycleCallbacks(this)
        }
    }

    override fun onApplicationCreateEnd() {
        if (!isHasActivity) {
            val applicationCost = ActivityThreadHacker.getApplicationCost()
            TraceHarborLog.i(TAG, "onApplicationCreateEnd, applicationCost:%d", applicationCost)
            analyse(applicationCost, 0, applicationCost, false)
        }
    }

    override fun onActivityFocused(activity: Activity) {
        if (ActivityThreadHacker.sApplicationCreateScene == Int.MIN_VALUE) {
            Log.w(TAG, "start up from unknown scene")
            return
        }

        val activityName = activity.javaClass.name
        if (isColdStartup) {
            val isCreatedByLaunchActivity = ActivityThreadHacker.isCreatedByLaunchActivity()
            TraceHarborLog.i(
                TAG,
                "#ColdStartup# activity:%s, splashActivities:%s, empty:%b, " +
                    "isCreatedByLaunchActivity:%b, hasShowSplashActivity:%b, " +
                    "firstScreenCost:%d, now:%d, application_create_begin_time:%d, app_cost:%d",
                activityName,
                splashActivities,
                splashActivities.isEmpty(),
                isCreatedByLaunchActivity,
                hasShowSplashActivity,
                firstScreenCost,
                uptimeMillis(),
                ActivityThreadHacker.getEggBrokenTime(),
                ActivityThreadHacker.getApplicationCost(),
            )

            val key = "$activityName@${activity.hashCode()}"
            val createdTime = createdTimeMap[key] ?: 0L
            createdTimeMap[key] = uptimeMillis() - createdTime

            if (firstScreenCost == 0L) {
                firstScreenCost = uptimeMillis() - ActivityThreadHacker.getEggBrokenTime()
            }
            if (hasShowSplashActivity) {
                coldCost = uptimeMillis() - ActivityThreadHacker.getEggBrokenTime()
            } else {
                if (splashActivities.contains(activityName)) {
                    hasShowSplashActivity = true
                } else if (splashActivities.isEmpty()) {
                    if (isCreatedByLaunchActivity) {
                        coldCost = firstScreenCost
                    } else {
                        firstScreenCost = 0
                        coldCost = ActivityThreadHacker.getApplicationCost()
                    }
                } else {
                    if (isCreatedByLaunchActivity) {
                        coldCost = firstScreenCost
                    } else {
                        firstScreenCost = 0
                        coldCost = ActivityThreadHacker.getApplicationCost()
                    }
                }
            }
            if (coldCost > 0) {
                val betweenCost = createdTimeMap[key]
                if (betweenCost != null && betweenCost >= 30 * 1000) {
                    TraceHarborLog.e(
                        TAG,
                        "%s cost too much time[%s] between activity create and onActivityFocused, " +
                            "just throw it.(createTime:%s) ",
                        key,
                        uptimeMillis() - createdTime,
                        createdTime,
                    )
                    return
                }
                analyse(
                    ActivityThreadHacker.getApplicationCost(),
                    firstScreenCost,
                    coldCost,
                    false,
                )
            }
        } else if (isWarmStartUp) {
            isWarmStartUp = false
            val warmCost = uptimeMillis() - lastCreateActivity
            TraceHarborLog.i(
                TAG,
                "#WarmStartup# activity:%s, warmCost:%d, now:%d, lastCreateActivity:%d",
                activityName,
                warmCost,
                uptimeMillis(),
                lastCreateActivity,
            )

            if (warmCost > 0) {
                analyse(0, 0, warmCost, true)
            }
        }
    }

    private val isColdStartup: Boolean
        get() = coldCost == 0L

    private fun analyse(
        applicationCost: Long,
        firstScreenCost: Long,
        allCost: Long,
        isWarmStartUp: Boolean,
    ) {
        TraceHarborLog.i(
            TAG,
            "[report] applicationCost:%s firstScreenCost:%s allCost:%s isWarmStartUp:%s, createScene:%d",
            applicationCost,
            firstScreenCost,
            allCost,
            isWarmStartUp,
            ActivityThreadHacker.sApplicationCreateScene,
        )
        var data = LongArray(0)
        if (!isWarmStartUp && allCost >= coldStartupThresholdMs) {
            data = AppMethodBeat.getInstance()
                .copyData(ActivityThreadHacker.sApplicationCreateBeginMethodIndex)
            ActivityThreadHacker.sApplicationCreateBeginMethodIndex.release()
        } else if (isWarmStartUp && allCost >= warmStartupThresholdMs) {
            data = AppMethodBeat.getInstance()
                .copyData(ActivityThreadHacker.sLastLaunchActivityMethodIndex)
            ActivityThreadHacker.sLastLaunchActivityMethodIndex.release()
        }

        TraceHarborHandlerThread.getDefaultHandler().post(
            AnalyseTask(
                data,
                applicationCost,
                firstScreenCost,
                allCost,
                isWarmStartUp,
                ActivityThreadHacker.sApplicationCreateScene,
            ),
        )
    }

    private inner class AnalyseTask(
        val data: LongArray,
        val applicationCost: Long,
        val firstScreenCost: Long,
        val allCost: Long,
        val isWarmStartUp: Boolean,
        val scene: Int,
    ) : Runnable {

        override fun run() {
            val stack = LinkedList<MethodItem>()
            if (data.isNotEmpty()) {
                TraceDataUtils.structuredDataToStack(data, stack, false, -1)
                TraceDataUtils.trimStack(
                    stack,
                    Constants.TARGET_EVIL_METHOD_STACK,
                    object : TraceDataUtils.IStructuredDataFilter {
                        override fun isFilter(during: Long, filterCount: Int): Boolean =
                            during < filterCount.toLong() * Constants.TIME_UPDATE_CYCLE_MS

                        override fun getFilterMaxCount(): Int =
                            Constants.FILTER_STACK_MAX_COUNT

                        override fun fallback(stack: List<MethodItem>, size: Int) {
                            TraceHarborLog.w(
                                TAG,
                                "[fallback] size:%s targetSize:%s stack:%s",
                                size,
                                Constants.TARGET_EVIL_METHOD_STACK,
                                stack,
                            )
                            val it = (stack as MutableList<MethodItem>)
                                .listIterator(Math.min(size, Constants.TARGET_EVIL_METHOD_STACK))
                            while (it.hasNext()) {
                                it.next()
                                it.remove()
                            }
                        }
                    },
                )
            }

            val reportBuilder = StringBuilder()
            val logcatBuilder = StringBuilder()
            val stackCost = Math.max(
                allCost,
                TraceDataUtils.stackToString(stack, reportBuilder, logcatBuilder),
            )
            val stackKey = TraceDataUtils.getTreeKey(stack, stackCost)

            if ((allCost > coldStartupThresholdMs && !isWarmStartUp) ||
                (allCost > warmStartupThresholdMs && isWarmStartUp)
            ) {
                TraceHarborLog.w(TAG, "stackKey:%s \n%s", stackKey, logcatBuilder.toString())
            }

            report(
                applicationCost,
                firstScreenCost,
                reportBuilder,
                stackKey,
                stackCost,
                isWarmStartUp,
                scene,
            )
        }

        private fun report(
            applicationCost: Long,
            firstScreenCost: Long,
            reportBuilder: StringBuilder,
            stackKey: String,
            allCost: Long,
            isWarmStartUp: Boolean,
            scene: Int,
        ) {
            val plugin = TraceHarbor.with().getPluginByClass(TracePlugin::class.java) ?: return
            try {
                var costObject = JSONObject()
                costObject = DeviceUtil.getDeviceInfo(costObject, TraceHarbor.with().application)
                costObject.put(SharePluginInfo.STAGE_APPLICATION_CREATE, applicationCost)
                costObject.put(SharePluginInfo.STAGE_APPLICATION_CREATE_SCENE, scene)
                costObject.put(SharePluginInfo.STAGE_FIRST_ACTIVITY_CREATE, firstScreenCost)
                costObject.put(SharePluginInfo.STAGE_STARTUP_DURATION, allCost)
                costObject.put(SharePluginInfo.ISSUE_IS_WARM_START_UP, isWarmStartUp)
                val issue = Issue()
                issue.tag = SharePluginInfo.TAG_PLUGIN_STARTUP
                issue.content = costObject
                plugin.onDetectIssue(issue)
            } catch (e: JSONException) {
                TraceHarborLog.e(TAG, "[JSONException for StartUpReportTask error: %s", e)
            }

            if ((allCost > coldStartupThresholdMs && !isWarmStartUp) ||
                (allCost > warmStartupThresholdMs && isWarmStartUp)
            ) {
                try {
                    var jsonObject = JSONObject()
                    jsonObject = DeviceUtil.getDeviceInfo(jsonObject, TraceHarbor.with().application)
                    jsonObject.put(SharePluginInfo.ISSUE_STACK_TYPE, Constants.Type.STARTUP)
                    jsonObject.put(SharePluginInfo.ISSUE_COST, allCost)
                    jsonObject.put(SharePluginInfo.ISSUE_TRACE_STACK, reportBuilder.toString())
                    jsonObject.put(SharePluginInfo.ISSUE_STACK_KEY, stackKey)
                    jsonObject.put(SharePluginInfo.ISSUE_SUB_TYPE, if (isWarmStartUp) 2 else 1)
                    val issue = Issue()
                    issue.tag = SharePluginInfo.TAG_PLUGIN_EVIL_METHOD
                    issue.content = jsonObject
                    plugin.onDetectIssue(issue)
                } catch (e: JSONException) {
                    TraceHarborLog.e(TAG, "[JSONException error: %s", e)
                }
            }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        TraceHarborLog.i(TAG, "activeActivityCount:%d, coldCost:%d", activeActivityCount, coldCost)
        if (activeActivityCount == 0 && coldCost > 0) {
            lastCreateActivity = uptimeMillis()
            TraceHarborLog.i(
                TAG,
                "lastCreateActivity:%d, activity:%s",
                lastCreateActivity,
                activity.javaClass.name,
            )
            isWarmStartUp = true
        }
        activeActivityCount++
        if (isShouldRecordCreateTime) {
            createdTimeMap["${activity.javaClass.name}@${activity.hashCode()}"] = uptimeMillis()
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        TraceHarborLog.i(TAG, "activeActivityCount:%d", activeActivityCount)
        activeActivityCount--
    }

    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onForeground(isForeground: Boolean) {
        super.onForeground(isForeground)
        if (!isForeground) {
            checkActivityThread_mCallback()
        }
    }

    private companion object {
        private const val TAG = "TraceHarbor.StartupTracer"

        @JvmStatic
        @Suppress("FunctionName")
        private fun checkActivityThread_mCallback() {
            try {
                val forName = Class.forName("android.app.ActivityThread")
                val field = forName.getDeclaredField("sCurrentActivityThread")
                field.isAccessible = true
                val activityThreadValue = field.get(forName)
                val mH = forName.getDeclaredField("mH")
                mH.isAccessible = true
                val handler = mH.get(activityThreadValue)
                val handlerClass = handler.javaClass.superclass
                val callbackField = handlerClass.getDeclaredField("mCallback")
                callbackField.isAccessible = true
                val currentCallback = callbackField.get(handler) as? Handler.Callback
                TraceHarborLog.i(TAG, "callback %s", currentCallback)
            } catch (_: Exception) {
            }
        }
    }
}
