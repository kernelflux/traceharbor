package com.kernelflux.traceharbor.batterycanary.utils

import android.os.SystemClock
import androidx.annotation.Nullable
import androidx.annotation.RestrictTo
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import java.util.HashMap

/**
 * Configure timeline portions & ratio for the given stamps and return split-portions with each weight.
 *
 * @author Kaede
 * @since 2020/12/22
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
object TimeBreaker {
    /**
     * !!Thread Unsafe!! operations
     */
    @JvmStatic
    fun gcList(list: MutableList<*>?) {
        if (list == null) {
            return
        }
        val size = list.size
        val from = size / 2
        val to = size - 1
        if (from < to) {
            list.subList(from, to).clear()
        }
    }

    @JvmStatic
    fun configurePortions(outerStampList: List<out Stamp>, windowToCurr: Long): TimePortions {
        return configurePortions(outerStampList, windowToCurr, 10L, object : Stamp.Stamper {
            override fun stamp(key: String): Stamp = Stamp(key)
        })
    }

    @JvmStatic
    fun configurePortions(
        outerStampList: List<out Stamp>,
        windowToCurr: Long,
        delta: Long,
        stamper: Stamp.Stamper,
    ): TimePortions {
        val stampList: MutableList<Stamp> = ArrayList(outerStampList)
        if (stampList.isNotEmpty()) {
            val currStamp = stamper.stamp("CURR_STAMP")
            val deltaFromLastStamp = currStamp.upTime - stampList[0].upTime
            if (deltaFromLastStamp > delta) {
                stampList.add(0, currStamp)
            }
        }

        val mapper: MutableMap<String, StatRecord> = HashMap()
        var totalMillis = 0L
        var lastStampMillis = Long.MIN_VALUE
        var lastStampStatMillis = Long.MIN_VALUE

        if (windowToCurr <= 0L) {
            // configure for long all app uptime
            for (item in stampList) {
                if (lastStampMillis != Long.MIN_VALUE) {
                    if (lastStampMillis < item.upTime) {
                        // invalid data
                        break
                    }

                    val interval = lastStampMillis - item.upTime
                    totalMillis += interval
                    val record = mapper.getOrPut(item.key) { StatRecord() }
                    record.weight += interval
                    record.millis += (lastStampStatMillis - item.statMillis)
                }
                lastStampMillis = item.upTime
                lastStampStatMillis = item.statMillis
            }
        } else {
            // just configure for long of the given window
            for (item in stampList) {
                if (lastStampMillis != Long.MIN_VALUE) {
                    if (lastStampMillis < item.upTime) {
                        // invalid data
                        break
                    }

                    val interval = lastStampMillis - item.upTime
                    if (totalMillis + interval >= windowToCurr) {
                        // reach widow edge
                        val lastInterval = windowToCurr - totalMillis
                        totalMillis += lastInterval
                        val record = mapper.getOrPut(item.key) { StatRecord() }
                        record.weight += lastInterval
                        record.millis += ((lastStampStatMillis - item.statMillis) * (lastInterval.toFloat() / interval)).toLong()
                        break
                    }

                    totalMillis += interval
                    val record = mapper.getOrPut(item.key) { StatRecord() }
                    record.weight += interval
                    record.millis += (lastStampStatMillis - item.statMillis)
                }
                lastStampMillis = item.upTime
                lastStampStatMillis = item.statMillis
            }
        }

        val timePortions = TimePortions()
        if (totalMillis <= 0L) {
            timePortions.mIsValid = false
        } else {
            // window > uptime
            if (windowToCurr > totalMillis) {
                timePortions.mIsValid = false
            }

            timePortions.totalUptime = totalMillis
            val portions: MutableList<TimePortions.Portion> = ArrayList()
            for ((key, value) in mapper) {
                val portion = TimePortions.Portion(key, configureRatio(value.weight, totalMillis))
                portion.totalStatMillis = value.millis
                portions.add(portion)
            }
            Collections.sort(portions, Comparator { o1, o2 ->
                val minus = o1.ratio - o2.ratio
                when {
                    minus == 0 -> 0
                    minus > 0 -> -1
                    else -> 1
                }
            })
            timePortions.portions = portions
        }
        return timePortions
    }

    private fun configureRatio(my: Long, total: Long): Int {
        val round = Math.round((my.toDouble() / total) * 100)
        if (round >= 100) return 100
        if (round <= 0) return 0
        return round.toInt()
    }

    private class StatRecord {
        @JvmField
        var weight: Long = 0

        @JvmField
        var millis: Long = 0
    }

    open class Stamp(
        @JvmField val key: String,
        @JvmField val upTime: Long,
        @JvmField val statMillis: Long,
    ) {
        fun interface Stamper {
            fun stamp(key: String): Stamp
        }

        constructor(key: String) : this(key, SystemClock.uptimeMillis(), System.currentTimeMillis())

        constructor(key: String, upTime: Long) : this(key, upTime, System.currentTimeMillis())

        override fun toString(): String {
            return "Stamp{" +
                "key='" + key + '\'' +
                ", upTime=" + upTime +
                ", statMillis=" + statMillis +
                '}'
        }
    }

    class TimePortions internal constructor() {
        class Portion(
            @JvmField val key: String,
            @JvmField val ratio: Int,
        ) {
            @JvmField
            var totalStatMillis: Long = 0
        }

        @JvmField
        var totalUptime: Long = 0

        @JvmField
        var portions: List<Portion> = Collections.emptyList()

        internal var mIsValid: Boolean = true

        val isValid: Boolean
            get() = mIsValid

        fun getRatio(key: String): Int {
            for (item in portions) {
                if (item.key == key) {
                    return item.ratio
                }
            }
            return 0
        }

        @Nullable
        fun top1(): Portion? {
            if (portions.size >= 1) {
                return portions[0]
            }
            return null
        }

        @Nullable
        fun top2(): Portion? {
            if (portions.size >= 2) {
                return portions[1]
            }
            return null
        }

        companion object {
            @JvmStatic
            fun ofInvalid(): TimePortions {
                val item = TimePortions()
                item.mIsValid = false
                return item
            }
        }
    }
}

