package com.kernelflux.traceharbor.batterycanary.monitor.feature

import android.os.Handler
import android.os.SystemClock
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.kernelflux.traceharbor.batterycanary.monitor.BatteryMonitorCore
import com.kernelflux.traceharbor.batterycanary.utils.BatteryCanaryUtil
import com.kernelflux.traceharbor.batterycanary.utils.Function
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.util.Arrays
import java.util.Objects
import java.util.concurrent.Callable

interface MonitorFeature {
    fun configure(monitor: BatteryMonitorCore)
    fun onTurnOn()
    fun onTurnOff()
    fun onForeground(isForeground: Boolean)
    fun onBackgroundCheck(duringMillis: Long)
    fun weight(): Int

    abstract class Snapshot<RECORD> {
        @JvmField
        val time: Long = getTimeStamps()

        @JvmField
        var isDelta: Boolean = false

        private var mIsValid = true

        @Suppress("UNCHECKED_CAST")
        fun setValid(bool: Boolean): Snapshot<RECORD> {
            mIsValid = bool
            return this
        }

        fun isValid(): Boolean = mIsValid

        abstract fun diff(bgn: RECORD): Delta<RECORD>

        protected open fun getTimeStamps(): Long = SystemClock.uptimeMillis()

        abstract class Delta<RECORD> {
            @JvmField
            val bgn: RECORD

            @JvmField
            val end: RECORD

            @JvmField
            val dlt: RECORD

            @JvmField
            val during: Long

            constructor(bgn: RECORD, end: RECORD) {
                this.bgn = bgn
                this.end = end
                during = (end as Snapshot<*>).time - (bgn as Snapshot<*>).time
                dlt = computeDelta()
                (dlt as Snapshot<*>).isDelta = true
            }

            constructor(bgn: RECORD, end: RECORD, dlt: RECORD) {
                this.bgn = bgn
                this.end = end
                during = (end as Snapshot<*>).time - (bgn as Snapshot<*>).time
                this.dlt = dlt
                (dlt as Snapshot<*>).isDelta = true
            }

            fun isValid(): Boolean =
                (bgn as Snapshot<*>).isValid() && (end as Snapshot<*>).isValid()

            protected abstract fun computeDelta(): RECORD

            open class SimpleDelta<RECORD>(bgn: RECORD, end: RECORD, dlt: RECORD) :
                Delta<RECORD>(bgn, end, dlt) {
                override fun computeDelta(): RECORD {
                    throw RuntimeException("stub!")
                }
            }
        }

        abstract class Entry<ENTRY> {
            private var mIsValid = true

            @Suppress("UNCHECKED_CAST")
            open fun setValid(bool: Boolean): ENTRY {
                mIsValid = bool
                return this as ENTRY
            }

            open fun isValid(): Boolean = mIsValid

            abstract class DigitEntry<DIGIT : Number>(
                @JvmField
                var value: DIGIT,
            ) : Entry<DigitEntry<DIGIT>>() {
                fun get(): DIGIT = value

                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (other == null || javaClass != other.javaClass) return false
                    other as DigitEntry<*>
                    return value == other.value
                }

                override fun hashCode(): Int = Objects.hash(value)

                override fun toString(): String = value.toString()

                abstract fun diff(right: DIGIT): DIGIT

                internal class IntDigit(value: Int) : DigitEntry<Int>(value) {
                    override fun diff(right: Int): Int = value - right
                }

                internal class LongDigit(value: Long) : DigitEntry<Long>(value) {
                    override fun diff(right: Long): Long = value - right
                }

                internal class FloatDigit(value: Float) : DigitEntry<Float>(value) {
                    override fun diff(right: Float): Float = value - right
                }

                internal class DoubleDigit(value: Double) : DigitEntry<Double>(value) {
                    override fun diff(right: Double): Double = value - right
                }

                companion object {
                    @Suppress("UNCHECKED_CAST")
                    @JvmStatic
                    fun <DIGIT : Number> of(digit: DIGIT): DigitEntry<DIGIT> {
                        return when (digit) {
                            is Int -> IntDigit(digit) as DigitEntry<DIGIT>
                            is Long -> LongDigit(digit) as DigitEntry<DIGIT>
                            is Float -> FloatDigit(digit) as DigitEntry<DIGIT>
                            is Double -> DoubleDigit(digit) as DigitEntry<DIGIT>
                            else -> throw RuntimeException("unsupported digit: " + digit.javaClass)
                        }
                    }
                }
            }

