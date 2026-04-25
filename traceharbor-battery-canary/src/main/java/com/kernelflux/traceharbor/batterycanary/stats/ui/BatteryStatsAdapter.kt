package com.kernelflux.traceharbor.batterycanary.stats.ui

import android.annotation.SuppressLint
import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.kernelflux.traceharbor.batterycanary.R
import com.kernelflux.traceharbor.batterycanary.monitor.feature.CompositeMonitors
import com.kernelflux.traceharbor.batterycanary.stats.BatteryRecord
import com.kernelflux.traceharbor.batterycanary.stats.BatteryRecord.ReportRecord.Companion.EXTRA_APP_FOREGROUND
import com.kernelflux.traceharbor.batterycanary.stats.BatteryRecord.ReportRecord.Companion.EXTRA_JIFFY_OVERHEAT
import com.kernelflux.traceharbor.batterycanary.utils.ThreadSafeReference
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * @author Kaede
 * @since 2021/12/10
 */
open class BatteryStatsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    @JvmField
    protected val dataList: MutableList<Item> = ArrayList()

    fun getDataList(): MutableList<Item> = dataList

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> ViewHolder.HeaderHolder(inflater.inflate(R.layout.stats_item_header, parent, false))
            VIEW_TYPE_NO_DATA -> ViewHolder.NoDataHolder(inflater.inflate(R.layout.stats_item_no_data, parent, false))
            VIEW_TYPE_EVENT_DUMP -> ViewHolder.EventDumpHolder(inflater.inflate(R.layout.stats_item_event_dump, parent, false))
            VIEW_TYPE_EVENT_SIMPLE -> ViewHolder.EventSimpleHolder(inflater.inflate(R.layout.stats_item_event_simple, parent, false))
            VIEW_TYPE_EVENT_BATTERY -> ViewHolder.EventBatteryHolder(inflater.inflate(R.layout.stats_item_event_battery, parent, false))
            VIEW_TYPE_EVENT_LEVEL_1 -> ViewHolder.EventLevel1Holder(inflater.inflate(R.layout.stats_item_event_1, parent, false))
            VIEW_TYPE_EVENT_LEVEL_2 -> ViewHolder.EventLevel2Holder(inflater.inflate(R.layout.stats_item_event_2, parent, false))
            else -> throw IllegalStateException("Unknown view type: $viewType")
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ViewHolder<Item>).bind(dataList[position])
    }

    override fun getItemCount(): Int = dataList.size

    override fun getItemViewType(position: Int): Int = dataList[position].viewType()

    interface Item {
        fun viewType(): Int

        open class HeaderItem : Item {
            @JvmField
            var date: String? = null

            override fun viewType(): Int = VIEW_TYPE_HEADER
        }

        open class NoDataItem : Item {
            @JvmField
            var text: String? = null

            override fun viewType(): Int = VIEW_TYPE_NO_DATA
        }

        open class EventDumpItem(
            @JvmField val record: BatteryRecord.ReportRecord,
        ) : BatteryRecord.ReportRecord(), Item {
            @JvmField
            var expand: Boolean = false

            @JvmField
            var desc: String? = null

            init {
                millis = record.millis
                id = record.id
                event = record.event
                extras = record.extras
                scope = record.scope
                windowMillis = record.windowMillis
                threadInfoList = record.threadInfoList
                entryList = record.entryList
            }

            override fun viewType(): Int = VIEW_TYPE_EVENT_DUMP
        }

        open class EventSimpleItem(
            @JvmField val record: BatteryRecord.EventStatRecord,
        ) : BatteryRecord.EventStatRecord(), Item {
            init {
                millis = record.millis
                id = record.id
                event = record.event
            }

            override fun viewType(): Int = VIEW_TYPE_EVENT_SIMPLE
        }

        open class EventBatteryItem(
            @JvmField val record: BatteryRecord.EventStatRecord,
        ) : BatteryRecord.EventStatRecord(), Item {
            init {
                millis = record.millis
                id = record.id
                event = record.event
            }

            override fun viewType(): Int = VIEW_TYPE_EVENT_BATTERY
        }

        @SuppressLint("ParcelCreator")
        open class EventLevel1Item(record: BatteryRecord) : BatteryRecord(), Item {
            @JvmField
            var text: String? = null

            init {
                millis = record.millis
            }

            override fun viewType(): Int = VIEW_TYPE_EVENT_LEVEL_1
        }

        @SuppressLint("ParcelCreator")
        open class EventLevel2Item(record: BatteryRecord) : BatteryRecord(), Item {
            @JvmField
            var text: String? = null

            init {
                millis = record.millis
            }

            override fun viewType(): Int = VIEW_TYPE_EVENT_LEVEL_2
        }
    }

    abstract class ViewHolder<ITEM : Item>(itemView: View) : RecyclerView.ViewHolder(itemView) {
        protected lateinit var mItem: ITEM

        open fun bind(item: ITEM) {
        }

        open class HeaderHolder(itemView: View) : ViewHolder<Item.HeaderItem>(itemView) {
            private val mTitleTv: TextView = itemView.findViewById(R.id.tv_title)

            override fun bind(item: Item.HeaderItem) {
                mItem = item
                mTitleTv.text = item.date
            }
        }

        open class NoDataHolder(itemView: View) : ViewHolder<Item.NoDataItem>(itemView) {
            private val mTitleTv: TextView = itemView.findViewById(R.id.tv_title)

            override fun bind(item: Item.NoDataItem) {
                mItem = item
                if (!TextUtils.isEmpty(item.text)) {
                    mTitleTv.text = item.text
                }
            }
        }

        open class EventDumpHolder(itemView: View) : ViewHolder<Item.EventDumpItem>(itemView) {
            private val mTimeTv: TextView = itemView.findViewById(R.id.tv_time)
            private val mTitleTv: TextView = itemView.findViewById(R.id.tv_title)
            private val mTitleSub1: TextView = itemView.findViewById(R.id.tv_title_sub_1)
            private val mTitleSub2: TextView = itemView.findViewById(R.id.tv_title_sub_2)
            private val mMoreTv: TextView = itemView.findViewById(R.id.tv_more)
            private val mIndicatorIv: ImageView = itemView.findViewById(R.id.iv_indicator)
            private val mExpandView: View = itemView.findViewById(R.id.layout_expand)

            private val mHeaderEntryView: View = itemView.findViewById(R.id.layout_expand_entry_header)
            private val mHeaderLeftTv: TextView = mHeaderEntryView.findViewById(R.id.tv_header_left)
            private val mHeaderRightTv: TextView = mHeaderEntryView.findViewById(R.id.tv_header_right)
            private val mHeaderDescTv: TextView = mHeaderEntryView.findViewById(R.id.tv_desc)

            private val mEntryViewThread: View = itemView.findViewById(R.id.layout_expand_entry_thread)
            private val mEntryView1: View = itemView.findViewById(R.id.layout_expand_entry_1)
            private val mEntryView2: View = itemView.findViewById(R.id.layout_expand_entry_2)
            private val mEntryView3: View = itemView.findViewById(R.id.layout_expand_entry_3)
            private val mEntryView4: View = itemView.findViewById(R.id.layout_expand_entry_4)

            init {
                val onClickListener = View.OnClickListener {
                    mItem.expand = !mItem.expand
                    updateView(mItem)
                }
                itemView.findViewById<View>(R.id.layout_title).setOnClickListener(onClickListener)
                itemView.findViewById<View>(R.id.layout_right).setOnClickListener(onClickListener)

                mEntryViewThread.setOnClickListener { v ->
                    val endMillis = mItem.record.millis
                    val sb = StringBuilder()
                    val bgnMillis = mItem.record.millis - mItem.record.windowMillis
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                    sb.append("线程异常: ").append(mItem.record.getBoolean(EXTRA_JIFFY_OVERHEAT, false))
                        .append("\n统计时长: ").append(max(mItem.record.windowMillis / (60 * 1000L), 1)).append("min")
                        .append("\n时间窗口: ").append(dateFormat.format(Date(bgnMillis))).append(" ~ ").append(dateFormat.format(Date(endMillis)))

                    for (i in mItem.record.threadInfoList.indices) {
                        val threadInfo = mItem.record.threadInfoList[i]
                        if (threadInfo != null) {
                            val status = when (threadInfo.stat) {
                                "R" -> "Running"
                                "S" -> "Sleep"
                                "D" -> "Dead"
                                else -> threadInfo.stat
                            }
                            sb.append("\n\n线程 TOP ").append(i + 1).append(":").append(threadInfo.name)
                                .append("\ntid: ").append(threadInfo.tid)
                                .append("\n状态: ").append(status)
                                .append("\nJiffy 开销: ").append(threadInfo.jiffies).append(", ").append(threadInfo.jiffies / max(mItem.windowMillis / (60 * 1000L), 1)).append("/min")
                                .append("\n运行时间: ").append(max(threadInfo.jiffies * 10L / (60 * 1000L), 1)).append("min").append(", 占整体统计时间 ").append(String.format(Locale.US, "%s", threadInfo.jiffies * 10L * 100 / mItem.windowMillis)).append("%")
                                .append("\nStackTrace: \n").append(threadInfo.extraInfo[BatteryRecord.ReportRecord.EXTRA_THREAD_STACK])
                        }
                    }

                    val layout = LayoutInflater.from(v.context).inflate(R.layout.stats_battery_report, null)
                    val tv = layout.findViewById<TextView>(R.id.tv_report)
                    tv.text = sb.toString()
                    val dialog = AlertDialog.Builder(v.context)
                        .setTitle("线程详细信息")
                        .setPositiveButton("确定", null)
                        .setCancelable(true)
                        .setView(layout)
                        .create()
                    dialog.show()
                }
            }

            override fun bind(item: Item.EventDumpItem) {
                mItem = item
                resetView()
                updateView(item)
            }

            private fun resetView() {
                mExpandView.visibility = View.GONE
                mHeaderEntryView.visibility = View.VISIBLE
                mEntryViewThread.visibility = View.GONE
                mEntryView1.visibility = View.GONE
                mEntryView2.visibility = View.GONE
                mEntryView3.visibility = View.GONE
                mEntryView4.visibility = View.GONE
            }

            @SuppressLint("SetTextI18n", "CutPasteId")
            private fun updateView(item: Item.EventDumpItem) {
                var title = ""
                var desc = ""
                when (if (TextUtils.isEmpty(item.record.scope)) "" else item.record.scope) {
                    CompositeMonitors.SCOPE_CANARY -> {
                        if (item.record.getBoolean(EXTRA_APP_FOREGROUND, false)) {
                            title += "前台 Polling 监控"
                            desc = "App 在前台时, 周期性地执行电量统计 (具体周期见时长)"
                        } else {
                            title += "待机功耗监控"
                            desc = "App 进入后台并持续一段时间后 (待机), 再次切换到前台时执行一次电量统计。"
                        }
                    }
                    CompositeMonitors.SCOPE_INTERNAL -> {
                        title += "TraceHarbor 内部监控"
                        desc = "TraceHarbor 自身电量开销的监控, 避免电量监控框架自身导致的耗电问题"
                    }
                    CompositeMonitors.SCOPE_OVERHEAT -> {
                        title += "Runnable 任务监控"
                        desc = "ThreadPool 等需要执行大量零碎 Runnable 的专项电量统计。"
                    }
                    else -> {
                        title += ": " + item.record.scope
                        desc = "缺乏描述"
                    }
                }

                mTimeTv.text = sTimeFormatRef.safeGet().format(Date(item.millis))
                mMoreTv.rotation = if (item.expand) 180f else 0f
                mExpandView.visibility = if (item.expand) View.VISIBLE else View.GONE
                mTitleTv.text = title
                mTitleSub1.text = sTimeFormatRef.safeGet().format(Date(item.millis - item.windowMillis)) + " ~ " + sTimeFormatRef.safeGet().format(Date(item.millis))
                if (item.record.isOverHeat()) {
                    mIndicatorIv.setImageLevel(4)
                    mTitleSub2.text = "#OVERHEAT"
                } else {
                    mIndicatorIv.setImageLevel(2)
                    mTitleSub2.text = "正常"
                }
                if (!item.expand) {
                    return
                }

                mHeaderLeftTv.text = "模式: " + item.scope
                mHeaderRightTv.text = "时长: " + max(1, item.windowMillis / (60 * 1000L)) + "min"
                mHeaderDescTv.text = if (TextUtils.isEmpty(item.desc)) desc else item.desc

                mEntryViewThread.visibility = if (item.threadInfoList.isNotEmpty()) View.VISIBLE else View.GONE
                if (item.threadInfoList.isNotEmpty()) {
                    val overHeat = item.record.getBoolean(EXTRA_JIFFY_OVERHEAT, false)
                    val tvTitle = mEntryViewThread.findViewById<TextView>(R.id.tv_header_left)
                    tvTitle.setTextColor(tvTitle.resources.getColor(if (overHeat) COLOR_FG_ALERT else COLOR_FG_SUB))

                    val entryGroup = mEntryViewThread.findViewById<LinearLayout>(R.id.layout_entry_group)
                    val reusableCount = entryGroup.childCount
                    for (i in 0 until reusableCount) {
                        entryGroup.getChildAt(i).visibility = View.GONE
                    }
                    for (i in 0 until 5) {
                        if (i < item.threadInfoList.size) {
                            val threadInfo = item.threadInfoList[i]
                            val entryItemView = if (i < reusableCount) {
                                entryGroup.getChildAt(i)
                            } else {
                                val newEntryItemView = LayoutInflater.from(entryGroup.context).inflate(R.layout.stats_item_event_dump_entry_subentry, entryGroup, false)
                                val layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                                layoutParams.topMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, entryGroup.context.resources.displayMetrics).toInt()
                                entryGroup.addView(newEntryItemView, layoutParams)
                                newEntryItemView
                            }

                            entryItemView.visibility = View.VISIBLE
                            val left = entryItemView.findViewById<TextView>(R.id.tv_key)
                            val right = entryItemView.findViewById<TextView>(R.id.tv_value)
                            left.visibility = if (threadInfo != null) View.VISIBLE else View.GONE
                            right.visibility = if (threadInfo != null) View.VISIBLE else View.GONE

                            if (threadInfo != null) {
                                left.text = threadInfo.name
                                right.text = threadInfo.tid.toString() + " / " + max(1, threadInfo.jiffies * 10 / (60 * 1000L)) + "min / " + threadInfo.stat
                            }
                        }
                    }
                }

                for (i in 1..4) {
                    val entryView = when (i) {
                        1 -> mEntryView1
                        2 -> mEntryView2
                        3 -> mEntryView3
                        4 -> mEntryView4
                        else -> throw IndexOutOfBoundsException("entryList section out of bound: $i")
                    }

                    val entryInfo = if (i <= item.entryList.size) item.entryList[i - 1] else null
                    entryView.visibility = if (entryInfo != null) View.VISIBLE else View.GONE
                    if (entryInfo != null) {
                        val left = entryView.findViewById<TextView>(R.id.tv_header_left)
                        left.text = entryInfo.name

                        val entryGroup = entryView.findViewById<LinearLayout>(R.id.layout_entry_group)
                        val reusableCount = entryGroup.childCount
                        for (j in 0 until reusableCount) {
                            entryGroup.getChildAt(j).visibility = View.GONE
                        }

                        var entryIdx = 0
                        val entryLimit = 6
                        for ((key, value) in entryInfo.entries) {
                            entryIdx++
                            if (entryIdx > entryLimit) {
                                break
                            }
                            val entryItemView = if (entryIdx < reusableCount) {
                                entryGroup.getChildAt(entryIdx)
                            } else {
                                val newEntryItemView = LayoutInflater.from(entryGroup.context).inflate(R.layout.stats_item_event_dump_entry_subentry, entryGroup, false)
                                val layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                                layoutParams.topMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, entryGroup.context.resources.displayMetrics).toInt()
                                entryGroup.addView(newEntryItemView, layoutParams)
                                newEntryItemView
                            }

                            entryItemView.visibility = View.VISIBLE
                            val keyTv = entryItemView.findViewById<TextView>(R.id.tv_key)
                            val valTv = entryItemView.findViewById<TextView>(R.id.tv_value)
                            keyTv.text = key
                            valTv.text = value
                        }
                    }
                }
            }
        }

        open class EventLevel1Holder(itemView: View) : ViewHolder<Item.EventLevel1Item>(itemView) {
            private val mTimeTv: TextView = itemView.findViewById(R.id.tv_time)
            private val mTitleTv: TextView = itemView.findViewById(R.id.tv_title)

            override fun bind(item: Item.EventLevel1Item) {
                mItem = item
                mTimeTv.text = sTimeFormatRef.safeGet().format(Date(item.millis))
                mTitleTv.text = item.text
            }
        }

        open class EventLevel2Holder(itemView: View) : ViewHolder<Item.EventLevel2Item>(itemView) {
            private val mTimeTv: TextView = itemView.findViewById(R.id.tv_time)
            private val mTitleTv: TextView = itemView.findViewById(R.id.tv_title)

            override fun bind(item: Item.EventLevel2Item) {
                mItem = item
                mTimeTv.text = sTimeFormatRef.safeGet().format(Date(item.millis))
                mTitleTv.text = item.text
            }
        }

        open class EventSimpleHolder(itemView: View) : ViewHolder<Item.EventSimpleItem>(itemView), View.OnClickListener {
            private val mTimeTv: TextView = itemView.findViewById(R.id.tv_time)
            private val mTitleTv: TextView = itemView.findViewById(R.id.tv_title)

            init {
                itemView.findViewById<View>(R.id.layout_title).setOnClickListener(this)
            }

            override fun bind(item: Item.EventSimpleItem) {
                mItem = item
                mTimeTv.text = sTimeFormatRef.safeGet().format(Date(item.millis))
                mTitleTv.text = item.event
            }

            override fun onClick(v: View) {
                val layout = LayoutInflater.from(v.context).inflate(R.layout.stats_battery_report, null)
                val tv = layout.findViewById<TextView>(R.id.tv_report)
                tv.text = getDetailInfo()
                val dialog = AlertDialog.Builder(v.context)
                    .setTitle(mItem.event)
                    .setPositiveButton("确定", null)
                    .setCancelable(true)
                    .setView(layout)
                    .create()
                dialog.show()
            }

            protected open fun getDetailInfo(): String {
                val sb = StringBuilder()
                sb.append("EVENT_ID: ").append(mItem.record.id).append("\n\n")
                for (key in mItem.record.extras.keys) {
                    sb.append(key).append(" = ").append(mItem.record.extras[key]).append("\n\n")
                }
                return sb.toString()
            }
        }

        open class EventBatteryHolder(itemView: View) : ViewHolder<Item.EventBatteryItem>(itemView) {
            private val mTimeTv: TextView = itemView.findViewById(R.id.tv_time)
            private val mIndicatorIv: ImageView = itemView.findViewById(R.id.iv_indicator)
            private val mTitleTv: TextView = itemView.findViewById(R.id.tv_title)

            @SuppressLint("SetTextI18n")
            override fun bind(item: Item.EventBatteryItem) {
                mItem = item
                mTimeTv.text = sTimeFormatRef.safeGet().format(Date(item.millis))
                mTitleTv.text = item.event

                mIndicatorIv.setImageLevel(1)
                if (item.record.extras.containsKey("battery-low")) {
                    val lowBattery = item.record.getBoolean("battery-low", false)
                    mIndicatorIv.setImageLevel(if (lowBattery) 4 else 2)
                    val pct = item.record.getDigit("battery-pct", -1)
                    mTitleTv.text = (if (lowBattery) "电量低" else "电量恢复") + if (pct > 0) " ($pct%)" else ""
                    return
                }
                if (item.record.extras.containsKey("battery-temp")) {
                    val temp = item.record.getDigit("battery-temp", -1)
                    if (temp != -1L) {
                        mIndicatorIv.setImageLevel(3)
                    }
                    val pct = item.record.getDigit("battery-pct", -1)
                    mTitleTv.text = "电池温度: " + (if (temp > 0) temp / 10f else "/") + "°C" + if (pct > 0) " ($pct%)" else ""
                    return
                }
                if (item.record.extras.containsKey("battery-pct")) {
                    val pct = item.record.getDigit("battery-pct", -1)
                    mTitleTv.text = "电量变化: " + (if (pct > 0) pct else "/") + "%"
                }
            }
        }

        companion object {
            @JvmField
            val COLOR_FG_MAIN: Int = R.color.FG_0

            @JvmField
            val COLOR_FG_SUB: Int = R.color.FG_2

            @JvmField
            val COLOR_FG_ALERT: Int = R.color.Red_80_CARE

            @JvmField
            protected val sTimeFormatRef: ThreadSafeReference<DateFormat> = object : ThreadSafeReference<DateFormat>() {
                override fun onCreate(): DateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            }
        }
    }

    companion object {
        const val VIEW_TYPE_HEADER: Int = 0
        const val VIEW_TYPE_EVENT_DUMP: Int = 1
        const val VIEW_TYPE_EVENT_LEVEL_1: Int = 2
        const val VIEW_TYPE_EVENT_LEVEL_2: Int = 3
        const val VIEW_TYPE_NO_DATA: Int = 4
        const val VIEW_TYPE_EVENT_SIMPLE: Int = 5
        const val VIEW_TYPE_EVENT_BATTERY: Int = 6
    }
}
