package com.kernelflux.traceharbor.resource.processor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Pair
import com.kernelflux.traceharbor.resource.MemoryUtil
import com.kernelflux.traceharbor.resource.R
import com.kernelflux.traceharbor.resource.analyzer.model.ActivityLeakResult
import com.kernelflux.traceharbor.resource.analyzer.model.DestroyedActivityInfo
import com.kernelflux.traceharbor.resource.config.ResourceConfig
import com.kernelflux.traceharbor.resource.config.SharePluginInfo
import com.kernelflux.traceharbor.resource.dumper.HprofFileManager
import com.kernelflux.traceharbor.resource.watcher.ActivityRefWatcher
import com.kernelflux.traceharbor.util.TraceHarborLog
import com.kernelflux.traceharbor.util.TraceHarborUtil
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale
import java.util.concurrent.TimeUnit

class ManualDumpProcessor(
    watcher: ActivityRefWatcher,
    private val mTargetActivity: String?,
) : BaseLeakProcessor(watcher) {
    private val mNotificationManager: NotificationManager =
        watcher.context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var isMuted = false

    override fun process(destroyedActivityInfo: DestroyedActivityInfo): Boolean {
        watcher.triggerGc()
        if (destroyedActivityInfo.mActivityRef.get() == null) {
            TraceHarborLog.v(TAG, "activity with key [%s] was already recycled.", destroyedActivityInfo.mKey)
            return true
        }

        TraceHarborLog.i(TAG, "show notification for activity leak. %s", destroyedActivityInfo.mActivityName)
        if (isMuted) {
            TraceHarborLog.i(TAG, "is muted, won't show notification util process reboot")
            return true
        }

        val data = dumpAndAnalyse(destroyedActivityInfo.mActivityName, destroyedActivityInfo.mKey)
        if (data != null) {
            if (!isMuted) {
                TraceHarborLog.i(TAG, "shown notification!!!3")
                sendResultNotification(destroyedActivityInfo, data.first, data.second)
            } else {
                TraceHarborLog.i(TAG, "mute mode, notification will not be shown.")
            }
        }
        return true
    }

    private fun sendResultNotification(activityInfo: DestroyedActivityInfo, hprofPath: String, refChain: String?) {
        if (!watcher.getResourcePlugin().getConfig().isManualDumpNotificationEnabled()) {
            TraceHarborLog.i(TAG, "Manual dump notification is disabled")
            return
        }

        val context = watcher.context
        val targetActivity = mTargetActivity ?: return
        val targetIntent = Intent()
        targetIntent.setClassName(watcher.context, targetActivity)
        targetIntent.putExtra(SharePluginInfo.ISSUE_ACTIVITY_NAME, activityInfo.mActivityName)
        targetIntent.putExtra(SharePluginInfo.ISSUE_REF_KEY, activityInfo.mKey)
        targetIntent.putExtra(SharePluginInfo.ISSUE_LEAK_PROCESS, TraceHarborUtil.getProcessName(context))
        targetIntent.putExtra(SharePluginInfo.ISSUE_HPROF_PATH, hprofPath)
        targetIntent.putExtra(SharePluginInfo.ISSUE_LEAK_DETAIL, refChain)

        val flags =
            if (Build.VERSION.SDK_INT >= 31) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        val pIntent = PendingIntent.getActivity(context, 0, targetIntent, flags)

        val dumpingHeapTitle = context.getString(R.string.resource_canary_leak_tip)
        val config: ResourceConfig = watcher.getResourcePlugin().getConfig()
        val dumpingHeapContent =
            String.format(
                Locale.getDefault(),
                "[%s] has leaked for [%s]min!!!",
                activityInfo.mActivityName,
                TimeUnit.MILLISECONDS.toMinutes(config.getScanIntervalMillis() * config.getMaxRedetectTimes().toLong()),
            )

        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, getNotificationChannelIdCompat(context))
            } else {
                Notification.Builder(context)
            }

        builder
            .setContentTitle(dumpingHeapTitle)
            .setPriority(Notification.PRIORITY_DEFAULT)
            .setStyle(Notification.BigTextStyle().bigText(dumpingHeapContent))
            .setAutoCancel(true)
            .setContentIntent(pIntent)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setWhen(System.currentTimeMillis())

        val notification = builder.build()
        mNotificationManager.notify(NOTIFICATION_ID + activityInfo.mKey.hashCode(), notification)
    }

    private fun getNotificationChannelIdCompat(context: Context): String {
        val channelName = "com.kernelflux.traceharbor.resource.processor.ManualDumpProcessor"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            var notificationChannel = notificationManager.getNotificationChannel(channelName)
            if (notificationChannel == null) {
                TraceHarborLog.v(TAG, "create channel")
                notificationChannel =
                    NotificationChannel(channelName, channelName, NotificationManager.IMPORTANCE_HIGH)
                notificationManager.createNotificationChannel(notificationChannel)
            }
        }
        return channelName
    }

    fun setMuted(mute: Boolean) {
        isMuted = mute
    }

    private fun dumpAndAnalyse(activity: String, key: String): Pair<String, String?>? {
        watcher.triggerGc()
        var file: File? = null
        try {
            file = HprofFileManager.prepareHprofFile("MDP", false)
        } catch (e: FileNotFoundException) {
            TraceHarborLog.printErrStackTrace(TAG, e, "")
        }

        if (file == null) {
            TraceHarborLog.e(TAG, "prepare hprof file failed, see log above")
            return null
        }

        val result: ActivityLeakResult = MemoryUtil.dumpAndAnalyze(file.absolutePath, key, 600)
        return if (result.mLeakFound) {
            val leakChain = result.toString()
            publishIssue(
                SharePluginInfo.IssueType.LEAK_FOUND,
                ResourceConfig.DumpMode.MANUAL_DUMP,
                activity,
                key,
                leakChain,
                result.mAnalysisDurationMs.toString(),
                0,
                file.absolutePath,
            )
            Pair(file.absolutePath, leakChain)
        } else if (result.mFailure != null) {
            publishIssue(
                SharePluginInfo.IssueType.ERR_EXCEPTION,
                ResourceConfig.DumpMode.MANUAL_DUMP,
                activity,
                key,
                result.mFailure.toString(),
                "0",
            )
            null
        } else {
            Pair(file.absolutePath, null)
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.LeakProcessor.ManualDump"
        private const val NOTIFICATION_ID = 0x110
    }
}

