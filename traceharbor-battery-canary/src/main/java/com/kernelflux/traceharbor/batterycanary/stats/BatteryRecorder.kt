package com.kernelflux.traceharbor.batterycanary.stats

import android.os.Process
import android.text.TextUtils
import com.kernelflux.traceharbor.batterycanary.utils.BatteryCanaryUtil
import com.kernelflux.traceharbor.batterycanary.utils.ThreadSafeReference
import com.kernelflux.traceharbor.util.TraceHarborLog
import com.tencent.mmkv.MMKV
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.Collections
import java.util.Comparator
import java.util.HashSet
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Kaede
 * @since 2021/12/10
 */
interface BatteryRecorder {
    fun updateProc(proc: String?)

    fun getProcSet(): Set<String>

    fun write(date: String, record: BatteryRecord)

    fun read(date: String, proc: String?): List<BatteryRecord>

    fun clean(date: String, proc: String?)

    fun clean(dayToKeepOnly: Int)

    class MMKVRecorder(
        @JvmField protected val mmkv: MMKV,
    ) : BatteryRecorder {
        @JvmField
        protected val pid: Int = Process.myPid()

        @JvmField
        protected val inc: AtomicInteger = AtomicInteger(0)

        protected fun getRecordKeyPrefix(date: String, proc: String?): String {
            return MAGIC + "-" + date + (if (TextUtils.isEmpty(proc)) "" else "-" + proc)
        }

        protected fun getProcSetKey(): String = "$MAGIC-proc-set"

        override fun updateProc(proc: String?) {
            if (!TextUtils.isEmpty(proc)) {
                var procSet = getProcSet()
                if (!procSet.contains(proc)) {
                    if (procSet.isEmpty()) {
                        procSet = HashSet()
                    }
                    val mutableSet = HashSet(procSet)
                    mutableSet.add(proc!!)
                    val key = getProcSetKey()
                    mmkv.encode(key, mutableSet)
                }
            }
        }

        override fun getProcSet(): Set<String> {
            val key = getProcSetKey()
            val procSet = mmkv.decodeStringSet(key)
            return procSet ?: Collections.emptySet()
        }

        override fun write(date: String, record: BatteryRecord) {
            val proc = getProcNameSuffix()
            write(date, record, proc)
        }

        fun write(date: String, record: BatteryRecord, proc: String?) {
            try {
                val key = getRecordKeyPrefix(date, proc) + "-" + pid + "-" + inc.getAndIncrement()
                val bytes = BatteryRecord.encode(record)
                mmkv.encode(key, bytes)
                // mmkv.sync();
            } catch (e: Exception) {
                TraceHarborLog.w(TAG, "record encode failed: " + e.message)
            }
        }

        override fun read(date: String, proc: String?): List<BatteryRecord> {
            val keys = mmkv.allKeys() ?: return Collections.emptyList()
            if (keys.isEmpty()) {
                return Collections.emptyList()
            }
            val records: MutableList<BatteryRecord> = ArrayList(minOf(16, keys.size))
            val keyPrefix = getRecordKeyPrefix(date, proc) + "-"
            for (item in keys) {
                if (item.startsWith(keyPrefix)) {
                    try {
                        val bytes = mmkv.decodeBytes(item)
                        if (bytes != null) {
                            val record = BatteryRecord.decode(bytes)
                            records.add(record)
                        }
                    } catch (e: Exception) {
                        TraceHarborLog.w(TAG, "record decode failed: " + e.message)
                    }
                }
            }
            records.sortWith(Comparator { left, right -> left.millis.compareTo(right.millis) })
            return records
        }

        override fun clean(date: String, proc: String?) {
            val keys = mmkv.allKeys() ?: return
            if (keys.isEmpty()) {
                return
            }
            val keyPrefix = getRecordKeyPrefix(date, proc)
            for (item in keys) {
                if (item.startsWith(keyPrefix)) {
                    try {
                        mmkv.remove(item)
                    } catch (e: Exception) {
                        TraceHarborLog.w(TAG, "record clean failed: " + e.message)
                    }
                }
            }
        }

        override fun clean(dayToKeepOnly: Int) {
            if (dayToKeepOnly > 0) {
                val datesToKeep: MutableList<String> = ArrayList()
                for (i in 0 until dayToKeepOnly) {
                    datesToKeep.add(getDateString(-i))
                }
                val keys = mmkv.allKeys() ?: return
                if (keys.isEmpty()) {
                    return
                }
                val procSetKey = getProcSetKey()
                for (item in keys) {
                    if (procSetKey == item) {
                        continue
                    }
                    var keep = false
                    for (date in datesToKeep) {
                        val keyPrefix = getRecordKeyPrefix(date, "")
                        if (item.startsWith(keyPrefix)) {
                            keep = true
                            break
                        }
                    }
                    if (!keep) {
                        try {
                            mmkv.remove(item)
                        } catch (e: Exception) {
                            TraceHarborLog.w(TAG, "record clean failed: " + e.message)
                        }
                    }
                }
            }
        }

        fun flush() {
            mmkv.sync()
        }

        companion object {
            @JvmField
            val MAGIC: String = "bs"

            private val sProcSuffixRef: ThreadSafeReference<String> = object : ThreadSafeReference<String>() {
                override fun onCreate(): String {
                    val processName = BatteryCanaryUtil.getProcessName()
                    return if (processName.contains(":")) {
                        processName.substring(processName.lastIndexOf(":") + 1)
                    } else {
                        "main"
                    }
                }
            }

            private val sFormatRef: ThreadSafeReference<DateFormat> = object : ThreadSafeReference<DateFormat>() {
                override fun onCreate(): DateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            }

            @JvmStatic
            fun getProcNameSuffix(): String = sProcSuffixRef.safeGet()

            @JvmStatic
            fun getDateString(dayOffset: Int): String {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DATE, dayOffset)
                return sFormatRef.safeGet().format(cal.time)
            }
        }
    }

    companion object {
        const val TAG: String = "TraceHarbor.battery.recorder"
    }
}

