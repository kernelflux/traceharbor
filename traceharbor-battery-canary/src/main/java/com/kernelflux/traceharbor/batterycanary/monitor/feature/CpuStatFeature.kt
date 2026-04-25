package com.kernelflux.traceharbor.batterycanary.monitor.feature

import android.os.Parcel
import android.os.Parcelable
import android.os.Process
import androidx.annotation.WorkerThread
import androidx.core.util.Pair
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Delta
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Differ
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Entry.DigitEntry
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Entry.ListEntry
import com.kernelflux.traceharbor.batterycanary.shell.TopThreadFeature
import com.kernelflux.traceharbor.batterycanary.shell.ui.TopThreadIndicator
import com.kernelflux.traceharbor.batterycanary.utils.BatteryCanaryUtil
import com.kernelflux.traceharbor.batterycanary.utils.BatteryCanaryUtil.JIFFY_MILLIS
import com.kernelflux.traceharbor.batterycanary.utils.BatteryCanaryUtil.ONE_HOR
import com.kernelflux.traceharbor.batterycanary.utils.KernelCpuSpeedReader
import com.kernelflux.traceharbor.batterycanary.utils.KernelCpuUidFreqTimeReader
import com.kernelflux.traceharbor.batterycanary.utils.PowerProfile
import com.kernelflux.traceharbor.batterycanary.utils.ProcStatUtil
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.io.IOException
import java.util.Collections

/**
 * @author Kaede
 * @since 2021/9/10
 */
@Suppress("SpellCheckingInspection", "MemberVisibilityCanBePrivate")
open class CpuStatFeature : AbsTaskMonitorFeature() {
    private var mPowerProfile: PowerProfile? = null

    protected override fun getTag(): String = TAG

    override fun weight(): Int = 0

    override fun onTurnOn() {
        super.onTurnOn()
        tryInitPowerProfile()
    }

    override fun onForeground(isForeground: Boolean) {
        super.onForeground(isForeground)
        if (!isForeground && mPowerProfile == null) {
            mCore.getHandler().post { tryInitPowerProfile() }
        }
    }

    @WorkerThread
    private fun tryInitPowerProfile() {
        if (mPowerProfile != null) {
            return
        }
        synchronized(this) {
            if (mPowerProfile != null) {
                return
            }
            try {
                val powerProfile = PowerProfile.init(mCore.getContext())
                for (i in 0 until powerProfile.cpuCoreNum) {
                    val numSpeedSteps = powerProfile.getNumSpeedStepsInCpuCluster(
                        powerProfile.getClusterByCpuNum(i)
                    )
                    KernelCpuSpeedReader(i, numSpeedSteps).smoke()
                }

                val clusterSteps = IntArray(powerProfile.getNumCpuClusters())
                for (i in clusterSteps.indices) {
                    clusterSteps[i] = powerProfile.getNumSpeedStepsInCpuCluster(i)
                }
                KernelCpuUidFreqTimeReader(Process.myPid(), clusterSteps).smoke()
                mPowerProfile = powerProfile
            } catch (e: IOException) {
                TraceHarborLog.w(TAG, "Init cpuStat failed: " + e.message)
                mPowerProfile = null
            }
        }
    }

    val isSupported: Boolean
        get() = mPowerProfile != null

    val powerProfile: PowerProfile?
        get() = mPowerProfile

    fun currentCpuStateSnapshot(): CpuStateSnapshot {
        return currentCpuStateSnapshot(Process.myPid())
    }

