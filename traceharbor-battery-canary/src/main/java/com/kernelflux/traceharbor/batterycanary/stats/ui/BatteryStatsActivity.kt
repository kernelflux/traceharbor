package com.kernelflux.traceharbor.batterycanary.stats.ui

import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kernelflux.traceharbor.batterycanary.BatteryCanary
import com.kernelflux.traceharbor.batterycanary.R
import com.kernelflux.traceharbor.batterycanary.stats.BatteryRecorder
import com.kernelflux.traceharbor.batterycanary.stats.BatteryStatsFeature

class BatteryStatsActivity : AppCompatActivity() {
    private lateinit var mStatsLoader: BatteryStatsLoader
    private var mCurrHeader: BatteryStatsAdapter.Item.HeaderItem? = null
    private var mEnd = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battery_stats)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        toolbar.title = "电量统计报告"
        setSupportActionBar(toolbar)

        val procTv: TextView = findViewById(R.id.tv_proc)
        val proc = BatteryRecorder.MMKVRecorder.getProcNameSuffix()
        procTv.text = ":$proc"

        BatteryCanary.getMonitorFeature(BatteryStatsFeature::class.java) { batteryStatsFeature ->
            procTv.setOnClickListener { v ->
                val menu = PopupMenu(v.context, procTv)
                menu.menu.add("Process :main")
                for (item in batteryStatsFeature.getProcSet()) {
                    if ("main" == item) {
                        continue
                    }
                    menu.menu.add("Process :$item")
                }
                menu.setOnMenuItemClickListener { item ->
                    val title = item.title.toString()
                    if (title.contains(":")) {
                        val procName = title.substring(title.lastIndexOf(":") + 1)
                        procTv.text = ":$procName"
                        mStatsLoader.reset(procName)
                        mStatsLoader.load()
                        updateHeader(0)
                    }
                    false
                }
                menu.show()
            }
        }

        val recyclerView: RecyclerView = findViewById(R.id.rv_battery_stats)
        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        val adapter = BatteryStatsAdapter()
        recyclerView.adapter = adapter
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                // update header
                val topPosition = layoutManager.findFirstVisibleItemPosition()
                updateHeader(topPosition)
            }
        })
        mStatsLoader = BatteryStatsLoader(adapter)
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                // update header
                val topPosition = layoutManager.findFirstVisibleItemPosition()
                updateHeader(topPosition)

                // load more
                if (layoutManager.findLastVisibleItemPosition() == mStatsLoader.getAdapter().getDataList().size - 1) {
                    if (!mStatsLoader.loadMore()) {
                        if (!mEnd) {
                            mEnd = true
                        }
                    }
                }
            }
        })

        // load today's data
        mStatsLoader.reset(proc)
        mStatsLoader.load()
    }

    private fun updateHeader(topPosition: Int) {
        val currHeader = mStatsLoader.getFirstHeader(topPosition)
        if (currHeader != null) {
            if (mCurrHeader == null || mCurrHeader !== currHeader) {
                mCurrHeader = currHeader
                val headerView: View = findViewById(R.id.header_pinned)
                headerView.visibility = View.VISIBLE
                val tv: TextView = headerView.findViewById(R.id.tv_title)
                tv.text = currHeader.date
            }
        }
    }
}

