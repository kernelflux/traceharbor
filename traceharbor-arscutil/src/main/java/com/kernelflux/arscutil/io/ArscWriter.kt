package com.kernelflux.arscutil.io

import com.kernelflux.arscutil.data.ResTable
import com.kernelflux.traceharbor.javalib.util.Log
import java.io.File

class ArscWriter(arscFile: String) {
    private val dataOutput: LittleEndianOutputStream

    init {
        val file = File(arscFile)
        Log.i(TAG, "write to %s", arscFile)
        if (file.exists()) {
            file.delete()
        }
        file.parentFile.mkdirs()
        file.createNewFile()
        dataOutput = LittleEndianOutputStream(arscFile)
    }

    @Throws(Exception::class)
    fun writeResTable(resTable: ResTable) {
        dataOutput.write(resTable.toBytes())
        dataOutput.close()
    }

    companion object {
        private const val TAG = "ArscUtil.ArscWriter"
    }
}
