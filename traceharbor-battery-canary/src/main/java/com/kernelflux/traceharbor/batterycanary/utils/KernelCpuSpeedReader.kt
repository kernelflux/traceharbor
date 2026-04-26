package com.kernelflux.traceharbor.batterycanary.utils

import android.text.TextUtils
import androidx.annotation.RestrictTo
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException

/**
 * Reads CPU time of a specific core spent at various frequencies and provides a delta from the
 * last call to `readDelta`. Each line in the proc file has the format:
 *
 * freq time
 *
 * where time is measured in jiffies.
 *
 * @see com.android.internal.os.KernelCpuSpeedReader
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
@Suppress("SpellCheckingInspection", "JavadocReference")
class KernelCpuSpeedReader(
    cpuNumber: Int,
    private val mNumSpeedSteps: Int,
) {
    private val mProcFile: String = "/sys/devices/system/cpu/cpu$cpuNumber/cpufreq/stats/time_in_state"

    @Throws(IOException::class)
    fun smoke() {
        val stepJiffies = readAbsolute()
        if (stepJiffies.size != mNumSpeedSteps) {
            throw IOException(
                "CpuCore Step unmatched, expect = $mNumSpeedSteps, actual = ${stepJiffies.size}, path = $mProcFile"
            )
        }
    }

    @Throws(IOException::class)
    fun readTotal(): Long {
        var sum = 0L
        for (item in readAbsolute()) {
            sum += item
        }
        return sum
    }

    /**
     * @return The time (in jiffies) spent at different cpu speeds. The values should be
     * monotonically increasing, unless the cpu was hotplugged.
     */
    @Throws(IOException::class)
    fun readAbsolute(): LongArray {
        val speedTimeJiffies = LongArray(mNumSpeedSteps)
        try {
            BufferedReader(FileReader(mProcFile)).use { reader ->
                val splitter = TextUtils.SimpleStringSplitter(' ')
                var speedIndex = 0
                while (speedIndex < mNumSpeedSteps) {
                    val line = reader.readLine() ?: break
                    splitter.setString(line)
                    splitter.next()
                    speedTimeJiffies[speedIndex] = splitter.next().toLong()
                    speedIndex++
                }
            }
        } catch (e: Throwable) {
            throw IOException("Failed to read cpu-freq: " + e.message, e)
        }
        return speedTimeJiffies
    }
}

