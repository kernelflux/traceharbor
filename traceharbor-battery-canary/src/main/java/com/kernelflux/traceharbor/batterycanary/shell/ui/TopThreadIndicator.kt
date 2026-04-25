package com.kernelflux.traceharbor.batterycanary.shell.ui

import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.SparseBooleanArray
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RestrictTo
import androidx.annotation.UiThread
import androidx.core.util.Pair
import androidx.core.util.Supplier
import com.kernelflux.traceharbor.batterycanary.BatteryCanary
import com.kernelflux.traceharbor.batterycanary.R
import com.kernelflux.traceharbor.batterycanary.monitor.BatteryMonitorCallback
import com.kernelflux.traceharbor.batterycanary.monitor.BatteryMonitorCore
import com.kernelflux.traceharbor.batterycanary.monitor.feature.CompositeMonitors
import com.kernelflux.traceharbor.batterycanary.monitor.feature.CpuStatFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.DeviceStatMonitorFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.JiffiesMonitorFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.JiffiesMonitorFeature.JiffiesSnapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Delta
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Sampler.Result
import com.kernelflux.traceharbor.batterycanary.monitor.feature.TrafficMonitorFeature
import com.kernelflux.traceharbor.batterycanary.shell.TopThreadFeature
import com.kernelflux.traceharbor.batterycanary.stats.BatteryStatsFeature
import com.kernelflux.traceharbor.batterycanary.stats.HealthStatsFeature
import com.kernelflux.traceharbor.batterycanary.stats.HealthStatsHelper
import com.kernelflux.traceharbor.batterycanary.stats.ui.BatteryStatsActivity
import com.kernelflux.traceharbor.batterycanary.utils.BatteryCanaryUtil
import com.kernelflux.traceharbor.batterycanary.utils.CallStackCollector
import com.kernelflux.traceharbor.batterycanary.utils.Consumer
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.util.Arrays
import java.util.LinkedHashMap
import kotlin.math.abs

/**
 * See [TopThreadFeature].
 *
 * @author Kaede
 * @since 2022/2/23
 */
class TopThreadIndicator private constructor() {
    private val displayMetrics = DisplayMetrics()
    private val mUiHandler = Handler(Looper.getMainLooper())
    private val mRunningRef = SparseBooleanArray()

    @SuppressLint("RestrictedApi")
    private var mCurrProc: Pair<Int, String> = Pair(
        Process.myPid(),
        getProcSuffix(BatteryCanaryUtil.getProcessName()),
    )

