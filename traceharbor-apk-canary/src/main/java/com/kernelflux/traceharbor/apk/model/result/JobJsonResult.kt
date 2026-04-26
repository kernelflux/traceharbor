package com.kernelflux.traceharbor.apk.model.result

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.kernelflux.traceharbor.javalib.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.util.ArrayList
import java.util.Collections

class JobJsonResult(
    format: String,
    outputPath: String,
) : JobResult() {
    private val outputFile: File = File("$outputPath.${TaskResultFactory.TASK_RESULT_TYPE_JSON}")
    private var elementCount: Int = 0

    init {
        this.format = format
        this.resultList = ArrayList()
    }

    @Throws(IOException::class)
    private fun writeJsonArrayStart() {
        var printWriter: PrintWriter? = null
        try {
            if (outputFile.exists() && !outputFile.delete()) {
                Log.e(TAG, "file " + outputFile.name + " is already exists and delete it failed!")
                return
            }
            if (!outputFile.createNewFile()) {
                Log.e(TAG, "create output file " + outputFile.name + " failed!")
                return
            }
            printWriter = PrintWriter(outputFile, "UTF-8")
            printWriter.append("[")
        } finally {
            printWriter?.close()
        }
    }

    private fun writeJsonElement(jsonElement: JsonElement?) {
        if (jsonElement != null) {
            try {
                var writer: FileWriter? = null
                val gson = GsonBuilder().setPrettyPrinting().create()
                try {
                    writer = FileWriter(outputFile, true)
                    if (elementCount > 0) {
                        writer.append(",\n" + gson.toJson(jsonElement))
                    } else {
                        writer.append(gson.toJson(jsonElement))
                    }
                    elementCount++
                } finally {
                    writer?.close()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun writeJsonArrayEnd() {
        try {
            var writer: FileWriter? = null
            try {
                writer = FileWriter(outputFile, true)
                writer.append("]")
            } finally {
                writer?.close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun output() {
        try {
            writeJsonArrayStart()
            val list = resultList
            if (!list.isNullOrEmpty()) {
                Collections.sort(list, TaskResultComparator())
                for (taskResult in list) {
                    val result = taskResult.getResult()
                    if (result is JsonObject) {
                        writeJsonElement(result)
                    }
                }
            }
            writeJsonArrayEnd()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val TAG = "JobJsonResult"
    }
}

