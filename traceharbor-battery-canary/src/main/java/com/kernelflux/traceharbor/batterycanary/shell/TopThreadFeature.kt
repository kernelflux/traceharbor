package com.kernelflux.traceharbor.batterycanary.shell

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.SparseArray
import androidx.annotation.Nullable
import androidx.core.util.Pair
import androidx.core.util.Supplier
import com.kernelflux.traceharbor.batterycanary.monitor.BatteryMonitorCallback.BatteryPrinter.Printer
import com.kernelflux.traceharbor.batterycanary.monitor.feature.AbsMonitorFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.CompositeMonitors
import com.kernelflux.traceharbor.batterycanary.monitor.feature.JiffiesMonitorFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.JiffiesMonitorFeature.JiffiesSnapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Delta
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Something like 'adb shell top -Hb -u <uid>'
 *
 * @author Kaede
 * @since 2022/2/22
 */
class TopThreadFeature : AbsMonitorFeature() {
    private var sStopShell = false

    @Nullable
    private var mTopTask: Runnable? = null
    private val mLastPidJiffiesHolder: SparseArray<JiffiesSnapshot> = SparseArray()

    fun interface ContinuousCallback {
        fun onGetDeltas(monitors: CompositeMonitors, windowMillis: Long): Boolean
    }

    override fun getTag(): String = TAG

    override fun weight(): Int = Int.MIN_VALUE

    fun topShell(seconds: Int) {
        sStopShell = false
        top(seconds, Supplier {
            val monitors = CompositeMonitors(core, CompositeMonitors.SCOPE_TOP_SHELL)
            monitors.metric(JiffiesMonitorFeature.UidJiffiesSnapshot::class.java)
            monitors
        }, object : ContinuousCallback {
            override fun onGetDeltas(monitors: CompositeMonitors, windowMillis: Long): Boolean {
                // Proc Load
                val deltaList: List<Delta<JiffiesSnapshot>> = monitors.getAllPidDeltaList()
                var allProcJiffies = 0L
                for (delta in deltaList) {
                    allProcJiffies += delta.dlt.totalJiffies.get()
                }
                val totalLoad = figureCupLoad(allProcJiffies, seconds * 100L)

                val printer = Printer()
                printer.writeTitle()
                printer.append("| TOP Thread\tpidNum=").append(deltaList.size)
                    .append("\tcpuLoad=").append(formatFloat(totalLoad, 1)).append("%")
                    .append("\n")

                // Thread Load
                for (delta in deltaList) {
                    if (delta.isValid()) {
                        printer.createSection("Proc")
                        printer.writeLine("pid", delta.dlt.pid.toString())
                        printer.writeLine("cmm", delta.dlt.name.toString())
                        printer.writeLine("load", formatFloat(figureCupLoad(delta.dlt.totalJiffies.get(), seconds * 100L), 1) + "%")
                        printer.createSubSection("Thread(" + delta.dlt.threadEntries.list.size + ")")
                        printer.writeLine("  TID\tLOAD \tSTATUS \tTHREAD_NAME \tJIFFY")
                        for (threadJiffies in delta.dlt.threadEntries.list) {
                            val entryJffies = threadJiffies.get()
                            printer.append("|   -> ")
                                .append(fixedColumn(threadJiffies.tid.toString(), 5)).append("\t")
                                .append(fixedColumn(formatFloat(figureCupLoad(entryJffies, seconds * 100L), 1), 4)).append("\t")
                                .append(if (threadJiffies.isNewAdded) "+" else "~").append("/").append(threadJiffies.stat).append("\t")
                                .append(fixedColumn(threadJiffies.name, 16)).append("\t")
                                .append(entryJffies).append("\t")
                                .append("\n")
                        }
                    }
                }

                printer.writeEnding()
                printer.dump()
                return sStopShell
            }
        })
    }

    fun stopShell() {
        sStopShell = true
        if (mTopTask != null) {
            core.getHandler().removeCallbacks(mTopTask!!)
            mTopTask = null
        }
        mLastPidJiffiesHolder.clear()
    }

    fun top(seconds: Int, supplier: Supplier<CompositeMonitors>, callback: ContinuousCallback) {
        val jiffiesFeat = core.getMonitorFeature(JiffiesMonitorFeature::class.java)
        if (jiffiesFeat == null) {
            return
        }
        val windowMillis = seconds * 1000L
        val lastMonitors = AtomicReference<CompositeMonitors?>(null)
        val thread = HandlerThread("traceharbor_top")
        thread.start()
        val handler = Handler(thread.looper)
        val action = object : Runnable {
            override fun run() {
                val monitors = lastMonitors.get()
                if (monitors == null) {
                    // Fist time
                    scheduleNext()
                } else {
                    lastMonitors.set(null)
                    monitors.finish()
                    val stop = callback.onGetDeltas(monitors, windowMillis)
                    if (stop) {
                        handler.looper.quit()
                    } else {
                        // Next
                        scheduleNext()
                    }
                }
            }

            private fun scheduleNext() {
                val monitors = supplier.get()
                monitors.start()
                lastMonitors.set(monitors)
                handler.postDelayed(this, windowMillis)
            }
        }
        handler.postDelayed(action, windowMillis)
    }

    companion object {
        private const val TAG = "TraceHarbor.battery.TopThread"

        @JvmStatic
        fun getAllPidList(context: Context): List<Int> {
            val list: MutableList<Int> = ArrayList()
            list.add(Process.myPid())
            val procList = getProcList(context)
            for (item in procList) {
                val pid = item.first
                if (pid != null && !list.contains(pid)) {
                    list.add(pid)
                }
            }
            return list
        }

        @JvmStatic
        fun getProcList(context: Context): List<Pair<Int, String>> {
            val list: MutableList<Pair<Int, String>> = ArrayList()
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
            if (am != null) {
                val processes = am.runningAppProcesses
                if (processes != null) {
                    for (item in processes) {
                        if (item.processName.contains(context.packageName)) {
                            list.add(Pair(item.pid, item.processName))
                        }
                    }
                }
            }
            return list
        }

        @JvmStatic
        fun figureCupLoad(jiffies: Long, cpuJiffies: Long): Float {
            return (jiffies / (cpuJiffies * 1f)) * 100
        }

        @JvmStatic
        fun formatFloat(input: Float, decimal: Int): String {
            return String.format("%.${decimal}f", input)
        }

        @JvmStatic
        fun fixedColumn(input: String?, width: Int): String {
            if (input != null && input.length >= width) {
                return input
            }
            return repeat(" ", width - (input?.length ?: 0)) + input
        }

        @Suppress("SameParameterValue")
        private fun repeat(symbol: String, count: Int): String {
            return CharArray(count).concatToString().replace("\u0000", symbol)
        }
    }
}

