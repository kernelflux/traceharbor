package com.kernelflux.traceharbor.resource.analyzer.model

import com.kernelflux.traceharbor.resource.analyzer.utils.AnalyzeUtil
import com.kernelflux.traceharbor.resource.common.utils.Preconditions.checkNotNull
import com.squareup.haha.perflib.HprofParser
import com.squareup.haha.perflib.Snapshot
import com.squareup.haha.perflib.io.HprofBuffer
import com.squareup.haha.perflib.io.MemoryMappedFileBuffer
import java.io.File
import java.io.IOException

class HeapSnapshot(
    hprofFile: File,
) {
    @JvmField val mHprofFile: File = checkNotNull(hprofFile, "hprofFile")
    @JvmField val mSnapshot: Snapshot = initSnapshot(hprofFile)

    fun getHprofFile(): File = mHprofFile

    fun getSnapshot(): Snapshot = mSnapshot

    companion object {
        @Throws(IOException::class)
        private fun initSnapshot(hprofFile: File): Snapshot {
            val buffer: HprofBuffer = MemoryMappedFileBuffer(hprofFile)
            val parser = HprofParser(buffer)
            val result = parser.parse()
            AnalyzeUtil.deduplicateGcRoots(result)
            return result
        }
    }
}

