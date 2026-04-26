package com.kernelflux.traceharbor.sqlitelint

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.NonNull

class SQLiteLintIssue() : Parcelable {
    @JvmField
    var id: String? = null

    @JvmField
    var dbPath: String? = null

    @JvmField
    var level: Int = 0

    @JvmField
    var type: Int = 0

    @JvmField
    var sql: String? = null

    @JvmField
    var table: String? = null

    @JvmField
    var desc: String? = null

    @JvmField
    var detail: String? = null

    @JvmField
    var advice: String? = null

    @JvmField
    var createTime: Long = 0

    @JvmField
    var extInfo: String? = null

    @JvmField
    var sqlTimeCost: Long = 0

    @JvmField
    var isNew: Boolean = false

    @JvmField
    var isInMainThread: Boolean = false

    constructor(
        id: String?,
        dbPath: String?,
        level: Int,
        type: Int,
        sql: String?,
        table: String?,
        desc: String?,
        detail: String?,
        advice: String?,
        createTime: Long,
        extInfo: String?,
        sqlTimeCost: Long,
        isInMainThread: Boolean
    ) : this() {
        this.id = id
        this.dbPath = dbPath
        this.level = level
        this.type = type
        this.sql = sql
        this.table = table
        this.desc = desc
        this.detail = detail
        this.advice = advice
        this.createTime = createTime
        this.extInfo = extInfo
        this.sqlTimeCost = sqlTimeCost
        this.isInMainThread = isInMainThread
    }

    private constructor(parcel: Parcel) : this() {
        id = parcel.readString()
        dbPath = parcel.readString()
        level = parcel.readInt()
        type = parcel.readInt()
        sql = parcel.readString()
        table = parcel.readString()
        desc = parcel.readString()
        detail = parcel.readString()
        advice = parcel.readString()
        createTime = parcel.readLong()
        extInfo = parcel.readString()
        sqlTimeCost = parcel.readLong()
        isInMainThread = parcel.readInt() == 1
    }

    override fun equals(other: Any?): Boolean {
        if (other !is SQLiteLintIssue) {
            return false
        }
        return other.id == id
    }

    override fun hashCode(): Int {
        return id!!.hashCode()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(id)
        dest.writeString(dbPath)
        dest.writeInt(level)
        dest.writeInt(type)
        dest.writeString(sql)
        dest.writeString(table)
        dest.writeString(desc)
        dest.writeString(detail)
        dest.writeString(advice)
        dest.writeLong(createTime)
        dest.writeString(extInfo)
        dest.writeLong(sqlTimeCost)
        dest.writeInt(if (isInMainThread) 1 else 0)
    }

    companion object {
        const val PASS = 0
        const val TIPS = 1
        const val SUGGESTION = 2
        const val WARNING = 3
        const val ERROR = 4

        @JvmStatic
        @NonNull
        fun getLevelText(level: Int, @NonNull context: Context): String {
            return when (level) {
                TIPS -> context.getString(R.string.diagnosis_level_tips)
                SUGGESTION -> context.getString(R.string.diagnosis_level_suggestion)
                WARNING -> context.getString(R.string.diagnosis_level_warning)
                ERROR -> context.getString(R.string.diagnosis_level_error)
                else -> context.getString(R.string.diagnosis_level_suggestion)
            }
        }

        @JvmField
        val CREATOR: Parcelable.Creator<SQLiteLintIssue> = object : Parcelable.Creator<SQLiteLintIssue> {
            override fun createFromParcel(parcel: Parcel): SQLiteLintIssue {
                return SQLiteLintIssue(parcel)
            }

            override fun newArray(size: Int): Array<SQLiteLintIssue?> {
                return arrayOfNulls(size)
            }
        }
    }
}
