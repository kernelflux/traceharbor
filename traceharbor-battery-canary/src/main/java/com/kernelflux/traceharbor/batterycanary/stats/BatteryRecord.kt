package com.kernelflux.traceharbor.batterycanary.stats

import android.os.Parcel
import android.os.Parcelable
import android.util.ArrayMap
import androidx.annotation.CallSuper
import com.kernelflux.traceharbor.batterycanary.monitor.AppStats
import java.util.Collections
import java.util.HashMap
import java.util.LinkedHashMap

/**
 * @author Kaede
 * @since 2021/12/10
 */
abstract class BatteryRecord protected constructor() : Parcelable {
    @JvmField
    var version: Int = 0

    @JvmField
    var millis: Long = System.currentTimeMillis()

    protected constructor(parcel: Parcel) : this() {
        version = parcel.readInt()
        millis = parcel.readLong()
    }

    override fun describeContents(): Int = 0

    @CallSuper
    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(version)
        dest.writeLong(millis)
    }

    open class ProcStatRecord : BatteryRecord {
        @JvmField
        var procStat: Int = STAT_PROC_LAUNCH

        @JvmField
        var pid: Int = 0

        constructor() : super() {
            version = VERSION
        }

        protected constructor(parcel: Parcel) : super(parcel) {
            procStat = parcel.readInt()
            pid = parcel.readInt()
        }

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeInt(procStat)
            dest.writeInt(pid)
        }

        companion object {
            const val VERSION: Int = 0
            const val STAT_PROC_LAUNCH: Int = 1
            const val STAT_PROC_OFF: Int = 2

            @JvmField
            val CREATOR: Parcelable.Creator<ProcStatRecord> =
                object : Parcelable.Creator<ProcStatRecord> {
                    override fun createFromParcel(parcel: Parcel): ProcStatRecord =
                        ProcStatRecord(parcel)

                    override fun newArray(size: Int): Array<ProcStatRecord?> =
                        arrayOfNulls(size)
                }
        }
    }

    open class DevStatRecord : BatteryRecord {
        @AppStats.DevStatusDef
        @JvmField
        var devStat: Int = 0

        constructor() : super()

        protected constructor(parcel: Parcel) : super(parcel) {
            devStat = parcel.readInt()
        }

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeInt(devStat)
        }

        companion object {
            const val VERSION: Int = 0

            @JvmField
            val CREATOR: Parcelable.Creator<DevStatRecord> =
                object : Parcelable.Creator<DevStatRecord> {
                    override fun createFromParcel(parcel: Parcel): DevStatRecord =
                        DevStatRecord(parcel)

                    override fun newArray(size: Int): Array<DevStatRecord?> =
                        arrayOfNulls(size)
                }
        }
    }

    open class AppStatRecord : BatteryRecord {
        @AppStats.AppStatusDef
        @JvmField
        var appStat: Int = 0

        constructor() : super() {
            version = VERSION
        }

        protected constructor(parcel: Parcel) : super(parcel) {
            appStat = parcel.readInt()
        }

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeInt(appStat)
        }

        companion object {
            const val VERSION: Int = 0

            @JvmField
            val CREATOR: Parcelable.Creator<AppStatRecord> =
                object : Parcelable.Creator<AppStatRecord> {
                    override fun createFromParcel(parcel: Parcel): AppStatRecord =
                        AppStatRecord(parcel)

                    override fun newArray(size: Int): Array<AppStatRecord?> =
                        arrayOfNulls(size)
                }
        }
    }

    open class SceneStatRecord : BatteryRecord {
        @JvmField
        var scene: String? = null

        constructor() : super() {
            version = VERSION
        }

        protected constructor(parcel: Parcel) : super(parcel) {
            scene = parcel.readString()
        }

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeString(scene)
        }

        companion object {
            const val VERSION: Int = 0

            @JvmField
            val CREATOR: Parcelable.Creator<SceneStatRecord> =
                object : Parcelable.Creator<SceneStatRecord> {
                    override fun createFromParcel(parcel: Parcel): SceneStatRecord =
                        SceneStatRecord(parcel)

                    override fun newArray(size: Int): Array<SceneStatRecord?> =
                        arrayOfNulls(size)
                }
        }
    }

    /**
     * EventStatRecord {
     *     extras [:]  // since version >= 1
     * }
     */
    open class EventStatRecord : BatteryRecord {
        @JvmField
        var id: Long = 0

        @JvmField
        var event: String? = null

        @JvmField
        var extras: MutableMap<String, Any> = Collections.emptyMap<String, Any>() as MutableMap<String, Any>

        constructor() : super() {
            version = VERSION
        }

        @Suppress("UNCHECKED_CAST")
        protected constructor(parcel: Parcel) : super(parcel) {
            id = parcel.readLong()
            event = parcel.readString()
            if (version >= 1) {
                val readExtras = HashMap<String, Any>()
                parcel.readMap(readExtras as MutableMap<Any?, Any?>, javaClass.classLoader)
                extras = readExtras
            }
        }

        open fun getString(key: String): String {
            if (extras.containsKey(key)) {
                return extras[key].toString()
            }
            return ""
        }

        open fun getDigit(key: String, def: Long): Long {
            try {
                if (extras.containsKey(key)) {
                    return extras[key].toString().toLong()
                }
            } catch (ignored: Exception) {
            }
            return def
        }

        open fun getBoolean(key: String, def: Boolean): Boolean {
            if (extras.containsKey(key)) {
                try {
                    return extras[key].toString().toBoolean()
                } catch (ignored: Exception) {
                }
            }
            return def
        }

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeLong(id)
            dest.writeString(event)
            dest.writeMap(extras)
        }

        companion object {
            const val VERSION: Int = 1
            const val EVENT_REPORT: String = "REPORT"
            const val EVENT_BATTERY_STAT: String = "BATTERY_STAT"

            @JvmField
            val CREATOR: Parcelable.Creator<EventStatRecord> =
                object : Parcelable.Creator<EventStatRecord> {
                    override fun createFromParcel(parcel: Parcel): EventStatRecord =
                        EventStatRecord(parcel)

                    override fun newArray(size: Int): Array<EventStatRecord?> =
                        arrayOfNulls(size)
                }
        }
    }

    /**
     * ReportRecord {
     *     extras [:]  // since version >= 1
     *
     *     ThreadInfo [] {
     *         extraInfo [:]
     *     }
     *
     *     EntryInfo [] {
     *         entries [:]
     *     }
     * }
     */
    open class ReportRecord : EventStatRecord {
        @JvmField
        var scope: String? = null

        @JvmField
        var windowMillis: Long = 0

        @JvmField
        var threadInfoList: MutableList<ThreadInfo?> =
            Collections.emptyList<ThreadInfo>() as MutableList<ThreadInfo?>

        @JvmField
        var entryList: MutableList<EntryInfo?> =
            Collections.emptyList<EntryInfo>() as MutableList<EntryInfo?>

        constructor() : super() {
            version = VERSION
            event = EVENT_REPORT
        }

        protected constructor(parcel: Parcel) : super(parcel) {
            scope = parcel.readString()
            windowMillis = parcel.readLong()
            threadInfoList = parcel.createTypedArrayList(ThreadInfo.CREATOR)
                ?: Collections.emptyList<ThreadInfo>() as MutableList<ThreadInfo?>
            entryList = parcel.createTypedArrayList(EntryInfo.CREATOR)
                ?: Collections.emptyList<EntryInfo>() as MutableList<EntryInfo?>
        }

        open fun isOverHeat(): Boolean {
            for (item in extras.keys) {
                if (item.endsWith("_overheat")) {
                    if (getBoolean(item, false)) {
                        return true
                    }
                }
            }
            return false
        }

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeString(scope)
            dest.writeLong(windowMillis)
            dest.writeTypedList(threadInfoList)
            dest.writeTypedList(entryList)
        }

        open class ThreadInfo : Parcelable {
            @JvmField
            var tid: Int = 0

            @JvmField
            var name: String? = null

            @JvmField
            var stat: String? = null

            @JvmField
            var jiffies: Long = 0

            @JvmField
            var extraInfo: MutableMap<String, String> =
                Collections.emptyMap<String, String>() as MutableMap<String, String>

            constructor()

            @Suppress("UNCHECKED_CAST")
            protected constructor(parcel: Parcel) {
                tid = parcel.readInt()
                name = parcel.readString()
                stat = parcel.readString()
                jiffies = parcel.readLong()
                val readExtraInfo = ArrayMap<String, String>()
                parcel.readMap(readExtraInfo as MutableMap<Any?, Any?>, javaClass.classLoader)
                extraInfo = readExtraInfo
            }

            override fun describeContents(): Int = 0

            override fun writeToParcel(dest: Parcel, flags: Int) {
                dest.writeInt(tid)
                dest.writeString(name)
                dest.writeString(stat)
                dest.writeLong(jiffies)
                dest.writeMap(extraInfo)
            }

            companion object {
                @JvmField
                val CREATOR: Parcelable.Creator<ThreadInfo> =
                    object : Parcelable.Creator<ThreadInfo> {
                        override fun createFromParcel(parcel: Parcel): ThreadInfo =
                            ThreadInfo(parcel)

                        override fun newArray(size: Int): Array<ThreadInfo?> =
                            arrayOfNulls(size)
                    }
            }
        }

        open class EntryInfo : Parcelable {
            @JvmField
            var name: String? = null

            @JvmField
            var stat: String? = null

            @JvmField
            var entries: MutableMap<String, String> =
                Collections.emptyMap<String, String>() as MutableMap<String, String>

            constructor()

            @Suppress("UNCHECKED_CAST")
            protected constructor(parcel: Parcel) {
                name = parcel.readString()
                stat = parcel.readString()
                val readEntries = LinkedHashMap<String, String>()
                parcel.readMap(readEntries as MutableMap<Any?, Any?>, javaClass.classLoader)
                entries = readEntries
            }

            override fun describeContents(): Int = 0

            override fun writeToParcel(dest: Parcel, flags: Int) {
                dest.writeString(name)
                dest.writeString(stat)
                dest.writeMap(entries)
            }

            companion object {
                @JvmField
                val CREATOR: Parcelable.Creator<EntryInfo> =
                    object : Parcelable.Creator<EntryInfo> {
                        override fun createFromParcel(parcel: Parcel): EntryInfo =
                            EntryInfo(parcel)

                        override fun newArray(size: Int): Array<EntryInfo?> =
                            arrayOfNulls(size)
                    }
            }
        }

        companion object {
            const val VERSION: Int = EventStatRecord.VERSION

            const val EXTRA_APP_FOREGROUND: String = "app_fg"
            const val EXTRA_JIFFY_TOTAL: String = "jiffy_total"
            const val EXTRA_JIFFY_OVERHEAT: String = "jiffy_overheat"
            const val EXTRA_THREAD_STACK: String = "extra_stack_top"

            @JvmField
            val CREATOR: Parcelable.Creator<ReportRecord> =
                object : Parcelable.Creator<ReportRecord> {
                    override fun createFromParcel(parcel: Parcel): ReportRecord =
                        ReportRecord(parcel)

                    override fun newArray(size: Int): Array<ReportRecord?> =
                        arrayOfNulls(size)
                }
        }
    }

    companion object {
        const val RECORD_TYPE_PROC_STAT: Int = 1
        const val RECORD_TYPE_DEV_STAT: Int = 2
        const val RECORD_TYPE_APP_STAT: Int = 3
        const val RECORD_TYPE_SCENE_STAT: Int = 4
        const val RECORD_TYPE_EVENT_STAT: Int = 5
        const val RECORD_TYPE_REPORT: Int = 6

        @JvmStatic
        fun encode(record: BatteryRecord): ByteArray {
            val type = when (record.javaClass) {
                ProcStatRecord::class.java -> RECORD_TYPE_PROC_STAT
                DevStatRecord::class.java -> RECORD_TYPE_DEV_STAT
                AppStatRecord::class.java -> RECORD_TYPE_APP_STAT
                SceneStatRecord::class.java -> RECORD_TYPE_SCENE_STAT
                EventStatRecord::class.java -> RECORD_TYPE_EVENT_STAT
                ReportRecord::class.java -> RECORD_TYPE_REPORT
                else -> throw UnsupportedOperationException("Unknown record type: $record")
            }

            val parcel = Parcel.obtain()
            try {
                parcel.writeInt(type)
                record.writeToParcel(parcel, 0)
                return parcel.marshall()
            } finally {
                parcel.recycle()
            }
        }

        @JvmStatic
        fun decode(bytes: ByteArray): BatteryRecord {
            val parcel = Parcel.obtain()
            try {
                parcel.unmarshall(bytes, 0, bytes.size)
                parcel.setDataPosition(0)
                val creator: Parcelable.Creator<out BatteryRecord> = when (val type = parcel.readInt()) {
                    RECORD_TYPE_PROC_STAT -> ProcStatRecord.CREATOR
                    RECORD_TYPE_DEV_STAT -> DevStatRecord.CREATOR
                    RECORD_TYPE_APP_STAT -> AppStatRecord.CREATOR
                    RECORD_TYPE_SCENE_STAT -> SceneStatRecord.CREATOR
                    RECORD_TYPE_EVENT_STAT -> EventStatRecord.CREATOR
                    RECORD_TYPE_REPORT -> ReportRecord.CREATOR
                    else -> throw UnsupportedOperationException("Unknown record type: $type")
                }
                return creator.createFromParcel(parcel)
            } finally {
                parcel.recycle()
            }
        }
    }
}
