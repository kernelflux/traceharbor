package com.kernelflux.traceharbor.batterycanary.utils

import android.text.TextUtils
import androidx.annotation.RestrictTo
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.util.ArrayList

/**
 * Reads /proc/uid_time_in_state which has the format:
 *
 * uid: [freq1] [freq2] [freq3] ...
 * [uid1]: [time in freq1] [time in freq2] [time in freq3] ...
 * [uid2]: [time in freq1] [time in freq2] [time in freq3] ...
 * ...
 *
 * This provides the times a UID's processes spent executing at each different cpu frequency.
 * The file contains a monotonically increasing count of time for a single boot. This class
 * maintains the previous results of a call to `readDelta` in order to provide a proper
 * delta.
 *
 * where time is measured in jiffies.
 *
 * @see com.android.internal.os.KernelCpuUidTimeReader.KernelCpuUidFreqTimeReader
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
@Suppress("SpellCheckingInspection", "JavadocReference")
class KernelCpuUidFreqTimeReader(
    pid: Int,
    private val mClusterSteps: IntArray,
) {
    private val mProcFile: String = "/proc/$pid/time_in_state"

    @Throws(IOException::class)
    fun smoke() {
        val cpuCoreStepJiffies = readAbsolute()
        if (mClusterSteps.size != cpuCoreStepJiffies.size) {
            throw IOException(
                "Cpu clusterNum unmatched, expect = ${mClusterSteps.size}, actual = ${cpuCoreStepJiffies.size}"
            )
        }
        for (i in cpuCoreStepJiffies.indices) {
            val clusterStepJiffies = cpuCoreStepJiffies[i]
            if (mClusterSteps[i] != clusterStepJiffies.size) {
                throw IOException(
                    "Cpu clusterStepNum unmatched, expect = ${mClusterSteps[i]}, actual = ${clusterStepJiffies.size}, cluster = $i"
                )
            }
        }
    }

    @Throws(IOException::class)
    fun readTotal(): List<Long> {
        val cpuCoreStepJiffies = readAbsolute()
        val cpuCoreJiffies: MutableList<Long> = ArrayList(cpuCoreStepJiffies.size)
        for (stepJiffies in cpuCoreStepJiffies) {
            var sum = 0L
            for (item in stepJiffies) {
                sum += item
            }
            cpuCoreJiffies.add(sum)
        }
        return cpuCoreJiffies
    }

    @Throws(IOException::class)
    fun readAbsolute(): List<LongArray> {
        val cpuCoreJiffies: MutableList<LongArray> = ArrayList()
        var speedJiffies: LongArray? = null
        try {
            BufferedReader(FileReader(mProcFile)).use { reader ->
                val splitter = TextUtils.SimpleStringSplitter(' ')
                var cluster = -1
                var speedIndex = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.startsWith("cpu")) {
                        if (cluster >= 0) {
                            speedJiffies?.let { cpuCoreJiffies.add(it) }
                        }
                        cluster++
                        speedIndex = 0
                        speedJiffies = LongArray(mClusterSteps[cluster])
                        continue
                    }
                    if (cluster >= 0 && speedIndex < mClusterSteps[cluster]) {
                        splitter.setString(line)
                        splitter.next()
                        speedJiffies!![speedIndex] = splitter.next().toLong()
                        speedIndex++
                    }
                }
                speedJiffies?.let { cpuCoreJiffies.add(it) }
            }
        } catch (e: Throwable) {
            throw IOException("Failed to read cpu-freq: " + e.message, e)
        }
        return cpuCoreJiffies
    }
}

