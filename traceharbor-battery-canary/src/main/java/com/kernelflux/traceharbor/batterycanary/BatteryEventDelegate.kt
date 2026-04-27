package com.kernelflux.traceharbor.batterycanary

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import androidx.annotation.StringDef
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import com.kernelflux.traceharbor.batterycanary.monitor.AppStats
import com.kernelflux.traceharbor.batterycanary.monitor.BatteryMonitorCore
import com.kernelflux.traceharbor.batterycanary.stats.BatteryStatsFeature
import com.kernelflux.traceharbor.batterycanary.utils.BatteryCanaryUtil
import com.kernelflux.traceharbor.batterycanary.utils.BatteryCanaryUtil.ONE_MIN
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.util.LinkedList
import kotlin.math.abs

/**
 * @author Kaede
 * @since 2021/1/11
 */
class BatteryEventDelegate private constructor(context: Context?) {
    @JvmField
    val mContext: Context

    @JvmField
    val mListenerList: MutableList<Listener> = LinkedList()

    @JvmField
    val mUiHandler: Handler = Handler(Looper.getMainLooper())

    @JvmField
    val mAppLowEnergyTask: BackgroundTask = BackgroundTask()

    @JvmField
    var sIsForeground: Boolean = true

    @JvmField
    var mLastBatteryPowerPct: Long

    @JvmField
    var mLastBatteryTemp: Long

    @JvmField
    var mCore: BatteryMonitorCore? = null

    init {
        checkNotNull(context) { "Context should not be null" }
        mContext = context
        mLastBatteryPowerPct = BatteryCanaryUtil.getBatteryPercentageImmediately(context).toLong()
        mLastBatteryTemp = BatteryCanaryUtil.getBatteryTemperature(context).toLong()
    }