    fun currentCpuStateSnapshot(pid: Int): CpuStateSnapshot {
        val snapshot = CpuStateSnapshot()
        try {
            if (!isSupported) {
                throw IOException("PowerProfile not supported")
            }
            synchronized(this) {
                val powerProfile = mPowerProfile ?: throw IOException("PowerProfile not supported")

                if (pid == Process.myPid()) {
                    val cpuCoreStates: MutableList<ListEntry<DigitEntry<Long>>> = ArrayList()
                    snapshot.cpuCoreStates = cpuCoreStates
                    for (i in 0 until powerProfile.cpuCoreNum) {
                        val numSpeedSteps = powerProfile.getNumSpeedStepsInCpuCluster(
                            powerProfile.getClusterByCpuNum(i)
                        )
                        val cpuStepJiffiesReader = KernelCpuSpeedReader(i, numSpeedSteps)
                        val cpuCoreStepJiffies = cpuStepJiffiesReader.readAbsolute()
                        cpuCoreStates.add(ListEntry.ofDigits(cpuCoreStepJiffies))
                    }
                }

                val clusterSteps = IntArray(powerProfile.getNumCpuClusters())
                for (i in clusterSteps.indices) {
                    clusterSteps[i] = powerProfile.getNumSpeedStepsInCpuCluster(i)
                }
                val procStepJiffiesReader = KernelCpuUidFreqTimeReader(pid, clusterSteps)
                val procStepJiffies = procStepJiffiesReader.readAbsolute()
                val procCpuCoreStates: MutableList<ListEntry<DigitEntry<Long>>> = ArrayList()
                snapshot.procCpuCoreStates = procCpuCoreStates
                for (item in procStepJiffies) {
                    procCpuCoreStates.add(ListEntry.ofDigits(item))
                }
            }
        } catch (e: Exception) {
            TraceHarborLog.w(TAG, "Read cpu core state fail: " + e.message)
            snapshot.setValid(false)
        }
        return snapshot
    }

    fun currentUidCpuStateSnapshot(): UidCpuStateSnapshot {
        val curr = UidCpuStateSnapshot()
        try {
            val procList: List<Pair<Int, String>> = TopThreadFeature.getProcList(mCore.getContext())
            val pidCurrCupSateList: MutableList<CpuStateSnapshot> = ArrayList(procList.size)
            curr.pidCurrCupSateList = pidCurrCupSateList

            for (item in procList) {
                val pid = item.first!!
                val procName = java.lang.String.valueOf(item.second)
                var snapshot: CpuStateSnapshot? = null

                if (pid == Process.myPid()) {
                    snapshot = currentCpuStateSnapshot()
                } else {
                    if (ProcStatUtil.exists(pid)) {
                        snapshot = currentCpuStateSnapshot(pid)
                    }
                    if (snapshot != null && !snapshot.isValid()) {
                        val ipcCpuStatCollector = mCore.getConfig().ipcCpuStatCollector
                        if (ipcCpuStatCollector != null) {
                            val remote = ipcCpuStatCollector.apply(item)
                            if (remote != null) {
                                snapshot = UidCpuStateSnapshot.IpcCpuStat.toLocal(remote)
                            }
                        }
                    }
                }
                if (snapshot != null) {
                    snapshot.pid = pid
                    snapshot.name = TopThreadIndicator.getProcSuffix(procName)
                    pidCurrCupSateList.add(snapshot)
                }
            }
        } catch (e: Exception) {
            TraceHarborLog.w(TAG, "get curr UidCpuStatSnapshot failed: " + e.message)
            curr.setValid(false)
        }
        return curr
    }

    class CpuStateSnapshot : Snapshot<CpuStateSnapshot>() {
        /*
         * cpuCoreStates
         * [
         *     [step1Jiffies, step2Jiffies ...], // CpuCore 1
         *     [step1Jiffies, step2Jiffies ...], // CpuCore 2
         *                                          ...
         * ]
         *
         * procCpuCoreStates
         * [
         *     [step1Jiffies, step2Jiffies ...], // Cluster 1
         *     [step1Jiffies, step2Jiffies ...], // Cluster 2
         *                                          ...
         * ]
         */
        @JvmField
        var cpuCoreStates: List<ListEntry<DigitEntry<Long>>> = Collections.emptyList()

        @JvmField
        var procCpuCoreStates: List<ListEntry<DigitEntry<Long>>> = Collections.emptyList()

        @JvmField
        var pid: Int = Process.myPid()

