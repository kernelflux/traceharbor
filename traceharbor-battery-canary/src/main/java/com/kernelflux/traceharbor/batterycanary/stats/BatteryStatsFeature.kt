package com.kernelflux.traceharbor.batterycanary.stats

import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.kernelflux.traceharbor.batterycanary.monitor.AppStats
import com.kernelflux.traceharbor.batterycanary.monitor.feature.AbsMonitorFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.CompositeMonitors
import com.kernelflux.traceharbor.batterycanary.utils.BatteryCanaryUtil
import com.kernelflux.traceharbor.util.TraceHarborHandlerThread
import java.util.Collections
import java.util.HashMap

/**
 * @author Kaede
 * @since 2021/12/3
 */
class BatteryStatsFeature : AbsMonitorFeature() {
    private var mStatsThread: HandlerThread? = null
    private var mStatsHandler: Handler? = null
    private var mBatteryRecorder: BatteryRecorder? = null
    private var mBatteryStats: BatteryStats? = null
    private var mStatsImmediately = false

    override fun weight(): Int = 0

    override fun getTag(): String = TAG

    override fun onTurnOn() {
        super.onTurnOn()
        mBatteryRecorder = core.getConfig().batteryRecorder
        mBatteryStats = core.getConfig().batteryStats
        val batteryRecorder = mBatteryRecorder
        if (batteryRecorder != null) {
            mStatsThread = TraceHarborHandlerThread.getNewHandlerThread("traceharbor_stats", Thread.NORM_PRIORITY)
            mStatsHandler = Handler(mStatsThread!!.looper)
            mStatsHandler?.post {
                batteryRecorder.updateProc(BatteryRecorder.MMKVRecorder.getProcNameSuffix())

                // Clean expired records if need
                batteryRecorder.clean(DAY_LIMIT)
            }
        }

        val procStatRecord = BatteryRecord.ProcStatRecord()
        procStatRecord.pid = Process.myPid()
        procStatRecord.procStat = BatteryRecord.ProcStatRecord.STAT_PROC_LAUNCH
        writeRecord(procStatRecord)
    }

    override fun onForeground(isForeground: Boolean) {
        super.onForeground(isForeground)
        statsAppStat(if (isForeground) AppStats.APP_STAT_FOREGROUND else AppStats.APP_STAT_BACKGROUND)
    }

    override fun onTurnOff() {
        super.onTurnOff()
        val procStatRecord = BatteryRecord.ProcStatRecord()
        procStatRecord.pid = Process.myPid()
        procStatRecord.procStat = BatteryRecord.ProcStatRecord.STAT_PROC_OFF
        writeRecord(procStatRecord)

        mStatsHandler?.post {
            mStatsThread?.quit()
        }
    }

    @VisibleForTesting
    fun setStatsImmediately(statsImmediately: Boolean) {
        mStatsImmediately = statsImmediately
    }

    fun getProcSet(): Set<String> {
        val batteryRecorder = mBatteryRecorder
        if (batteryRecorder != null) {
            return batteryRecorder.getProcSet()
        }
        return Collections.emptySet()
    }

    fun writeRecord(record: BatteryRecord) {
        val batteryRecorder = mBatteryRecorder
        if (batteryRecorder != null) {
            val date = getDateString(0)
            if (mStatsImmediately) {
                writeRecord(date, record)
                return
            }
            mStatsHandler?.post {
                writeRecord(date, record)
            }
        }
    }

    @WorkerThread
    fun writeRecord(date: String, record: BatteryRecord) {
        mBatteryRecorder?.write(date, record)
    }

    @WorkerThread
    fun readRecords(dayOffset: Int, proc: String?): List<BatteryRecord> {
        val batteryRecorder = mBatteryRecorder
        if (batteryRecorder != null) {
            val date = getDateString(dayOffset)
            return batteryRecorder.read(date, proc)
        }
        return Collections.emptyList()
    }

    @WorkerThread
    fun readRecords(date: String, proc: String?): List<BatteryRecord> {
        val batteryRecorder = mBatteryRecorder
        if (batteryRecorder != null) {
            return batteryRecorder.read(date, proc)
        }
        return Collections.emptyList()
    }

    @WorkerThread
    fun readBatteryRecords(dayOffset: Int, proc: String?): BatteryRecords {
        val batteryRecords = BatteryRecords()
        batteryRecords.date = getDateString(dayOffset)
        batteryRecords.records = readRecords(dayOffset, proc)
        return batteryRecords
    }

    @WorkerThread
    fun cleanRecords(date: String, proc: String?) {
        mBatteryRecorder?.clean(date, proc)
    }

    @WorkerThread
    fun cleanRecords() {
        mBatteryRecorder?.clean(DAY_LIMIT)
    }

    fun statsAppStat(appStat: Int) {
        mBatteryStats?.let { writeRecord(it.statsAppStat(appStat)) }
    }

    fun statsDevStat(devStat: Int) {
        mBatteryStats?.let { writeRecord(it.statsDevStat(devStat)) }
    }

    fun statsScene(scene: String) {
        mBatteryStats?.let { writeRecord(it.statsScene(scene)) }
    }

    fun statsEvent(event: String) {
        statsEvent(event, 0)
    }

    fun statsEvent(event: String, eventId: Int) {
        statsEvent(event, eventId, Collections.emptyMap<String, Any>())
    }

    fun statsEvent(event: String, eventId: Int, extras: Map<String, Any>) {
        mBatteryStats?.let { writeRecord(it.statsEvent(event, eventId, extras)) }
    }

    fun statsBatteryEvent(isLowBattery: Boolean) {
        val event = BatteryRecord.EventStatRecord.EVENT_BATTERY_STAT
        val id = 0
        val pct = BatteryCanaryUtil.getBatteryPercentage(core.getContext())
        val extras: MutableMap<String, Any> = HashMap()
        extras["battery-low"] = isLowBattery
        extras["battery-pct"] = pct
        statsEvent(event, id, extras)
    }

    fun statsBatteryEvent(pct: Int) {
        val event = BatteryRecord.EventStatRecord.EVENT_BATTERY_STAT
        val id = 0
        val extras: MutableMap<String, Any> = HashMap()
        extras["battery-change"] = true
        extras["battery-pct"] = pct
        statsEvent(event, id, extras)
    }

    fun statsBatteryTempEvent(temperature: Int) {
        val event = BatteryRecord.EventStatRecord.EVENT_BATTERY_STAT
        val id = 0
        val pct = BatteryCanaryUtil.getBatteryPercentage(core.getContext())
        val extras: MutableMap<String, Any> = HashMap()
        extras["battery-temp"] = temperature
        extras["battery-pct"] = pct
        statsEvent(event, id, extras)
    }

    fun statsMonitors(monitors: CompositeMonitors) {
        if (mBatteryRecorder == null) {
            return
        }
        if (monitors.getAppStats() == null) {
            return
        }
        if (mBatteryStats != null) {
            writeRecord(mBatteryStats!!.statsMonitors(monitors))
        }
    }

    class BatteryRecords {
        @JvmField
        var date: String? = null

        @JvmField
        var records: List<BatteryRecord> = Collections.emptyList()
    }

    companion object {
        private const val TAG = "TraceHarbor.battery.BatteryStats"
        private const val DAY_LIMIT = 7

        @JvmStatic
        fun getDateString(dayOffset: Int): String = BatteryRecorder.MMKVRecorder.getDateString(dayOffset)
    }
}