    fun attach(core: BatteryMonitorCore?): BatteryEventDelegate {
        if (core != null) {
            mCore = core
        }
        return this
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    fun onForeground(isForeground: Boolean) {
        sIsForeground = isForeground
        if (isForeground) {
            sBackgroundBgnMillis = 0L
            mUiHandler.removeCallbacks(mAppLowEnergyTask)
        } else {
            sBackgroundBgnMillis = SystemClock.uptimeMillis()
            mUiHandler.postDelayed(mAppLowEnergyTask, mAppLowEnergyTask.reset())
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    fun startListening() {
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_BATTERY_CHANGED)

        filter.addAction(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        filter.addAction(Intent.ACTION_POWER_CONNECTED)
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED)

        filter.addAction(Intent.ACTION_BATTERY_OKAY)
        filter.addAction(Intent.ACTION_BATTERY_LOW)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                filter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            }
        }

        mContext.registerReceiver(object : BroadcastReceiver() {
            private var mLastBatteryChangedHandleMs = -1L

            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                if (action != null) {
                    if (action == Intent.ACTION_BATTERY_CHANGED) {
                        // ACTION_BATTERY_CHANGED is frequent, so rate-limit and handle it on the monitor worker.
                        val core = mCore
                        if (core != null) {
                            var limited = false
                            val currMs = System.currentTimeMillis()
                            if (mLastBatteryChangedHandleMs > 0 && currMs - mLastBatteryChangedHandleMs < ONE_MIN) {
                                limited = true
                            }
                            if (!limited) {
                                mLastBatteryChangedHandleMs = currMs
                                core.getHandler().post {
                                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                                    if (status == BatteryManager.BATTERY_STATUS_FULL) {
                                        onBatteryFullCharged()
                                        return@post
                                    }

                                    val currPct = BatteryCanaryUtil.getBatteryPercentage(mContext)
                                    if (currPct in 0..1000) {
                                        if (abs(currPct - mLastBatteryPowerPct) >= BATTERY_POWER_GRADUATION) {
                                            mLastBatteryPowerPct = currPct.toLong()
                                            val feat =
                                                mCore?.getMonitorFeature(BatteryStatsFeature::class.java)
                                            feat?.statsBatteryEvent(currPct)
                                            onBatteryPowerChanged(currPct)
                                        }
                                    }

                                    try {
                                        val currTemp =
                                            intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                                        if (currTemp >= 0 && currPct <= 1000) {
                                            if (abs(currTemp - mLastBatteryTemp) >= BATTERY_TEMPERATURE_GRADUATION) {
                                                mLastBatteryTemp = currTemp.toLong()
                                                val feat =
                                                    mCore?.getMonitorFeature(BatteryStatsFeature::class.java)
                                                feat?.statsBatteryTempEvent(currTemp)
                                                onBatteryTemperatureChanged(currTemp)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        TraceHarborLog.w(
                                            TAG,
                                            "get EXTRA_TEMPERATURE failed: " + e.message
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        var devStat = -1
                        var notifyStateChanged = false
                        var notifyBatteryLowChanged = false
                        when (action) {
                            Intent.ACTION_SCREEN_ON -> {
                                devStat = AppStats.DEV_STAT_SCREEN_ON
                                notifyStateChanged = true
                            }

                            Intent.ACTION_SCREEN_OFF -> {
                                devStat = AppStats.DEV_STAT_SCREEN_OFF
                                notifyStateChanged = true
                            }

                            Intent.ACTION_POWER_CONNECTED -> {
                                devStat = AppStats.DEV_STAT_CHARGING
                                notifyStateChanged = true
                            }

                            Intent.ACTION_POWER_DISCONNECTED -> {
                                devStat = AppStats.DEV_STAT_UN_CHARGING
                                notifyStateChanged = true
                            }

                            PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> {
                                devStat = if (BatteryCanaryUtil.isDeviceOnIdleMode(context)) {
                                    AppStats.DEV_STAT_DOZE_MODE_ON
                                } else {
                                    AppStats.DEV_STAT_DOZE_MODE_OFF
                                }
                                notifyStateChanged = true
                            }

                            PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                                devStat = if (BatteryCanaryUtil.isDeviceOnPowerSave(context)) {
                                    AppStats.DEV_STAT_SAVE_POWER_MODE_ON
                                } else {
                                    AppStats.DEV_STAT_SAVE_POWER_MODE_OFF
                                }
                                notifyStateChanged = true
                            }

                            Intent.ACTION_BATTERY_OKAY,
                            Intent.ACTION_BATTERY_LOW -> {
                                notifyStateChanged = true
                                notifyBatteryLowChanged = true
                            }
                        }

                        if (devStat != -1) {
                            val feat = mCore?.getMonitorFeature(BatteryStatsFeature::class.java)
                            feat?.statsDevStat(devStat)
                        }

                        if (notifyStateChanged) {
                            onSateChangedEvent(intent)
                        }
                        if (notifyBatteryLowChanged) {
                            val feat = mCore?.getMonitorFeature(BatteryStatsFeature::class.java)
                            feat?.statsBatteryEvent(action == Intent.ACTION_BATTERY_LOW)
                            onBatteryChangeEvent(intent)
                        }
                    }
                }
            }
        }, filter)
    }

    fun currentState(): BatteryState {
        return BatteryState(mContext).attach(mCore)
    }

    fun addListener(listener: Listener) {
        synchronized(mListenerList) {
            if (!mListenerList.contains(listener)) {
                mListenerList.add(listener)
            }
        }
    }

    fun removeListener(listener: Listener) {
        synchronized(mListenerList) {
            val iterator = mListenerList.listIterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                if (item === listener) {
                    iterator.remove()
                }
            }
        }
    }

    private fun onSateChangedEvent(intent: Intent) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dispatchSateChangedEvent(intent)
        } else {
            mUiHandler.post { dispatchSateChangedEvent(intent) }
        }
    }

    private fun onAppLowEnergyEvent(duringMillis: Long) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dispatchAppLowEnergyEvent(duringMillis)
        } else {
            mUiHandler.post { dispatchAppLowEnergyEvent(duringMillis) }
        }
    }

