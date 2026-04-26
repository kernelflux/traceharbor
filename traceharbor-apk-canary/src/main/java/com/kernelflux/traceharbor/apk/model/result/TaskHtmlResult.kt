package com.kernelflux.traceharbor.apk.model.result

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.w3c.dom.Document
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

open class TaskHtmlResult
    @Throws(ParserConfigurationException::class)
    constructor(
        taskType: Int,
        @JvmField protected var config: JsonObject?,
    ) : TaskResult(taskType) {
        @JvmField
        protected val document: Document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()

        private fun add(root: Element) {
            document.appendChild(root)
        }

        @Throws(ParserConfigurationException::class)
        open fun format(jsonObject: JsonObject) {
        val root = toElement(document, jsonObject) ?: return
        add(root)
        }

        @Throws(ParserConfigurationException::class)
        private fun toElement(document: Document, jsonElement: JsonElement?): Element? {
            if (jsonElement == null) {
                return null
            }

            if (jsonElement.isJsonPrimitive) {
                val value = document.createElement("span")
                value.textContent = jsonElement.asString
                return value
            } else if (jsonElement.isJsonObject) {
                val table = document.createElement("table")
                table.setAttribute("border", "1")
                table.setAttribute("width", "100%")

                for ((key, jsonValue) in (jsonElement as JsonObject).entrySet()) {
                    if (jsonValue.isJsonPrimitive) {
                        val tr = document.createElement("tr")
                        val name = document.createElement("td")
                        name.setAttribute("valign", "top")
                        name.setAttribute("width", "30%")
                        name.textContent = key
                        val value = document.createElement("td")
                        value.textContent = jsonValue.asString
                        tr.appendChild(name)
                        tr.appendChild(value)
                        table.appendChild(tr)
                    } else if (jsonValue.isJsonObject) {
                        val tr = document.createElement("tr")
                        val name = document.createElement("td")
                        name.setAttribute("valign", "top")
                        name.setAttribute("width", "30%")
                        name.textContent = key
                        val value = document.createElement("td")
                        value.appendChild(toElement(document, jsonValue))
                        tr.appendChild(name)
                        tr.appendChild(value)
                        table.appendChild(tr)
                    } else if (jsonValue.isJsonArray) {
                        val tr = document.createElement("tr")
                        val name = document.createElement("td")
                        name.setAttribute("valign", "top")
                        name.setAttribute("width", "30%")
                        name.textContent = key
                        val value = document.createElement("td")
                        val array = jsonValue as JsonArray
                        val ul = document.createElement("ul")
                        ul.setAttribute("style", "list-style-type:none")
                        value.appendChild(ul)
                        for (i in 0 until array.size()) {
                            val li = document.createElement("li")
                            li.appendChild(toElement(document, array[i]))
                            ul.appendChild(li)
                        }
                        tr.appendChild(name)
                        tr.appendChild(value)
                        table.appendChild(tr)
                    }
                }
                return table
            }
            return null
        }

        override fun toString(): String = document.toString()

        override fun getResult(): Document = document
    }