        @JvmField
        var name: String = BatteryCanaryUtil.getProcessName()

        fun totalCpuJiffies(): Long {
            var sum = 0L
            for (cpuCoreState in cpuCoreStates) {
                for (item in cpuCoreState.list) {
                    sum += item.value
                }
            }
            return sum
        }

        fun totalProcCpuJiffies(): Long {
            var sum = 0L
            for (cpuCoreState in procCpuCoreStates) {
                for (item in cpuCoreState.list) {
                    sum += item.value
                }
            }
            return sum
        }

        fun configureCpuSip(powerProfile: PowerProfile): Double {
            if (!powerProfile.isSupported) {
                return 0.0
            }
            var sipSum = 0.0
            for (i in cpuCoreStates.indices) {
                val stepJiffies = cpuCoreStates[i].list
                for (j in stepJiffies.indices) {
                    val jiffy = stepJiffies[j].get().toDouble()
                    val cluster = powerProfile.getClusterByCpuNum(i)
                    val power = powerProfile.getAveragePowerForCpuCore(cluster, j)
                    val sip = power * (jiffy * JIFFY_MILLIS / ONE_HOR)
                    sipSum += sip
                }
            }
            return sipSum
        }

        fun configureProcSip(powerProfile: PowerProfile, procJiffies: Long): Double {
            if (!powerProfile.isSupported) {
                return 0.0
            }
            var jiffySum = 0L
            for (stepJiffies in procCpuCoreStates) {
                for (item in stepJiffies.list) {
                    jiffySum += item.get()
                }
            }
            var sipSum = 0.0
            for (i in procCpuCoreStates.indices) {
                val stepJiffies = procCpuCoreStates[i].list
                for (j in stepJiffies.indices) {
                    val jiffy = stepJiffies[j].get()
                    val figuredJiffies = jiffy.toDouble() / jiffySum * procJiffies
                    val power = powerProfile.getAveragePowerForCpuCore(i, j)
                    val sip = power * (figuredJiffies * JIFFY_MILLIS / ONE_HOR)
                    sipSum += sip
                }
            }
            return sipSum
        }

        override fun diff(bgn: CpuStateSnapshot): Delta<CpuStateSnapshot> {
            return object : Delta<CpuStateSnapshot>(bgn, this) {
                override fun computeDelta(): CpuStateSnapshot {
                    val delta = CpuStateSnapshot()
                    delta.pid = end.pid
                    delta.name = end.name
                    if (bgn.cpuCoreStates.size != end.cpuCoreStates.size) {
                        delta.setValid(false)
                    } else {
                        val cpuCoreStates: MutableList<ListEntry<DigitEntry<Long>>> = ArrayList()
                        delta.cpuCoreStates = cpuCoreStates
                        for (i in end.cpuCoreStates.indices) {
                            cpuCoreStates.add(
                                Differ.ListDiffer.globalDiff(
                                    bgn.cpuCoreStates[i],
                                    end.cpuCoreStates[i]
                                )
                            )
                        }
                        val procCpuCoreStates: MutableList<ListEntry<DigitEntry<Long>>> = ArrayList()
                        delta.procCpuCoreStates = procCpuCoreStates
                        for (i in end.procCpuCoreStates.indices) {
                            procCpuCoreStates.add(
                                Differ.ListDiffer.globalDiff(
                                    bgn.procCpuCoreStates[i],
                                    end.procCpuCoreStates[i]
                                )
                            )
                        }
                    }
                    return delta
                }
            }
        }
    }

    class UidCpuStateSnapshot : Snapshot<UidCpuStateSnapshot>() {
        @JvmField
        var pidCurrCupSateList: List<CpuStateSnapshot> = Collections.emptyList()

        @JvmField
        var pidDeltaCpuSateList: List<Delta<CpuStateSnapshot>> = Collections.emptyList()

