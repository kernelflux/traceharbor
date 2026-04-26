package com.kernelflux.traceharbor.batterycanary.monitor.feature

import android.app.Notification
import android.app.NotificationChannel
import android.content.res.Resources
import android.os.Build
import android.os.SystemClock
import android.text.TextUtils
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.annotation.VisibleForTesting
import com.kernelflux.traceharbor.batterycanary.utils.NotificationManagerServiceHooker
import com.kernelflux.traceharbor.batterycanary.utils.NotificationManagerServiceHooker.IListener
import com.kernelflux.traceharbor.util.TraceHarborLog

class NotificationMonitorFeature : AbsMonitorFeature() {
    interface NotificationListener {
        fun onNotify(@NonNull notify: BadNotification)
    }

    @Nullable
    @JvmField
    var mListener: IListener? = null

    @Nullable
    @JvmField
    var mAppRunningNotifyText: String? = null

    @JvmField
    var mLastBgStartMillis: Long = -1L

    override fun getTag(): String = TAG

    override fun onTurnOn() {
        super.onTurnOn()

        mAppRunningNotifyText = tryGetAppRunningNotificationText()

        if (TextUtils.isEmpty(mAppRunningNotifyText)) {
            TraceHarborLog.w(TAG, "can not get app_running_notification_text, abort notification monitor")
        } else {
            TraceHarborLog.i(TAG, "get app_running_notification_text: $mAppRunningNotifyText")
            mListener = object : IListener {
                override fun onCreateNotificationChannel(notificationChannel: Any?) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (notificationChannel is NotificationChannel) {
                            TraceHarborLog.i(
                                TAG,
                                "#onCreateNotificationChannel, id = " + notificationChannel.id +
                                    ", name = " + notificationChannel.name
                            )
                        }
                    }
                }

                override fun onCreateNotification(id: Int, notification: Notification?) {
                    if (notification != null) {
                        val title = notification.extras.getString(Notification.EXTRA_TITLE, null)
                        val text = notification.extras.getString(Notification.EXTRA_TEXT, null)
                        TraceHarborLog.i(TAG, "#onCreateNotification, id = $id, title = $title, text = $text")

                        if (TextUtils.isEmpty(text)) {
                            TraceHarborLog.w(TAG, "notify with empty text!")
                            checkNotifyContent(title, "")
                        } else if (text == mAppRunningNotifyText) {
                            TraceHarborLog.w(TAG, "notify with appRunningText: $text")
                            checkNotifyContent(title, text)
                        }
                    }
                }
            }

            NotificationManagerServiceHooker.addListener(mListener)
        }
    }

    private fun tryGetAppRunningNotificationText(): String? {
        val resources = Resources.getSystem()
        if (resources != null) {
            val appRunningNotifyTextId = resources.getIdentifier(
                "app_running_notification_text",
                "string",
                "android",
            )
            if (appRunningNotifyTextId > 0) {
                return resources.getString(appRunningNotifyTextId)
            }
        }
        return null
    }

    override fun onForeground(isForeground: Boolean) {
        super.onForeground(isForeground)
        mLastBgStartMillis = if (isForeground) -1 else SystemClock.uptimeMillis()
    }

    override fun onTurnOff() {
        super.onTurnOff()
        NotificationManagerServiceHooker.removeListener(mListener)
    }

    override fun weight(): Int = Int.MIN_VALUE

    @VisibleForTesting
    fun checkNotifyContent(@Nullable title: String?, @Nullable text: String?) {
        val bgMillis = if (mLastBgStartMillis > 0) SystemClock.uptimeMillis() - mLastBgStartMillis else 0L
        val stack = if (core.getConfig().isAggressiveMode) core.getConfig().callStackCollector.collectCurr() else ""

        core.getHandler().post {
            val badNotification = BadNotification()
            badNotification.title = title
            badNotification.content = text
            badNotification.stack = stack
            badNotification.bgMillis = bgMillis
            core.onNotify(badNotification)
        }
    }

    class BadNotification {
        @Nullable
        @JvmField
        var title: String? = null

        @Nullable
        @JvmField
        var content: String? = null

        @Nullable
        @JvmField
        var stack: String? = null

        @JvmField
        var bgMillis: Long = 0L

        override fun toString(): String {
            return "BadNotification{" +
                "title='$title'" +
                ", content='$content'" +
                ", stack='$stack'" +
                ", bgMillis=$bgMillis" +
                '}'
        }
    }

    private companion object {
        private const val TAG = "TraceHarbor.battery.NotificationMonitorFeature"
    }
}

