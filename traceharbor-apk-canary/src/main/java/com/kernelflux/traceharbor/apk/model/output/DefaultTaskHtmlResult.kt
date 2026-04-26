package com.kernelflux.traceharbor.apk.model.output

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.kernelflux.traceharbor.apk.model.result.TaskHtmlResult
import com.kernelflux.traceharbor.apk.model.task.TaskFactory
import com.kernelflux.traceharbor.javalib.util.Util
import org.w3c.dom.Element
import javax.xml.parsers.ParserConfigurationException

class DefaultTaskHtmlResult @Throws(ParserConfigurationException::class) constructor(
    type: Int,
    config: JsonObject?,
) : TaskHtmlResult(type, config) {
    @Throws(ParserConfigurationException::class)
    override fun format(jsonObject: JsonObject) {
        DefaultTaskJsonResult.formatJson(jsonObject, null, config)

        val taskType = jsonObject.get("taskType").asInt
        when (taskType) {
            TaskFactory.TASK_TYPE_UNZIP -> {
                val element = formatUnzipTask(jsonObject)
                foldElement(element)
                document.appendChild(element)
            }
            TaskFactory.TASK_TYPE_MANIFEST -> {
                val element = formatManifestAnalyzeTask(jsonObject)
                foldElement(element)
                document.appendChild(element)
            }
            TaskFactory.TASK_TYPE_SHOW_FILE_SIZE -> {
                val element = formatShowFileSizeTask(jsonObject)
                foldElement(element)
                document.appendChild(element)
            }
            TaskFactory.TASK_TYPE_COUNT_METHOD -> {
                val element = formatMethodCountTask(jsonObject)
                foldElement(element)
                document.appendChild(element)
            }
            TaskFactory.TASK_TYPE_FIND_NON_ALPHA_PNG -> {
                val element = formatFindNonAlphaPngTask(jsonObject)
                foldElement(element)
                document.appendChild(element)
            }
            TaskFactory.TASK_TYPE_UNCOMPRESSED_FILE -> {
                val element = formatUncompressedFileTask(jsonObject)
                foldElement(element)
                document.appendChild(element)
            }
            else -> {
                jsonObject.remove("taskType")
                jsonObject.remove("start-time")
                jsonObject.remove("end-time")
                super.format(jsonObject)
                val firstChild = document.firstChild
                if (firstChild is Element) {
                    foldElement(firstChild)
                }
            }
        }
    }

    private fun foldElement(element: Element?) {
        if (element == null) {
            return
        }
        if (element.childNodes.length > MAX_SHOW_ITEMS) {
            for (i in MAX_SHOW_ITEMS until element.childNodes.length) {
                if (element.childNodes.item(i) is Element) {
                    (element.childNodes.item(i) as Element).setAttribute("hidden", "true")
                }
            }
            val span = document.createElement("span")
            span.setAttribute("style", FOLDER_STYLE)
            span.setAttribute("onClick", FOLDER_ONCLICK)
            span.textContent = "..."

            var folder: Element? = null
            if (element.tagName == "table") {
                folder = document.createElement("tr")
                val td = document.createElement("td")
                td.setAttribute("colspan", "100%")
                folder.appendChild(td)
                td.appendChild(span)
            } else if (element.tagName == "ul") {
                folder = document.createElement("li")
                val parent = document.createElement("span")
                parent.appendChild(span)
                folder.appendChild(parent)
            }
            if (folder != null) {
                element.insertBefore(folder, element.childNodes.item(MAX_SHOW_ITEMS))
            }
        }
        for (i in 0 until element.childNodes.length) {
            if (element.childNodes.item(i) is Element) {
                foldElement(element.childNodes.item(i) as Element)
            }
        }
    }

    private fun formatUnzipTask(jsonObject: JsonObject?): Element {
        val table = document.createElement("table")
        table.setAttribute("border", "1")
        table.setAttribute("width", "100%")
        if (jsonObject == null) {
            return table
        }

        run {
            val tr = document.createElement("tr")
            val td1 = document.createElement("td")
            td1.textContent = "taskDescription"
            val td2 = document.createElement("td")
            td2.textContent = jsonObject.get("taskDescription").asString
            tr.appendChild(td1)
            tr.appendChild(td2)
            table.appendChild(tr)
        }

        run {
            val tr = document.createElement("tr")
            val td1 = document.createElement("td")
            td1.textContent = "total-size"
            val td2 = document.createElement("td")
            td2.textContent = Util.formatByteUnit(jsonObject.get("total-size").asLong)
            tr.appendChild(td1)
            tr.appendChild(td2)
            table.appendChild(tr)
        }

        run {
            val files = jsonObject.getAsJsonArray("entries")
            for (file in files) {
                val suffix = (file as JsonObject).get("suffix").asString
                val size = file.get("total-size").asLong
                val tr = document.createElement("tr")
                val td1 = document.createElement("td")
                td1.textContent = suffix
                val td2 = document.createElement("td")
                td2.textContent = Util.formatByteUnit(size)
                tr.appendChild(td1)
                tr.appendChild(td2)
                table.appendChild(tr)
            }
        }
        return table
    }

    private fun formatManifestAnalyzeTask(jsonObject: JsonObject?): Element {
        val table = document.createElement("table")
        table.setAttribute("border", "1")
        table.setAttribute("width", "100%")
        if (jsonObject == null) {
            return table
        }

        run {
            val tr = document.createElement("tr")
            val td1 = document.createElement("td")
            td1.textContent = "taskDescription"
            val td2 = document.createElement("td")
            td2.textContent = jsonObject.get("taskDescription").asString
            tr.appendChild(td1)
            tr.appendChild(td2)
            table.appendChild(tr)
        }

        run {
            val manifest = jsonObject.getAsJsonObject("manifest")
            for ((key, value) in manifest.entrySet()) {
                val tr = document.createElement("tr")
                val td1 = document.createElement("td")
                td1.textContent = key
                val td2 = document.createElement("td")
                td2.textContent = value.asString
                tr.appendChild(td1)
                tr.appendChild(td2)
                table.appendChild(tr)
            }
        }
        return table
    }

    private fun formatShowFileSizeTask(jsonObject: JsonObject?): Element {
        val table = document.createElement("table")
        table.setAttribute("border", "1")
        table.setAttribute("width", "100%")
        if (jsonObject == null) {
            return table
        }

        run {
            val tr = document.createElement("tr")
            val td1 = document.createElement("td")
            td1.textContent = "taskDescription"
            val td2 = document.createElement("td")
            td2.textContent = jsonObject.get("taskDescription").asString
            tr.appendChild(td1)
            tr.appendChild(td2)
            table.appendChild(tr)
        }

        run {
            val files = jsonObject.getAsJsonArray("files")
            for (file in files) {
                val fileObj = file as JsonObject
                val filename = fileObj.get("entry-name").asString
                if (!Util.isNullOrNil(filename)) {
                    val tr = document.createElement("tr")
                    val td1 = document.createElement("td")
                    td1.textContent = filename
                    val td2 = document.createElement("td")
                    td2.textContent = Util.formatByteUnit(fileObj.get("entry-size").asLong)
                    tr.appendChild(td1)
                    tr.appendChild(td2)
                    table.appendChild(tr)
                }
            }
        }
        return table
    }

    private fun formatMethodCountTask(jsonObject: JsonObject?): Element {
        val table = document.createElement("table")
        table.setAttribute("border", "1")
        table.setAttribute("width", "100%")
        if (jsonObject == null) {
            return table
        }

        run {
            val tr = document.createElement("tr")
            val td1 = document.createElement("td")
            td1.textContent = "taskDescription"
            val td2 = document.createElement("td")
            td2.textContent = jsonObject.get("taskDescription").asString
            tr.appendChild(td1)
            tr.appendChild(td2)
            table.appendChild(tr)
        }

        run {
            val groups = jsonObject.getAsJsonArray("groups")
            for (entry in groups) {
                val obj = entry as JsonObject
                val tr = document.createElement("tr")
                val td1 = document.createElement("td")
                td1.textContent = obj.get("name").asString
                val td2 = document.createElement("td")
                td2.textContent = obj.get("method-count").asString
                tr.appendChild(td1)
                tr.appendChild(td2)
                table.appendChild(tr)
            }

            val tr = document.createElement("tr")
            val td1 = document.createElement("td")
            td1.textContent = "total-methods"
            val td2 = document.createElement("td")
            td2.textContent = jsonObject.get("total-methods").asString + " methods"
            tr.appendChild(td1)
            tr.appendChild(td2)
            table.appendChild(tr)
        }
        return table
    }

    private fun formatFindNonAlphaPngTask(jsonObject: JsonObject?): Element {
        val table = document.createElement("table")
        table.setAttribute("border", "1")
        table.setAttribute("width", "100%")
        if (jsonObject == null) {
            return table
        }

        run {
            val tr = document.createElement("tr")
            val td1 = document.createElement("td")
            td1.textContent = "taskDescription"
            val td2 = document.createElement("td")
            td2.textContent = jsonObject.get("taskDescription").asString
            tr.appendChild(td1)
            tr.appendChild(td2)
            table.appendChild(tr)
        }

        run {
            var totalSize = 0L
            val files = jsonObject.getAsJsonArray("files")
            for (file in files) {
                val fileObj = file as JsonObject
                val filename = fileObj.get("entry-name").asString
                if (!Util.isNullOrNil(filename)) {
                    val tr = document.createElement("tr")
                    val td1 = document.createElement("td")
                    td1.textContent = filename
                    val td2 = document.createElement("td")
                    totalSize += fileObj.get("entry-size").asLong
                    td2.textContent = Util.formatByteUnit(fileObj.get("entry-size").asLong)
                    tr.appendChild(td1)
                    tr.appendChild(td2)
                    table.appendChild(tr)
                }
            }

            val tr = document.createElement("tr")
            val td1 = document.createElement("td")
            td1.textContent = "total-size"
            val td2 = document.createElement("td")
            td2.textContent = Util.formatByteUnit(totalSize)
            tr.appendChild(td1)
            tr.appendChild(td2)
            table.appendChild(tr)
        }
        return table
    }

    private fun formatUncompressedFileTask(jsonObject: JsonObject?): Element {
        val table = document.createElement("table")
        table.setAttribute("border", "1")
        table.setAttribute("width", "100%")
        if (jsonObject == null) {
            return table
        }

        run {
            val tr = document.createElement("tr")
            val td1 = document.createElement("td")
            td1.textContent = "taskDescription"
            val td2 = document.createElement("td")
            td2.textContent = jsonObject.get("taskDescription").asString
            tr.appendChild(td1)
            tr.appendChild(td2)
            table.appendChild(tr)
        }

        run {
            val entries = jsonObject.getAsJsonArray("files")
            for (jsonElement in entries) {
                val jsonObj = jsonElement.asJsonObject
                val tr = document.createElement("tr")
                val td1 = document.createElement("td")
                td1.textContent = jsonObj.get("suffix").asString
                val td2 = document.createElement("td")
                td2.textContent = Util.formatByteUnit(jsonObj.get("total-size").asLong)
                tr.appendChild(td1)
                tr.appendChild(td2)
                table.appendChild(tr)
            }
        }
        return table
    }

    companion object {
        private const val MAX_SHOW_ITEMS = 10
        private const val FOLDER_STYLE = "color:white;font-size:20px;background-color:#C0C0C0"
        private const val FOLDER_ONCLICK =
            "root = this.parentNode.parentNode; " +
                "next = root.nextSibling; " +
                "while (next != null) { " +
                "if (next.hasAttribute('hidden')) { " +
                "next.removeAttribute('hidden');" +
                "} else { " +
                "break; " +
                "} " +
                "next = next.nextSibling; " +
                "} " +
                "root.parentNode.removeChild(root)"
    }
}