    private var mRootView: View? = null
    private var mCore: BatteryMonitorCore? = null
    private var mCurrDelta: Delta<JiffiesSnapshot>? = null
    private var mShowPower = false
    private var mCollector = CallStackCollector()
    private var mDumpHandler = Consumer<Delta<JiffiesSnapshot>?> { delta ->
        if (delta == null) {
            mRootView?.let {
                Toast.makeText(it.context, "Skip dump: no data", Toast.LENGTH_SHORT).show()
            }
            return@Consumer
        }
        if (delta.dlt.pid != Process.myPid()) {
            mRootView?.let {
                Toast.makeText(it.context, "Skip dump: only support curr process now", Toast.LENGTH_SHORT).show()
            }
            return@Consumer
        }

        val tag = "TOP_THREAD_DUMP"
        val printer = BatteryMonitorCallback.BatteryPrinter.Printer()
        printer.writeTitle()
        printer.append("| $tag\n")
        if (delta.isValid()) {
            val extras: MutableMap<String, Any> = LinkedHashMap()
            val cpuLoad = TopThreadFeature.figureCupLoad(delta.dlt.totalJiffies.get(), delta.during / 10)
            extras["load"] = cpuLoad
            // Load
            printer.createSection("Proc")
            printer.writeLine("pid", delta.dlt.pid.toString())
            printer.writeLine("cmm", delta.dlt.name.toString())
            printer.writeLine("load", TopThreadFeature.formatFloat(cpuLoad, 1) + "%")
            printer.createSubSection("Thread(" + delta.dlt.threadEntries.list.size + ")")
            printer.writeLine("  TID\tLOAD \tSTATUS \tTHREAD_NAME \tJIFFY")
            for (threadJiffies in delta.dlt.threadEntries.list) {
                val entryJffies = threadJiffies.get()
                printer.append("|   -> ")
                    .append(TopThreadFeature.fixedColumn(threadJiffies.tid.toString(), 5)).append("\t")
                    .append(TopThreadFeature.fixedColumn(TopThreadFeature.formatFloat(TopThreadFeature.figureCupLoad(entryJffies, delta.during / 10), 1) + "%", 4)).append("\t")
                    .append(if (threadJiffies.isNewAdded) "+" else "~").append("/").append(threadJiffies.stat).append("\t")
                    .append(TopThreadFeature.fixedColumn(threadJiffies.name, 16)).append("\t")
                    .append(entryJffies).append("\t")
                    .append("\n")
            }
            // Thread Stack
            printer.createSection("Stacks")
            for (threadJiffies in delta.dlt.threadEntries.list) {
                val entryJffies = threadJiffies.get()
                val load = TopThreadFeature.figureCupLoad(entryJffies, delta.during / 10)
                if (load > 0) {
                    printer.createSubSection(threadJiffies.name + "(" + threadJiffies.tid + ")")
                    val stack = mCollector.collect(threadJiffies.tid)
                    extras["stack_" + threadJiffies.name + "(" + threadJiffies.tid + ")"] = stack
                    var idx = 0
                    for (line in stack.split("\n".toRegex()).dropLastWhile { it.isEmpty() }) {
                        printer.append(if (idx == 0) "|   -> " else "|      ").append(line).append("\n")
                        idx++
                    }
                } else {
                    break
                }
            }
            // Stats
            val core = mCore
            if (core != null) {
                val stats = core.getMonitorFeature(BatteryStatsFeature::class.java)
                if (stats != null) {
                    val event = "MATRIX_TOP_DUMP"
                    val eventId = delta.dlt.pid
                    stats.statsEvent(event, eventId, extras)
                }
            }
        } else {
            printer.createSection("Invalid data, ignore")
        }
        printer.writeEnding()
        printer.dump()
        mRootView?.let {
            Toast.makeText(it.context, "Dump finish, search TAG '$tag' for detail", Toast.LENGTH_LONG).show()
        }
    }

    private var mShowReportHandler = Runnable {
        val core = mCore
        if (core == null) {
            mRootView?.let {
                val tag = "TOP_THREAD_DUMP"
                Toast.makeText(it.context, "Search TAG '$tag' for detail report", Toast.LENGTH_LONG).show()
            }
            return@Runnable
        }
        // Show battery stats report
        val intent = Intent(core.getContext(), BatteryStatsActivity::class.java)
        if (core.getContext() !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        core.getContext().startActivity(intent)
    }

    fun attach(core: BatteryMonitorCore): TopThreadIndicator {
        mCore = core
        return this
    }

    fun attach(dumpHandler: Consumer<Delta<JiffiesSnapshot>?>): TopThreadIndicator {
        mDumpHandler = dumpHandler
        return this
    }

    fun attach(collector: CallStackCollector): TopThreadIndicator {
        mCollector = collector
        return this
    }

    fun attach(showReportHandler: Runnable): TopThreadIndicator {
        mShowReportHandler = showReportHandler
        return this
    }

    fun requestPermission(context: Context, reqCode: Int) {
        if (checkPermission(context)) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
            if (reqCode > 0 && context is Activity) {
                context.startActivityForResult(intent, reqCode)
            } else {
                context.startActivity(intent)
            }
        }
    }

    fun checkPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    @UiThread
    fun isShowing(): Boolean = mRootView != null