    private fun onBatteryChangeEvent(intent: Intent) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dispatchBatteryStateChangedEvent(intent)
        } else {
            mUiHandler.post { dispatchBatteryStateChangedEvent(intent) }
        }
    }

    private fun onBatteryPowerChanged(pct: Int) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dispatchBatteryPowerChanged(pct)
        } else {
            mUiHandler.post { dispatchBatteryPowerChanged(pct) }
        }
    }

    private fun onBatteryFullCharged() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dispatchBatteryFullCharged()
        } else {
            mUiHandler.post { dispatchBatteryFullCharged() }
        }
    }

    private fun onBatteryTemperatureChanged(temp: Int) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dispatchBatteryTemperatureChanged(temp)
        } else {
            mUiHandler.post { dispatchBatteryTemperatureChanged(temp) }
        }
    }

    @VisibleForTesting
    fun dispatchSateChangedEvent(intent: Intent) {
        TraceHarborLog.i(TAG, "onSateChanged >> " + intent.action)
        synchronized(mListenerList) {
            val batteryState = currentState()
            for (item in mListenerList) {
                if (item is Listener.ExListener) {
                    if (item.onStateChanged(batteryState, intent.action)) {
                        removeListener(item)
                    }
                } else {
                    if (item.onStateChanged(intent.action)) {
                        removeListener(item)
                    }
                }
            }
        }
    }

    @VisibleForTesting
    fun dispatchAppLowEnergyEvent(duringMillis: Long) {
        TraceHarborLog.i(TAG, "onAppLowEnergy >> " + duringMillis / ONE_MIN + "min")
        synchronized(mListenerList) {
            val batteryState = currentState()
            for (item in mListenerList) {
                if (item.onAppLowEnergy(batteryState, duringMillis)) {
                    removeListener(item)
                }
            }
        }
    }

    @VisibleForTesting
    fun dispatchBatteryStateChangedEvent(intent: Intent) {
        val action = intent.action
        if (Intent.ACTION_BATTERY_OKAY == action || Intent.ACTION_BATTERY_LOW == action) {
            TraceHarborLog.i(TAG, "onBatteryStateChanged >> $action")
            synchronized(mListenerList) {
                val batteryState = currentState()
                for (item in mListenerList) {
                    if (item is Listener.ExListener) {
                        if (item.onBatteryStateChanged(
                                batteryState,
                                Intent.ACTION_BATTERY_LOW == action
                            )
                        ) {
                            removeListener(item)
                        }
                    }
                }
            }
        } else {
            throw IllegalStateException("Illegal battery state: $action")
        }
    }

    @VisibleForTesting
    fun dispatchBatteryPowerChanged(pct: Int) {
        TraceHarborLog.i(TAG, "onBatteryPowerChanged >> $pct%")
        synchronized(mListenerList) {
            val batteryState = currentState()
            for (item in mListenerList) {
                if (item is Listener.ExListener) {
                    if (item.onBatteryPowerChanged(batteryState, pct)) {
                        removeListener(item)
                    }
                }
            }
        }
    }

    @VisibleForTesting
    fun dispatchBatteryFullCharged() {
        TraceHarborLog.i(TAG, "dispatchBatteryFullCharged")
        synchronized(mListenerList) {
            val batteryState = currentState()
            for (item in mListenerList) {
                if (item is Listener.ExListener) {
                    if (item.onBatteryFullCharged(batteryState)) {
                        removeListener(item)
                    }
                }
            }
        }
    }

    @VisibleForTesting
    fun dispatchBatteryTemperatureChanged(temperature: Int) {
        TraceHarborLog.i(TAG, "onBatteryTemperatureChanged >> " + temperature / 10f + "°C")
        synchronized(mListenerList) {
            val batteryState = currentState()
            for (item in mListenerList) {
                if (item is Listener.ExListener) {
                    if (item.onBatteryTemperatureChanged(batteryState, temperature)) {
                        removeListener(item)
                    }
                }
            }
        }
    }

    inner class BackgroundTask : Runnable {
        private var duringMillis = 0L

        fun reset(): Long {
            duringMillis = 0L
            setNext(5 * 60 * 1000L)
            return duringMillis
        }

        fun setNext(millis: Long): Long {
            duringMillis += millis
            return millis
        }

        override fun run() {
            if (!sIsForeground) {
                if (!BatteryCanaryUtil.isDeviceCharging(mContext)) {
                    onAppLowEnergyEvent(duringMillis)
                }

                if (duringMillis <= 5 * 60 * 1000L) {
                    mUiHandler.postDelayed(this, setNext(5 * 60 * 1000L))
                } else {
                    if (duringMillis <= 10 * 60 * 1000L) {
                        mUiHandler.postDelayed(this, setNext(20 * 60 * 1000L))
                    }
                }
            }
        }
    }

    class BatteryState(context: Context) {
        @JvmField
        var mCore: BatteryMonitorCore? = null

        @JvmField
        val mContext: Context = context

        fun attach(core: BatteryMonitorCore?): BatteryState {
            if (core != null) {
                mCore = core
            }
            return this
        }

        fun isForeground(): Boolean {
            return mCore == null || mCore!!.isForeground()
        }

        fun isCharging(): Boolean {
            return BatteryCanaryUtil.isDeviceCharging(mContext)
        }

        fun isScreenOn(): Boolean {
            return BatteryCanaryUtil.isDeviceScreenOn(mContext)
        }

        fun isSysDozeMode(): Boolean {
            return BatteryCanaryUtil.isDeviceOnIdleMode(mContext)
        }

        fun isAppStandbyMode(): Boolean {
            return BatteryCanaryUtil.isDeviceOnPowerSave(mContext)
        }

        @Suppress("unused")
        fun isLowBattery(): Boolean {
            return BatteryCanaryUtil.isLowBattery(mContext)
        }

        @Suppress("unused")
        fun getBatteryPercentage(): Int {
            return BatteryCanaryUtil.getBatteryPercentage(mContext)
        }

        @Suppress("unused")
        fun getBatteryCapacity(): Int {
            return BatteryCanaryUtil.getBatteryCapacity(mContext)
        }

        fun getBackgroundTimeMillis(): Long {
            if (isForeground()) return 0L
            if (sBackgroundBgnMillis <= 0L) return 0L
            return SystemClock.uptimeMillis() - sBackgroundBgnMillis
        }

        override fun toString(): String {
            return "BatteryState{" +
                    "fg=" + isForeground() +
                    ", charge=" + isCharging() +
                    ", screen=" + isScreenOn() +
                    ", sysDoze=" + isSysDozeMode() +
                    ", appStandby=" + isAppStandbyMode() +
                    ", bgMillis=" + getBackgroundTimeMillis() +
                    '}'
        }
    }

    interface Listener {
        @RequiresApi(api = Build.VERSION_CODES.M)
        @StringDef(
            value = [
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED,
                Intent.ACTION_BATTERY_OKAY,
                Intent.ACTION_BATTERY_LOW,
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED,
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED,
            ],
        )
        @Retention(RetentionPolicy.SOURCE)
        annotation class BatteryEventDef

        /**
         * @see ExListener.onStateChanged
         * @return return true if your listening is done, thus we remove your listener
         */
        @UiThread
        @Deprecated("")
        fun onStateChanged(@BatteryEventDef event: String?): Boolean

        /**
         * @return return true if your listening is done, thus we remove your listener
         */
        @UiThread
        fun onAppLowEnergy(batteryState: BatteryState, backgroundMillis: Long): Boolean

        interface ExListener : Listener {
            @UiThread
            fun onStateChanged(batteryState: BatteryState, @BatteryEventDef event: String?): Boolean

            /**
             * On battery temperature changed.
             *
             * @param batteryState [BatteryState]
             * @param temperature  See [BatteryManager.EXTRA_TEMPERATURE], °C * 10
             * @return return true if your listening is done, thus we remove your listener
             */
            @UiThread
            fun onBatteryTemperatureChanged(batteryState: BatteryState, temperature: Int): Boolean

            /**
             * On battery power changed.
             *
             * @param batteryState [BatteryState]
             * @param levelPct     Battery capacity level 0 - 100
             * @return return true if your listening is done, thus we remove your listener
             */
            @UiThread
            fun onBatteryPowerChanged(batteryState: BatteryState, levelPct: Int): Boolean

            /**
             * On battery power full charged.
             *
             * @param batteryState [BatteryState]
             * @return return true if your listening is done, thus we remove your listener
             */
            @UiThread
            fun onBatteryFullCharged(batteryState: BatteryState): Boolean

            /**
             * On battery power low or ok.
             *
             * @param batteryState [BatteryState]
             * @param isLowBattery [Intent.ACTION_BATTERY_LOW], [Intent.ACTION_BATTERY_OKAY]
             * @return return true if your listening is done, thus we remove your listener
             */
            @UiThread
            fun onBatteryStateChanged(batteryState: BatteryState, isLowBattery: Boolean): Boolean
        }

        @Suppress("unused")
        open class DefaultListenerImpl(@JvmField val mKeepAlive: Boolean) : ExListener {
            @Deprecated("")
            override fun onStateChanged(event: String?): Boolean {
                throw RuntimeException("Use #onStateChanged(BatteryState, String) instead")
            }

            override fun onAppLowEnergy(
                batteryState: BatteryState,
                backgroundMillis: Long
            ): Boolean {
                return !mKeepAlive
            }

            override fun onStateChanged(batteryState: BatteryState, event: String?): Boolean {
                return !mKeepAlive
            }

            override fun onBatteryTemperatureChanged(
                batteryState: BatteryState,
                temperature: Int
            ): Boolean {
                return !mKeepAlive
            }

            override fun onBatteryPowerChanged(batteryState: BatteryState, levelPct: Int): Boolean {
                return !mKeepAlive
            }

            override fun onBatteryFullCharged(batteryState: BatteryState): Boolean {
                return !mKeepAlive
            }

            override fun onBatteryStateChanged(
                batteryState: BatteryState,
                isLowBattery: Boolean
            ): Boolean {
                return !mKeepAlive
            }
        }
    }

    companion object {
        const val TAG: String = "TraceHarbor.battery.LifeCycle"
        private const val BATTERY_POWER_GRADUATION = 5
        private const val BATTERY_TEMPERATURE_GRADUATION = 15

        @SuppressLint("StaticFieldLeak")
        @JvmField
        @Volatile
        var sInstance: BatteryEventDelegate? = null

        @JvmField
        var sBackgroundBgnMillis: Long = 0L

        @VisibleForTesting
        @JvmStatic
        fun release() {
            sInstance = null
        }

        @JvmStatic
        fun isInit(): Boolean {
            if (sInstance != null) {
                return true
            } else {
                synchronized(TAG) {
                    return sInstance != null
                }
            }
        }

        @JvmStatic
        fun init(application: Application) {
            if (sInstance == null) {
                synchronized(TAG) {
                    if (sInstance == null) {
                        sInstance = BatteryEventDelegate(application)
                    }
                }
            }
        }

        @JvmStatic
        fun getInstance(): BatteryEventDelegate {
            if (sInstance == null) {
                synchronized(TAG) {
                    if (sInstance == null) {
                        throw IllegalStateException("Call #init() first!")
                    }
                }
            }
            return sInstance!!
        }
    }
}
