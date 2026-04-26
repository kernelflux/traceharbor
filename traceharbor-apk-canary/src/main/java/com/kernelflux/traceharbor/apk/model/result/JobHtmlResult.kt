package com.kernelflux.traceharbor.apk.model.result

import com.kernelflux.traceharbor.javalib.util.Log
import org.w3c.dom.Document
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.util.ArrayList
import java.util.Collections
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class JobHtmlResult(
    format: String,
    outputPath: String,
) : JobResult() {
    private val outputFile: File = File("$outputPath.${TaskResultFactory.TASK_RESULT_TYPE_HTML}")

    init {
        this.format = format
        this.resultList = ArrayList()
    }

    @Throws(IOException::class)
    private fun writeHtmlStart() {
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
            printWriter.append("<html>")
            printWriter.append("<body>")
        } finally {
            printWriter?.close()
        }
    }

    @Throws(Exception::class)
    private fun writeDocument(domSource: DOMSource) {
        try {
            val transformer = TransformerFactory.newInstance().newTransformer()
            if (outputFile.isFile && outputFile.exists()) {
                val writer = FileWriter(outputFile, true)
                writer.append("<br/>")
                val result = StreamResult(outputFile)
                result.writer = writer
                transformer.transform(domSource, result)
            }
        } catch (e: TransformerException) {
            throw e
        } catch (e: IOException) {
            throw e
        }
    }

    @Throws(IOException::class)
    private fun writeHtmlEnd() {
        try {
            var writer: FileWriter? = null
            try {
                writer = FileWriter(outputFile, true)
                writer.append("</body>")
                writer.append("</html>")
            } finally {
                writer?.close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun output() {
        try {
            writeHtmlStart()
            val list = resultList
            if (!list.isNullOrEmpty()) {
                Collections.sort(list, TaskResultComparator())
                for (taskResult in list) {
                    val result = taskResult.getResult()
                    if (result is Document) {
                        writeDocument(DOMSource(result))
                    }
                }
            }
            writeHtmlEnd()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val TAG = "JobHtmlResult"
    }
}