    @UiThread
    fun isRunning(): Boolean {
        val rootView = mRootView
        return rootView != null && mRunningRef.get(rootView.hashCode(), false)
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    @UiThread
    fun show(context: Context): Boolean {
        if (!checkPermission(context)) {
            return false
        }

        try {
            // 1. Window View
            val rootView: View? = LayoutInflater.from(context).inflate(R.layout.float_top_thread_container, null)
            mRootView = rootView
            if (rootView == null) {
                TraceHarborLog.w(TAG, "Can not load indicator view!")
                return false
            }
            val hashcode = rootView.hashCode()
            val windowManager = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            if (windowManager.defaultDisplay != null) {
                windowManager.defaultDisplay.getMetrics(displayMetrics)
                windowManager.defaultDisplay.getMetrics(metrics)
            }

            val layoutParam = WindowManager.LayoutParams()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutParam.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                layoutParam.type = WindowManager.LayoutParams.TYPE_PHONE
            }
            layoutParam.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            layoutParam.gravity = Gravity.START or Gravity.TOP
            // if (null != rootView) {
            //     layoutParam.x = metrics.widthPixels - rootView.getLayoutParams().width * 2;
            // }
            layoutParam.y = 0
            layoutParam.width = WindowManager.LayoutParams.WRAP_CONTENT
            layoutParam.height = WindowManager.LayoutParams.WRAP_CONTENT
            layoutParam.format = PixelFormat.TRANSPARENT

            windowManager.addView(rootView, layoutParam)

            // 2. Init views
            val tvPid = rootView.findViewById<TextView>(R.id.tv_curr_pid)
            tvPid.text = mCurrProc.first.toString()
            val tvProc = rootView.findViewById<TextView>(R.id.tv_proc)
            tvProc.text = mCurrProc.second

            val checkPower = rootView.findViewById<CheckBox>(R.id.check_power)
            mShowPower = checkPower.isChecked

            // init thread entryGroup
            val procEntryGroup = rootView.findViewById<LinearLayout>(R.id.layout_entry_proc_group)
            for (i in 0 until MAX_PROC_NUM - 1) {
                val entryItemView = LayoutInflater.from(procEntryGroup.context).inflate(R.layout.float_item_proc_entry, procEntryGroup, false)
                val layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                layoutParams.topMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, procEntryGroup.context.resources.displayMetrics).toInt()
                entryItemView.visibility = View.GONE
                procEntryGroup.addView(entryItemView, layoutParams)
            }
            // init thread entryGroup
            val threadEntryGroup = rootView.findViewById<LinearLayout>(R.id.layout_entry_group)
            for (i in 0 until MAX_THREAD_NUM - 1) {
                val entryItemView = LayoutInflater.from(threadEntryGroup.context).inflate(R.layout.float_item_thread_entry, threadEntryGroup, false)
                val layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                layoutParams.topMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, threadEntryGroup.context.resources.displayMetrics).toInt()
                entryItemView.visibility = View.GONE
                threadEntryGroup.addView(entryItemView, layoutParams)
            }
            // init power entryGroup
            val powerEntryGroup = rootView.findViewById<LinearLayout>(R.id.layout_entry_power_group)
            for (i in 0 until MAX_POWER_NUM - 1) {
                val entryItemView = LayoutInflater.from(powerEntryGroup.context).inflate(R.layout.float_item_power_entry, powerEntryGroup, false)
                val layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                layoutParams.topMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, powerEntryGroup.context.resources.displayMetrics).toInt()
                entryItemView.visibility = View.GONE
                powerEntryGroup.addView(entryItemView, layoutParams)
            }

            // 3. Drag
            val onTouchListener = object : View.OnTouchListener {
                var downX = 0f
                var downY = 0f
                var downOffsetX = 0
                var downOffsetY = 0

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    var consumed = false
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.x
                            downY = event.y
                            downOffsetX = layoutParam.x
                            downOffsetY = layoutParam.y
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val moveX = event.x
                            val moveY = event.y
                            layoutParam.x += ((moveX - downX) / 3).toInt()
                            layoutParam.y += ((moveY - downY) / 3).toInt()
                            windowManager.updateViewLayout(v, layoutParam)
                        }

                        MotionEvent.ACTION_UP -> {
                            val activeRoot = mRootView ?: return consumed
                            val holder = PropertyValuesHolder.ofInt(
                                "trans",
                                layoutParam.x,
                                if (layoutParam.x > (displayMetrics.widthPixels - activeRoot.width) / 2) displayMetrics.widthPixels - activeRoot.width else 0,
                            )
                            val animator = ValueAnimator.ofPropertyValuesHolder(holder)
                            animator.addUpdateListener { animation ->
                                val currentRoot = mRootView
                                if (currentRoot == null || currentRoot.hashCode() != hashcode) {
                                    return@addUpdateListener
                                }
                                layoutParam.x = animation.getAnimatedValue("trans") as Int
                                windowManager.updateViewLayout(v, layoutParam)
                            }
                            animator.interpolator = AccelerateInterpolator()
                            animator.setDuration(180).start()

                            val upOffsetX = layoutParam.x
                            val upOffsetY = layoutParam.y
                            if (abs(upOffsetX - downOffsetX) > 20 || abs(upOffsetY - downOffsetY) > 20) {
                                consumed = true
                            }
                        }
                    }
                    return consumed
                }
            }
            rootView.setOnTouchListener(onTouchListener)

            // 4. Listener
            val listener = View.OnClickListener { v ->
                val currentRoot = mRootView ?: return@OnClickListener
                if (v.id == R.id.layout_dump) {
                    // Dump all threads
                    mDumpHandler.accept(mCurrDelta)
                    return@OnClickListener
                }
                if (v.id == R.id.layout_proc) {
                    // Choose proc
                    val procTextView = currentRoot.findViewById<TextView>(R.id.tv_proc)
                    val menu = PopupMenu(v.context, procTextView)
                    val procList = TopThreadFeature.getProcList(v.context)
                    for (item in procList) {
                        menu.menu.add("Process :" + getProcSuffix(item.second!!))
                    }
                    menu.setOnMenuItemClickListener { item: MenuItem ->
                        val title = item.title.toString()
                        if (title.contains(":")) {
                            val proc = title.substring(title.lastIndexOf(":") + 1)
                            for (procItem in procList) {
                                if (title == "Process :" + getProcSuffix(procItem.second!!)) {
                                    mCurrProc = procItem
                                    procTextView.text = ":$proc"
                                    tvPid.text = mCurrProc.first.toString()
                                }
                            }
                        }
                        false
                    }
                    menu.show()
                    return@OnClickListener
                }
                if (v.id == R.id.iv_logo) {
                    // Show dump report
                    mShowReportHandler.run()
                    return@OnClickListener
                }
                if (v.id == R.id.tv_minify) {
                    // Minify
                    currentRoot.findViewById<View>(R.id.layout_top).visibility = View.GONE
                    currentRoot.findViewById<View>(R.id.iv_logo_minify).visibility = View.VISIBLE
                    return@OnClickListener
                }
                if (v.id == R.id.layout_check_power) {
                    val view = v.findViewById<CheckBox>(R.id.check_power)
                    view.isChecked = !view.isChecked
                    mShowPower = view.isChecked
                    return@OnClickListener
                }
                if (v === currentRoot && currentRoot.findViewById<View>(R.id.layout_top).visibility == View.GONE) {
                    // Minify LOGO
                    val anchorView = currentRoot.findViewById<View>(R.id.iv_logo_minify)
                    val menu = PopupMenu(v.context, anchorView)
                    menu.menu.add("Expand")
                    menu.menu.add("Close")
                    menu.setOnMenuItemClickListener { item: MenuItem ->
                        when (item.title.toString()) {
                            "Expand" -> {
                                currentRoot.findViewById<View>(R.id.layout_top).visibility = View.VISIBLE
                                currentRoot.findViewById<View>(R.id.iv_logo_minify).visibility = View.GONE
                            }

                            "Close" -> {
                                mUiHandler.postDelayed({ dismiss() }, 200L)
                            }

                            else -> {
                            }
                        }
                        false
                    }
                    menu.show()
                }
            }

            rootView.findViewById<View>(R.id.layout_proc).setOnClickListener(listener)
            rootView.findViewById<View>(R.id.layout_dump).setOnClickListener(listener)
            rootView.findViewById<View>(R.id.iv_logo).setOnClickListener(listener)
            rootView.findViewById<View>(R.id.tv_minify).setOnClickListener(listener)
            rootView.findViewById<View>(R.id.layout_check_power).setOnClickListener(listener)
            rootView.setOnClickListener(listener)
            return true
        } catch (e: Exception) {
            TraceHarborLog.w(TAG, "Create float view failed:" + e.message)
            return false
        }
    }

    fun start(seconds: Int) {
        val rootView = mRootView
        if (rootView == null) {
            TraceHarborLog.w(TAG, "Call #prepare first to show the indicator")
            return
        }
        if (isRunning()) {
            TraceHarborLog.w(TAG, "Already started!")
            return
        }
        val hashcode = rootView.hashCode()
        mRunningRef.clear()
        mRunningRef.put(hashcode, true)
        BatteryCanary.getMonitorFeature(TopThreadFeature::class.java, Consumer { topThreadFeat ->
            topThreadFeat.top(
                seconds,
                Supplier {
                    val monitors = CompositeMonitors(mCore, CompositeMonitors.SCOPE_TOP_INDICATOR)
                    monitors.metric(JiffiesMonitorFeature.UidJiffiesSnapshot::class.java)
                    monitors.metric(CpuStatFeature.CpuStateSnapshot::class.java)
                    monitors.metric(CpuStatFeature.UidCpuStateSnapshot::class.java)
                    monitors.metric(HealthStatsFeature.HealthStatsSnapshot::class.java)
                    monitors.metric(TrafficMonitorFeature.RadioStatSnapshot::class.java)
                    monitors.sample(DeviceStatMonitorFeature.CpuFreqSnapshot::class.java, 500L)
                    monitors.sample(DeviceStatMonitorFeature.BatteryCurrentSnapshot::class.java, 500L)
                    monitors.sample(TrafficMonitorFeature.RadioBpsSnapshot::class.java, 500L)
                    monitors
                },
                TopThreadFeature.ContinuousCallback { monitors, _ ->
                    refresh(monitors)
                    mRootView == null || !mRunningRef.get(hashcode, false)
                },
            )
        })
    }

    fun stop() {
        mRootView?.let {
            mRunningRef.put(it.hashCode(), false)
        }
    }

    @UiThread
    fun dismiss() {
        val rootView = mRootView
        if (rootView != null) {
            val windowManager = rootView.context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(rootView)
            mRootView = null
        }
    }

    fun getDumpHandler(): Consumer<Delta<JiffiesSnapshot>?> = mDumpHandler

    private fun setTextAlertColor(tv: TextView, level: Int) {
        tv.setTextColor(
            tv.resources.getColor(
                if (level == 2) R.color.Red_80 else if (level == 1) R.color.Orange_80 else R.color.FG_2,
            ),
        )
    }

    @SuppressLint("SetTextI18n", "RestrictedApi")
    private fun refresh(monitors: CompositeMonitors) {
        mUiHandler.post {
            val rootView = mRootView ?: return@post
            val appStats = monitors.getAppStats() ?: return@post

            val deltaList = monitors.getAllPidDeltaList()
            val battTemp = BatteryCanaryUtil.getBatteryTemperatureImmediately(rootView.context)
            var tvBattTemp = rootView.findViewById<TextView>(R.id.tv_header_left)
            tvBattTemp.text = (if (battTemp > 0) battTemp / 10f else "/").toString() + "°C"
            setTextAlertColor(tvBattTemp, if (battTemp >= 350) 2 else if (battTemp >= 300) 1 else 0)
            tvBattTemp = rootView.findViewById(R.id.tv_temp_minify)
            tvBattTemp.text = (if (battTemp > 0) battTemp / 10f else "/").toString() + "°C"
            setTextAlertColor(tvBattTemp, if (battTemp >= 350) 2 else if (battTemp >= 300) 1 else 0)

            // CpuLoad EntryList
            val procEntryGroup = rootView.findViewById<LinearLayout>(R.id.layout_entry_proc_group)
            for (i in 0 until procEntryGroup.childCount) {
                procEntryGroup.getChildAt(i).visibility = View.GONE
            }
            val threadEntryGroup = rootView.findViewById<LinearLayout>(R.id.layout_entry_group)
            for (i in 0 until threadEntryGroup.childCount) {
                threadEntryGroup.getChildAt(i).visibility = View.GONE
            }

            val procList = TopThreadFeature.getProcList(rootView.context)
            var totalCpuLoad = 0f
            for (i in deltaList.indices) {
                val delta = deltaList[i]
                if (delta.isValid()) {
                    // Proc
                    val pid = delta.dlt.pid
                    var name = delta.dlt.name
                    for (item in procList) {
                        if (item.first == pid) {
                            name = getProcSuffix(item.second!!)
                        }
                    }

                    if (pid == mCurrProc.first) {
                        // Curr Selected Proc's threads
                        name += " <-"
                        var idx = 0
                        for (threadJiffies in delta.dlt.threadEntries.list) {
                            val entryJffies = threadJiffies.get()
                            val tid = threadJiffies.tid
                            val threadName = threadJiffies.name
                            val status = (if (threadJiffies.isNewAdded) "+" else "~") + threadJiffies.stat
                            val threadLoad = TopThreadFeature.figureCupLoad(entryJffies, delta.during / 10L)

                            val threadItemView = threadEntryGroup.getChildAt(idx)
                            if (!mShowPower) {
                                threadItemView.visibility = View.VISIBLE
                            }
                            val tvName = threadItemView.findViewById<TextView>(R.id.tv_name)
                            val tvTid = threadItemView.findViewById<TextView>(R.id.tv_tid)
                            val tvStatus = threadItemView.findViewById<TextView>(R.id.tv_status)
                            val tvLoad = threadItemView.findViewById<TextView>(R.id.tv_load)
                            tvName.text = threadName
                            tvTid.text = tid.toString()
                            tvStatus.text = status
                            tvLoad.text = TopThreadFeature.formatFloat(threadLoad, 1) + "%"

                            val alertLevel = if (threadJiffies.isNewAdded) 0 else if (threadLoad >= 50) 2 else if (threadLoad >= 10) 1 else 0
                            setTextAlertColor(tvName, alertLevel)
                            setTextAlertColor(tvTid, alertLevel)
                            setTextAlertColor(tvStatus, alertLevel)
                            setTextAlertColor(tvLoad, alertLevel)

                            idx++
                            if (idx >= MAX_THREAD_NUM) {
                                break
                            }
                        }
                        mCurrDelta = delta
                    }

                    val procLoad = TopThreadFeature.figureCupLoad(delta.dlt.totalJiffies.get(), delta.during / 10L)
                    totalCpuLoad += procLoad
                    val procItemView = procEntryGroup.getChildAt(i)
                    procItemView.visibility = View.VISIBLE
                    val tvProcName = procItemView.findViewById<TextView>(R.id.tv_name)
                    val tvProcPid = procItemView.findViewById<TextView>(R.id.tv_pid)
                    val tvProcLoad = procItemView.findViewById<TextView>(R.id.tv_load)
                    tvProcName.text = ":$name"
                    tvProcPid.text = pid.toString()
                    tvProcLoad.text = TopThreadFeature.formatFloat(procLoad, 1) + "%"
                }
            }

            val totalLoad = TopThreadFeature.formatFloat(totalCpuLoad, 1) + "%"
            var tvLoad = rootView.findViewById<TextView>(R.id.tv_header_right)
            tvLoad.text = totalLoad
            tvLoad = rootView.findViewById(R.id.tv_load_minify)
            tvLoad.text = totalLoad

            // Power EntryList
            val powerEntryGroup = rootView.findViewById<LinearLayout>(R.id.layout_entry_power_group)
            for (i in 0 until powerEntryGroup.childCount) {
                powerEntryGroup.getChildAt(i).visibility = View.GONE
            }
            if (mShowPower) {
                val powerMap: MutableMap<String, Pair<String, Double?>> = LinkedHashMap()
                val result: Result? = monitors.getSamplingResult(DeviceStatMonitorFeature.BatteryCurrentSnapshot::class.java)
                if (result != null) {
                    val power = result.sampleAvg / -1000 * (appStats.duringMillis * 1f / BatteryCanaryUtil.ONE_HOR)
                    val deltaPh = power * BatteryCanaryUtil.ONE_HOR / appStats.duringMillis
                    powerMap["currency"] = Pair("mAh", deltaPh / 1000)
                } else {
                    powerMap["currency"] = Pair("mAh", null)
                }
                val healthStatsDelta = monitors.getDelta(HealthStatsFeature.HealthStatsSnapshot::class.java)
                if (healthStatsDelta != null) {
                    powerMap["total"] = Pair("mAh", healthStatsDelta.dlt.getTotalPower())
                    powerMap["cpu"] = Pair("mAh", healthStatsDelta.dlt.cpuPower.get())
                    run {
                        val modes = Arrays.asList("JiffyUid")
                        for (mode in modes) {
                            val powers = healthStatsDelta.dlt.extras[mode]
                            if (powers is Map<*, *>) {
                                // tuning cpu powers
                                for ((keyAny, valAny) in powers) {
                                    val key = keyAny.toString()
                                    if (key.startsWith("power-cpu") && valAny is Double) {
                                        powerMap[key.replace("power-cpu", " - cpu")] = Pair("mAh", valAny)
                                    }
                                }
                            }
                        }
                    }
                    powerMap["wakelocks"] = Pair("mAh", healthStatsDelta.dlt.wakelocksPower.get())
                    powerMap["mobile"] = Pair("mAh", healthStatsDelta.dlt.mobilePower.get())
                    run {
                        monitors.getDelta(TrafficMonitorFeature.RadioStatSnapshot::class.java, Consumer { delta ->
                            if (healthStatsDelta.dlt.extras.containsKey("power-mobile-statByte")) {
                                val value = healthStatsDelta.dlt.extras["power-mobile-statByte"]
                                if (value is Double) {
                                    powerMap[" - mobile-PowerBytes"] = Pair("mAh", value)
                                    powerMap["   - mobile-RxBytes"] = Pair("byte", delta.dlt.mobileRxBytes.get().toDouble())
                                    powerMap["   - mobile-TxBytes"] = Pair("byte", delta.dlt.mobileTxBytes.get().toDouble())
                                }
                            }
                        })
                    }
                    powerMap["wifi"] = Pair("mAh", healthStatsDelta.dlt.wifiPower.get())
                    run {
                        monitors.getDelta(TrafficMonitorFeature.RadioStatSnapshot::class.java, Consumer { delta ->
                            if (healthStatsDelta.dlt.extras.containsKey("power-wifi-statByte")) {
                                val value = healthStatsDelta.dlt.extras["power-wifi-statByte"]
                                if (value is Double) {
                                    powerMap[" - wifi-PowerBytes"] = Pair("mAh", value)
                                    powerMap["   - wifi-RxBytes"] = Pair("byte", delta.dlt.wifiRxBytes.get().toDouble())
                                    powerMap["   - wifi-TxBytes"] = Pair("byte", delta.dlt.wifiTxBytes.get().toDouble())
                                }
                            }
                            if (healthStatsDelta.dlt.extras.containsKey("power-wifi-statPacket")) {
                                val value = healthStatsDelta.dlt.extras["power-wifi-statPacket"]
                                if (value is Double) {
                                    powerMap[" - wifi-PowerPackets"] = Pair("mAh", value)
                                    powerMap["   - wifi-RxPackets"] = Pair("packet", delta.dlt.wifiRxPackets.get().toDouble())
                                    powerMap["   - wifi-TxPackets"] = Pair("packet", delta.dlt.wifiTxPackets.get().toDouble())
                                }
                            }
                        })
                    }
                    powerMap["blueTooth"] = Pair("mAh", healthStatsDelta.dlt.blueToothPower.get())
                    powerMap["gps"] = Pair("mAh", healthStatsDelta.dlt.gpsPower.get())
                    powerMap["sensors"] = Pair("mAh", healthStatsDelta.dlt.sensorsPower.get())
                    powerMap["camera"] = Pair("mAh", healthStatsDelta.dlt.cameraPower.get())
                    powerMap["flashLight"] = Pair("mAh", healthStatsDelta.dlt.flashLightPower.get())
                    powerMap["audio"] = Pair("mAh", healthStatsDelta.dlt.audioPower.get())
                    powerMap["video"] = Pair("mAh", healthStatsDelta.dlt.videoPower.get())
                    powerMap["screen"] = Pair("mAh", healthStatsDelta.dlt.screenPower.get())
                    // powerMap["systemService"] = healthStatsDelta.dlt.systemServicePower.get()
                    powerMap["idle"] = Pair("mAh", healthStatsDelta.dlt.idlePower.get())
                }
                // for (Iterator<Map.Entry<String, Double>> iterator = powerMap.entrySet().iterator(); iterator.hasNext(); ) {
                //     Map.Entry<String, Double> item = iterator.next();
                //     if (item.getValue() == 0d) {
                //         iterator.remove();
                //     }
                // }
                var idx = 0
                for ((module, pair) in powerMap) {
                    var unit = ""
                    var value: Double? = null
                    val pairUnit = pair.first
                    val pairValue = pair.second
                    if (pairUnit != null) {
                        if (pairUnit == "mAh") {
                            unit = ""
                            if (pairValue != null) {
                                value = pairValue * BatteryCanaryUtil.ONE_HOR / appStats.duringMillis
                            }
                        } else {
                            unit = pairUnit
                            if (pairValue != null) {
                                value = pairValue
                            }
                        }
                    }
                    val threadItemView = powerEntryGroup.getChildAt(idx)
                    threadItemView.visibility = View.VISIBLE
                    val tvName = threadItemView.findViewById<TextView>(R.id.tv_name)
                    val tvUnit = threadItemView.findViewById<TextView>(R.id.tv_unit)
                    val tvPower = threadItemView.findViewById<TextView>(R.id.tv_power)
                    tvName.text = module
                    tvUnit.text = unit
                    if (value == null) {
                        tvPower.text = "NULL"
                    } else {
                        tvPower.text = HealthStatsHelper.round(value, 5).toString()
                    }
                    idx++
                    if (idx >= MAX_POWER_NUM) {
                        break
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.TopThreadIndicator"
        private const val MAX_PROC_NUM = 10
        private const val MAX_THREAD_NUM = 10
        private const val MAX_POWER_NUM = 30

        @SuppressLint("StaticFieldLeak")
        private val sInstance = TopThreadIndicator()

        @JvmStatic
        fun instance(): TopThreadIndicator = sInstance

        @JvmStatic
        @RestrictTo(RestrictTo.Scope.LIBRARY)
        fun getProcSuffix(input: String): String {
            var proc = "main"
            if (input.contains(":")) {
                proc = input.substring(input.lastIndexOf(":") + 1)
            }
            return proc
        }
    }
}