        override fun diff(bgn: UidCpuStateSnapshot): Delta<UidCpuStateSnapshot> {
            return object : Delta<UidCpuStateSnapshot>(bgn, this) {
                override fun computeDelta(): UidCpuStateSnapshot {
                    val delta = UidCpuStateSnapshot()
                    if (end.pidCurrCupSateList.isNotEmpty()) {
                        val pidDeltaCpuSateList: MutableList<Delta<CpuStateSnapshot>> = ArrayList()
                        delta.pidDeltaCpuSateList = pidDeltaCpuSateList
                        for (endSnapshot in end.pidCurrCupSateList) {
                            var last: CpuStateSnapshot? = null
                            for (bgnSnapshot in bgn.pidCurrCupSateList) {
                                if (bgnSnapshot.pid == endSnapshot.pid) {
                                    last = bgnSnapshot
                                    break
                                }
                            }
                            if (last == null) {
                                val empty = CpuStateSnapshot()
                                empty.pid = endSnapshot.pid
                                empty.name = endSnapshot.name
                                val procCpuCoreStates: MutableList<ListEntry<DigitEntry<Long>>> =
                                    ArrayList(endSnapshot.procCpuCoreStates.size)
                                empty.procCpuCoreStates = procCpuCoreStates
                                for (item in endSnapshot.procCpuCoreStates) {
                                    val emptyStats = LongArray(item.list.size)
                                    procCpuCoreStates.add(ListEntry.ofDigits(emptyStats))
                                }
                                last = empty
                            }
                            val deltaPidCpuState = endSnapshot.diff(last)
                            pidDeltaCpuSateList.add(deltaPidCpuState)
                        }
                    }
                    return delta
                }
            }
        }

        class IpcCpuStat {
            class RemoteStat : Parcelable {
                @JvmField
                var procCpuCoreStates: List<LongArray> = Collections.emptyList()

                constructor()

                protected constructor(parcel: Parcel) {
                    val size = parcel.readInt()
                    val states: MutableList<LongArray> = ArrayList(size)
                    procCpuCoreStates = states
                    for (i in 0 until size) {
                        states.add(parcel.createLongArray() ?: LongArray(0))
                    }
                }

                override fun writeToParcel(dest: Parcel, flags: Int) {
                    val numOfArrays = procCpuCoreStates.size
                    dest.writeInt(numOfArrays)
                    for (i in 0 until numOfArrays) {
                        dest.writeLongArray(procCpuCoreStates[i])
                    }
                }

                override fun describeContents(): Int = 0

                companion object {
                    @JvmField
                    val CREATOR: Parcelable.Creator<RemoteStat> = object : Parcelable.Creator<RemoteStat> {
                        override fun createFromParcel(parcel: Parcel): RemoteStat {
                            return RemoteStat(parcel)
                        }

                        override fun newArray(size: Int): Array<RemoteStat?> {
                            return arrayOfNulls(size)
                        }
                    }
                }
            }

            companion object {
                @JvmStatic
                fun toIpc(local: CpuStateSnapshot): RemoteStat {
                    val remote = RemoteStat()
                    val procCpuCoreStates: MutableList<LongArray> = ArrayList(local.procCpuCoreStates.size)
                    remote.procCpuCoreStates = procCpuCoreStates
                    for (item in local.procCpuCoreStates) {
                        val stats = LongArray(item.list.size)
                        for (i in stats.indices) {
                            stats[i] = item.list[i].get()
                        }
                        procCpuCoreStates.add(stats)
                    }
                    return remote
                }

                @JvmStatic
                fun toLocal(remote: RemoteStat): CpuStateSnapshot {
                    val local = CpuStateSnapshot()
                    val procCpuCoreStates: MutableList<ListEntry<DigitEntry<Long>>> =
                        ArrayList(remote.procCpuCoreStates.size)
                    local.procCpuCoreStates = procCpuCoreStates
                    for (item in remote.procCpuCoreStates) {
                        procCpuCoreStates.add(ListEntry.ofDigits(item))
                    }
                    return local
                }
            }
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.battery.CpuStatFeature"
    }
}
