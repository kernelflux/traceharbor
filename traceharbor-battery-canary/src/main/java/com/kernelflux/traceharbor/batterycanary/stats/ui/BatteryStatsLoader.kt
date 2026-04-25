package com.kernelflux.traceharbor.batterycanary.stats.ui

import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import com.kernelflux.traceharbor.batterycanary.BatteryCanary
import com.kernelflux.traceharbor.batterycanary.monitor.AppStats
import com.kernelflux.traceharbor.batterycanary.stats.BatteryRecord
import com.kernelflux.traceharbor.batterycanary.stats.BatteryStatsFeature
import com.kernelflux.traceharbor.batterycanary.stats.BatteryStatsFeature.BatteryRecords
import com.kernelflux.traceharbor.batterycanary.stats.ui.BatteryStatsAdapter.Item
import com.kernelflux.traceharbor.batterycanary.utils.Consumer
import com.kernelflux.traceharbor.util.TraceHarborLog
import kotlin.math.abs
import kotlin.math.max

/**
 * @author Kaede
 * @since 2021/12/10
 */
open class BatteryStatsLoader @JvmOverloads constructor(
    @JvmField
    protected val mStatsAdapter: BatteryStatsAdapter,
    @JvmField
    protected val mDayLimit: Int = DAY_LIMIT,
) {
    @JvmField
    protected val mUiHandler: Handler = Handler(Looper.getMainLooper())

    @JvmField
    protected var mDayOffset: Int = 0

    @JvmField
    protected var mProc: String = ""

    @JvmField
    protected var mFilter: Filter? = null

    fun getAdapter(): BatteryStatsAdapter = mStatsAdapter

    fun getDateSet(): List<Item> = mStatsAdapter.getDataList()

    fun setFilter(filter: Filter) {
        mFilter = filter
    }

    fun removeFilter() {
        mFilter = null
    }

    fun reset(proc: String) {
        mProc = proc
        reset()
    }

    fun reset() {
        mDayOffset = 0
        postClearDataSet()
    }

    fun load() {
        if (TextUtils.isEmpty(mProc)) {
            TraceHarborLog.w(TAG, "Call #reset first!")
            return
        }
        load(mDayOffset)
    }

    fun loadMore(): Boolean {
        if (abs(mDayOffset) >= mDayLimit) {
            return false
        }
        mDayOffset--
        load(mDayOffset)
        return true
    }

    fun getFirstHeader(topPosition: Int): Item.HeaderItem? {
        var currHeader: Item.HeaderItem? = null
        val dataList = mStatsAdapter.getDataList()
        for (i in topPosition downTo 0) {
            if (topPosition < dataList.size) {
                val item = dataList[i]
                if (item is Item.HeaderItem) {
                    currHeader = item
                    break
                }
            }
        }
        return currHeader
    }

    protected open fun load(dayOffset: Int) {
        BatteryCanary.getMonitorFeature(BatteryStatsFeature::class.java, Consumer { batteryStatsFeature ->
            var records = batteryStatsFeature.readRecords(dayOffset, mProc)
            mFilter?.let { filter ->
                records = filter.filtering(records)
            }
            val batteryRecords = BatteryRecords()
            batteryRecords.date = BatteryStatsFeature.getDateString(dayOffset)
            batteryRecords.records = records
            add(batteryRecords)
        })
    }

    fun add(batteryRecords: BatteryRecords) {
        val dataList = onCreateDataSet(batteryRecords)

        // Footer
        if (abs(mDayOffset) >= mDayLimit) {
            val headerItem = Item.HeaderItem()
            headerItem.date = "END"
            dataList.add(headerItem)
            val footerItem = Item.NoDataItem()
            footerItem.text = "Only keep last " + mDayLimit + " days' data"
            dataList.add(footerItem)
        }

        // Notify
        postUpdateDataSet(dataList)
    }

    private fun postUpdateDataSet(dataList: List<Item>) {
        mUiHandler.post {
            val start = mStatsAdapter.getDataList().size - 1
            val length = dataList.size
            mStatsAdapter.getDataList().addAll(dataList)
            mStatsAdapter.notifyItemRangeChanged(max(start, 0), length)
        }
    }

    private fun postClearDataSet() {
        mUiHandler.post {
            val length = mStatsAdapter.getDataList().size
            mStatsAdapter.getDataList().clear()
            mStatsAdapter.notifyItemRangeRemoved(0, length)
        }
    }

    protected open fun onCreateDataSet(batteryRecords: BatteryRecords): MutableList<Item> {
        val dataList: MutableList<Item> = ArrayList(batteryRecords.records.size + 1)

        // Records
        if (batteryRecords.records.isEmpty()) {
            // N0 DATA
            val item = Item.NoDataItem()
            item.text = "NO DATA"
            dataList.add(0, item)
        } else {
            // Convert records to list items
            for (record in batteryRecords.records) {
                val item = onCreateDataItem(record)
                dataList.add(0, item)
            }
        }

        // Date
        val headerItem = Item.HeaderItem()
        headerItem.date = batteryRecords.date
        dataList.add(0, headerItem)

        return dataList
    }

    protected open fun onCreateDataItem(record: BatteryRecord): Item {
        if (record is BatteryRecord.ProcStatRecord) {
            val item = Item.EventLevel1Item(record)
            val title = when (record.procStat) {
                BatteryRecord.ProcStatRecord.STAT_PROC_LAUNCH -> "PROCESS_INIT"
                BatteryRecord.ProcStatRecord.STAT_PROC_OFF -> "PROCESS_QUIT"
                else -> "PROCESS_ID_" + record.procStat
            }
            item.text = title + " (pid " + record.pid + "）"
            return item
        }

        if (record is BatteryRecord.AppStatRecord) {
            val item = Item.EventLevel1Item(record)
            item.text = when (record.appStat) {
                AppStats.APP_STAT_FOREGROUND -> "App 切换到前台"
                AppStats.APP_STAT_BACKGROUND -> "App 切换到后台"
                AppStats.APP_STAT_FOREGROUND_SERVICE -> "App 切换到后台 (有前台服务)"
                AppStats.APP_STAT_FLOAT_WINDOW -> "App 切换到后台 (有浮窗)"
                else -> "App 状态变化: " + record.appStat
            }
            return item
        }

        if (record is BatteryRecord.DevStatRecord) {
            val item = Item.EventLevel2Item(record)
            item.text = when (record.devStat) {
                AppStats.DEV_STAT_CHARGING -> "CHARGE_ON"
                AppStats.DEV_STAT_UN_CHARGING -> "CHARGE_OFF"
                AppStats.DEV_STAT_DOZE_MODE_ON -> "低电耗模式(Doze) ON"
                AppStats.DEV_STAT_DOZE_MODE_OFF -> "低电耗模式(Doze) OFF"
                AppStats.DEV_STAT_SAVE_POWER_MODE_ON -> "待机模式(Standby) ON"
                AppStats.DEV_STAT_SAVE_POWER_MODE_OFF -> "待机模式(Standby) OFF"
                AppStats.DEV_STAT_SCREEN_ON -> "SCREEN_ON"
                AppStats.DEV_STAT_SCREEN_OFF -> "SCREEN_OFF"
                else -> "设备状态变化: " + record.devStat
            }
            return item
        }

        if (record is BatteryRecord.SceneStatRecord) {
            val item = Item.EventLevel2Item(record)
            item.text = "UI: " + record.scene
            return item
        }

        if (record is BatteryRecord.ReportRecord) {
            return Item.EventDumpItem(record)
        }

        if (record is BatteryRecord.EventStatRecord) {
            if (BatteryRecord.EventStatRecord.EVENT_BATTERY_STAT == record.event) {
                return Item.EventBatteryItem(record)
            }
            return Item.EventSimpleItem(record)
        }

        val item = Item.EventLevel2Item(record)
        item.text = "Unknown: " + record.javaClass.name
        return item
    }

    fun interface Filter {
        fun filtering(input: List<BatteryRecord>): List<BatteryRecord>
    }

    companion object {
        private const val TAG = "TraceHarbor.battery.loader"
        private const val DAY_LIMIT = 7
    }
}