            open class BeanEntry<BEAN>(
                @JvmField
                var value: BEAN?,
            ) : Entry<BeanEntry<BEAN>>() {
                open fun isEmpty(): Boolean = false

                @Suppress("UNCHECKED_CAST")
                fun get(): BEAN = value as BEAN

                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (other == null || javaClass != other.javaClass) return false
                    other as BeanEntry<*>
                    return Objects.equals(value.toString(), other.value.toString())
                }

                override fun hashCode(): Int = Objects.hash(value)

                override fun toString(): String = value.toString()

                companion object {
                    @JvmField
                    val sEmpty: BeanEntry<*> = object : BeanEntry<Void?>(null) {
                        override fun isEmpty(): Boolean = true
                    }

                    @JvmStatic
                    fun <BEAN> of(bean: BEAN): BeanEntry<BEAN> = BeanEntry(bean)
                }
            }

            open class ListEntry<ITEM : Entry<*>> private constructor() : Entry<ListEntry<ITEM>>() {
                @JvmField
                var list: List<ITEM> = ArrayList()

                override fun isValid(): Boolean {
                    for (item in list) {
                        if (!item.isValid()) return false
                    }
                    return super.isValid()
                }

                fun getList(): List<ITEM> = list

                companion object {
                    @JvmStatic
                    fun <ITEM : Entry<*>> of(items: List<ITEM>): ListEntry<ITEM> {
                        val listEntry = ListEntry<ITEM>()
                        listEntry.list = items
                        return listEntry
                    }

                    @JvmStatic
                    fun <BEAN> ofBeans(items: List<BEAN>): ListEntry<BeanEntry<BEAN>> {
                        val list: MutableList<BeanEntry<BEAN>> = ArrayList()
                        for (item in items) {
                            list.add(BeanEntry.of(item))
                        }
                        return of(list)
                    }

                    @JvmStatic
                    fun <DIGIT : Number> ofDigits(items: List<DIGIT>): ListEntry<DigitEntry<DIGIT>> {
                        val list: MutableList<DigitEntry<DIGIT>> = ArrayList()
                        for (item in items) {
                            list.add(DigitEntry.of(item))
                        }
                        val listEntry = ListEntry<DigitEntry<DIGIT>>()
                        listEntry.list = list
                        return listEntry
                    }

                    @JvmStatic
                    fun <DIGIT : Number> ofDigits(items: Array<DIGIT>): ListEntry<DigitEntry<DIGIT>> =
                        ofDigits(Arrays.asList(*items))

                    @JvmStatic
                    fun ofDigits(items: IntArray): ListEntry<DigitEntry<Int>> {
                        val list: MutableList<DigitEntry<Int>> = ArrayList()
                        for (item in items) {
                            list.add(DigitEntry.of(item))
                        }
                        val listEntry = ListEntry<DigitEntry<Int>>()
                        listEntry.list = list
                        return listEntry
                    }

                    @JvmStatic
                    fun ofDigits(items: LongArray): ListEntry<DigitEntry<Long>> {
                        val list: MutableList<DigitEntry<Long>> = ArrayList()
                        for (item in items) {
                            list.add(DigitEntry.of(item))
                        }
                        val listEntry = ListEntry<DigitEntry<Long>>()
                        listEntry.list = list
                        return listEntry
                    }

                    @JvmStatic
                    fun ofDigits(items: FloatArray): ListEntry<DigitEntry<Float>> {
                        val list: MutableList<DigitEntry<Float>> = ArrayList()
                        for (item in items) {
                            list.add(DigitEntry.of(item))
                        }
                        val listEntry = ListEntry<DigitEntry<Float>>()
                        listEntry.list = list
                        return listEntry
                    }

                    @JvmStatic
                    fun ofDigits(items: DoubleArray): ListEntry<DigitEntry<Double>> {
                        val list: MutableList<DigitEntry<Double>> = ArrayList()
                        for (item in items) {
                            list.add(DigitEntry.of(item))
                        }
                        val listEntry = ListEntry<DigitEntry<Double>>()
                        listEntry.list = list
                        return listEntry
                    }

                    @JvmStatic
                    fun <ITEM : Entry<*>> ofEmpty(): ListEntry<ITEM> {
                        val listEntry = ListEntry<ITEM>()
                        listEntry.list = ArrayList()
                        return listEntry
                    }
                }
            }
        }

        interface Differ<ENTRY : Entry<*>> {
            fun diff(bgn: ENTRY, end: ENTRY): ENTRY

            class DigitDiffer<DIGIT : Number> : Differ<Entry.DigitEntry<DIGIT>> {
                override fun diff(
                    bgn: Entry.DigitEntry<DIGIT>,
                    end: Entry.DigitEntry<DIGIT>
                ): Entry.DigitEntry<DIGIT> {
                    val diff = end.diff(bgn.value)
                    return Entry.DigitEntry.of(diff)
                }

                companion object {
                    @JvmField
                    val sGlobal: DigitDiffer<*> = DigitDiffer<Number>()

                    @Suppress("UNCHECKED_CAST")
                    @JvmStatic
                    fun <DIGIT : Number> globalDiff(
                        bgn: Entry.DigitEntry<DIGIT>,
                        end: Entry.DigitEntry<DIGIT>
                    ): Entry.DigitEntry<DIGIT> {
                        return (sGlobal as DigitDiffer<DIGIT>).diff(bgn, end)
                    }
                }
            }

            class BeanDiffer<BEAN> : Differ<Entry.BeanEntry<BEAN>> {
                @Suppress("UNCHECKED_CAST")
                override fun diff(
                    bgn: Entry.BeanEntry<BEAN>,
                    end: Entry.BeanEntry<BEAN>
                ): Entry.BeanEntry<BEAN> {
                    if (end == bgn) {
                        return Entry.BeanEntry.sEmpty as Entry.BeanEntry<BEAN>
                    }
                    return end
                }

                companion object {
                    @JvmField
                    val sGlobal: BeanDiffer<*> = BeanDiffer<Any?>()

                    @Suppress("UNCHECKED_CAST")
                    @JvmStatic
                    fun <BEAN> globalDiff(
                        bgn: Entry.BeanEntry<BEAN>,
                        end: Entry.BeanEntry<BEAN>
                    ): Entry.BeanEntry<BEAN> {
                        return (sGlobal as BeanDiffer<BEAN>).diff(bgn, end)
                    }
                }
            }

            class ListDiffer<ENTRY : Entry<*>> : Differ<Entry.ListEntry<ENTRY>> {
                @Suppress("UNCHECKED_CAST")
                override fun diff(
                    bgn: Entry.ListEntry<ENTRY>,
                    end: Entry.ListEntry<ENTRY>
                ): Entry.ListEntry<ENTRY> {
                    val diff: Entry.ListEntry<ENTRY> = Entry.ListEntry.ofEmpty()
                    val diffList = diff.list as MutableList<ENTRY>
                    for (i in end.list.indices) {
                        val endEntry = end.list[i]
                        if (endEntry is Entry.DigitEntry<*>) {
                            if (bgn.list.size > i) {
                                val bgnEntry = bgn.list[i]
                                if (bgnEntry is Entry.DigitEntry<*>) {
                                    diffList.add(
                                        DigitDiffer.globalDiff(
                                            bgnEntry as Entry.DigitEntry<Number>,
                                            endEntry as Entry.DigitEntry<Number>
                                        ) as ENTRY
                                    )
                                    continue
                                }
                            }
                            diffList.add(
                                Entry.DigitEntry.of((endEntry as Entry.DigitEntry<Number>).value)
                                    .setValid(false) as ENTRY
                            )
                        } else if (endEntry is Entry.BeanEntry<*>) {
                            if (bgn.list.contains(endEntry)) {
                                continue
                            }
                            var find = false
                            for (bgnEntry in bgn.list) {
                                if (bgnEntry !is Entry.BeanEntry<*>) {
                                    continue
                                }
                                if (BeanDiffer.globalDiff(
                                        bgnEntry as Entry.BeanEntry<Any?>,
                                        endEntry as Entry.BeanEntry<Any?>
                                    ) === Entry.BeanEntry.sEmpty
                                ) {
                                    find = true
                                    break
                                }
                            }
                            if (!find) {
                                diffList.add(endEntry)
                            }
                        }
                    }
                    return diff
                }

                companion object {
                    @JvmField
                    val sGlobal: ListDiffer<*> = ListDiffer<Entry<*>>()

                    @Suppress("UNCHECKED_CAST")
                    @JvmStatic
                    fun <ENTRY : Entry<*>> globalDiff(
                        bgn: Entry.ListEntry<ENTRY>,
                        end: Entry.ListEntry<ENTRY>
                    ): Entry.ListEntry<ENTRY> {
                        return (sGlobal as ListDiffer<ENTRY>).diff(bgn, end)
                    }
                }
            }
        }

        open class Sampler {
            @JvmField
            var mTag: String = ""

            @JvmField
            var mHandler: Handler? = null

            @JvmField
            var mSamplingBlock: Function<Sampler, out Number>? = null

            private var mSamplingTask: Runnable? = null

            @JvmField
            var mPaused: Boolean = true

            @JvmField
            var mInterval: Long = BatteryCanaryUtil.ONE_MIN.toLong()

            @JvmField
            var mCount: Int = 0

            @JvmField
            var mBgnMillis: Long = 0

            @JvmField
            var mEndMillis: Long = 0

            @JvmField
            var mSampleFst: Double = Double.MIN_VALUE

            @JvmField
            var mSampleLst: Double = Double.MIN_VALUE

            @JvmField
            var mSampleMax: Double = Double.MIN_VALUE

            @JvmField
            var mSampleMin: Double = Double.MIN_VALUE

            @JvmField
            var mSampleAvg: Double = Double.MIN_VALUE

            constructor(handler: Handler, onSampling: Callable<out Number>) : this("dft", handler, onSampling)

            constructor(tag: String, handler: Handler, onSampling: Callable<out Number>) {
                mTag = tag
                mHandler = handler
                mSamplingBlock = Function { _ ->
                    try {
                        onSampling.call()
                    } catch (e: Exception) {
                        throw RuntimeException(e)
                    }
                }
                mSamplingTask = createSamplingTask()
            }

            constructor(tag: String, handler: Handler, onSampling: Function<Sampler, out Number>) {
                mTag = tag
                mHandler = handler
                mSamplingBlock = onSampling
                mSamplingTask = createSamplingTask()
            }

            private fun createSamplingTask(): Runnable {
                return object : Runnable {
                    override fun run() {
                        try {
                            val currSample = mSamplingBlock!!.apply(this@Sampler)
                            if (currSample != INVALID) {
                                mSampleLst = currSample.toDouble()
                                mCount++
                                // FIXME: calc vag on finished
                                mSampleAvg = (mSampleAvg * (mCount - 1) + mSampleLst) / mCount
                                if (mSampleFst == Double.MIN_VALUE) {
                                    mSampleFst = mSampleLst
                                    mSampleMax = mSampleLst
                                    mSampleMin = mSampleLst
                                } else {
                                    if (mSampleLst > mSampleMax) {
                                        mSampleMax = mSampleLst
                                    }
                                    if (mSampleLst < mSampleMin) {
                                        mSampleMin = mSampleLst
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            TraceHarborLog.printErrStackTrace(TAG, e, "onSamplingFailed: $e")
                        } finally {
                            if (!mPaused) {
                                mHandler!!.postDelayed(this, mInterval)
                            }
                        }
                    }
                }
            }

            fun getTag(): String = mTag

            fun getCount(): Int = mCount

            fun setInterval(interval: Long) {
                if (interval > 0) {
                    mInterval = interval
                }
            }

            fun start() {
                mPaused = false
                mBgnMillis = SystemClock.uptimeMillis()
                mHandler!!.postDelayed(mSamplingTask!!, mInterval)
            }

            fun pause() {
                mPaused = true
                mEndMillis = SystemClock.uptimeMillis()
                mHandler!!.removeCallbacks(mSamplingTask!!)
            }

            @Nullable
            fun getResult(): Result? {
                if (mCount <= 0) {
                    TraceHarborLog.w(TAG, "Sampling count is invalid: $mCount")
                    return null
                }
                if (mBgnMillis <= 0 || mEndMillis <= 0 || mBgnMillis > mEndMillis) {
                    TraceHarborLog.w(TAG, "Sampling bgn/end millis is invalid: $mBgnMillis - $mEndMillis")
                    return null
                }
                val result = Result()
                result.interval = mInterval
                result.count = mCount
                result.duringMillis = mEndMillis - mBgnMillis
                result.sampleFst = mSampleFst
                result.sampleLst = mSampleLst
                result.sampleMax = mSampleMax
                result.sampleMin = mSampleMin
                result.sampleAvg = mSampleAvg
                return result
            }

            class Result {
                @JvmField
                var interval: Long = 0

                @JvmField
                var count: Int = 0

                @JvmField
                var duringMillis: Long = 0

                @JvmField
                var sampleFst: Double = 0.0

                @JvmField
                var sampleLst: Double = 0.0

                @JvmField
                var sampleMax: Double = 0.0

                @JvmField
                var sampleMin: Double = 0.0

                @JvmField
                var sampleAvg: Double = 0.0

                override fun toString(): String {
                    return "Result{" +
                        "interval=" + interval +
                        ", count=" + count +
                        ", duringMillis=" + duringMillis +
                        ", sampleFst=" + sampleFst +
                        ", sampleLst=" + sampleLst +
                        ", sampleMax=" + sampleMax +
                        ", sampleMin=" + sampleMin +
                        ", sampleAvg=" + sampleAvg +
                        '}'
                }
            }

            companion object {
                private const val TAG = "TraceHarbor.battery.Sampler"

                @JvmField
                val INVALID: Int = Int.MIN_VALUE
            }
        }
    }
}
