package com.kernelflux.traceharbor.resource.analyzer.model

import com.kernelflux.traceharbor.resource.common.utils.Preconditions
import java.io.File
import java.io.Serializable

class HeapDump(
    hprofFile: File,
    refKey: String,
    activityName: String,
) : Serializable {
    private val mHprofFile: File = Preconditions.checkNotNull(hprofFile, "hprofFile")
    private val mRefKey: String = Preconditions.checkNotNull(refKey, "refKey")
    private val mActivityName: String = Preconditions.checkNotNull(activityName, "activityName")

    fun getHprofFile(): File = mHprofFile

    fun getReferenceKey(): String = mRefKey

    fun getActivityName(): String = mActivityName
}

