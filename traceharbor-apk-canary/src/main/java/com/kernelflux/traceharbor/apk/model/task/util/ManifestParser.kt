package com.kernelflux.traceharbor.apk.model.task.util

import brut.androlib.AndrolibException
import brut.androlib.res.decoder.AXmlResourceParser
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.kernelflux.traceharbor.javalib.util.Util
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Stack

class ManifestParser {
    private val resourceParser: AXmlResourceParser
    private var manifestFile: File? = null
    private var isParseStarted = false
    private val jsonStack = Stack<JsonObject>()
    private var result: JsonObject? = null

    constructor(path: String) {
        manifestFile = File(path)
        resourceParser = ApkResourceDecoder.createAXmlParser()
    }

    constructor(manifestFile: File?) {
        if (manifestFile != null) {
            this.manifestFile = manifestFile
        }
        resourceParser = ApkResourceDecoder.createAXmlParser()
    }

    @Throws(IOException::class, AndrolibException::class)
    constructor(manifestFile: File?, arscFile: File?) {
        if (manifestFile != null) {
            this.manifestFile = manifestFile
        }
        resourceParser = ApkResourceDecoder.createAXmlParser(arscFile)
    }

    @Throws(Exception::class)
    fun parse(): JsonObject? {
        var inputStream: FileInputStream? = null
        try {
            inputStream = FileInputStream(manifestFile)
            try {
                resourceParser.open(inputStream)
                var token = resourceParser.nextToken()

                while (token != XmlPullParser.END_DOCUMENT) {
                    token = resourceParser.next()
                    if (token == XmlPullParser.START_TAG) {
                        handleStartElement()
                    } else if (token == XmlPullParser.TEXT) {
                        handleElementContent()
                    } else if (token == XmlPullParser.END_TAG) {
                        handleEndElement()
                    }
                }
            } finally {
                resourceParser.close()
                inputStream?.close()
            }
        } catch (e: Exception) {
            throw e
        }

        return result
    }

    private fun handleStartElement() {
        val name = resourceParser.name

        if (name == ROOTTAG) {
            isParseStarted = true
        }
        if (isParseStarted) {
            val jsonObject = JsonObject()
            for (i in 0 until resourceParser.attributeCount) {
                if (!Util.isNullOrNil(resourceParser.getAttributePrefix(i))) {
                    jsonObject.addProperty(
                        resourceParser.getAttributePrefix(i) + ":" + resourceParser.getAttributeName(i),
                        resourceParser.getAttributeValue(i),
                    )
                } else {
                    jsonObject.addProperty(resourceParser.getAttributeName(i), resourceParser.getAttributeValue(i))
                }
            }
            jsonStack.push(jsonObject)
        }
    }

    private fun handleElementContent() {
        // do nothing
    }

    private fun handleEndElement() {
        val name = resourceParser.name
        val jsonObject = jsonStack.pop()

        if (jsonStack.isEmpty()) { // root element
            result = jsonObject
        } else {
            val preObject = jsonStack.peek()
            if (preObject.has(name)) {
                val jsonArray = preObject.getAsJsonArray(name)
                jsonArray.add(jsonObject)
            } else {
                val jsonArray = JsonArray()
                jsonArray.add(jsonObject)
                preObject.add(name, jsonArray)
            }
        }
    }

    companion object {
        private const val ROOTTAG = "manifest"
    }
}

