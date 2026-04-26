package com.kernelflux.traceharbor.apk.model.task.util

import com.kernelflux.traceharbor.javalib.util.Util
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.HashSet
import brut.androlib.AndrolibException
import brut.androlib.res.decoder.ResStreamDecoder

class XmlPullResourceRefDecoder(
    private val mParser: XmlPullParser,
) : ResStreamDecoder {
    private val resourceRefSet: MutableSet<String> = HashSet()

    @Throws(AndrolibException::class)
    override fun decode(
        inputStream: InputStream,
        outputStream: OutputStream?,
    ) {
        try {
            mParser.setInput(inputStream, null)
            var token = mParser.next()
            while (token != XmlPullParser.END_DOCUMENT) {
                when (token) {
                    XmlPullParser.START_TAG -> handleElement()
                    XmlPullParser.TEXT -> handleContent()
                }
                token = mParser.next()
            }
            inputStream.close()
        } catch (var7: XmlPullParserException) {
            throw AndrolibException("Could not decode XML," + var7.message, var7)
        } catch (e: IOException) {
            throw AndrolibException("Parse xml error," + e.message, e)
        }
    }

    private fun handleElement() {
        var tagName = mParser.name
        val pointIndex = tagName.lastIndexOf('.')
        if (pointIndex >= 0) {
            tagName = tagName.substring(pointIndex + 1)
        }
        if (!Util.isNullOrNil(tagName)) {
            for (i in 0 until mParser.attributeCount) {
                val value = mParser.getAttributeValue(i)
                if (!Util.isNullOrNil(value)) {
                    if (value.startsWith("@")) {
                        val index = value.indexOf('/')
                        if (index > 1) {
                            val type = value.substring(1, index)
                            resourceRefSet.add(ApkConstants.R_PREFIX + type + "." + value.substring(index + 1).replace('.', '_'))
                        }
                    } else if (value.startsWith("?")) {
                        val index = value.indexOf('/')
                        if (index > 1) {
                            resourceRefSet.add(ApkConstants.R_ATTR_PREFIX + "." + value.substring(index + 1).replace('.', '_'))
                        } else {
                            // Attribute reference may be omitted the type, for example:
                            // ?attr/xxx -> ?xxx
                            // ?android:attr/xxx -> ?android:xxx
                            val colonIndex = value.indexOf(':')
                            if (colonIndex > 1) {
                                resourceRefSet.add(ApkConstants.R_ATTR_PREFIX + value.substring(colonIndex + 1).replace('.', '_'))
                            } else {
                                resourceRefSet.add(ApkConstants.R_ATTR_PREFIX + value.substring(1).replace('.', '_'))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleContent() {
        val text = mParser.text
        if (!Util.isNullOrNil(text)) {
            if (text.startsWith("@")) {
                val index = text.indexOf('/')
                if (index > 1) {
                    val type = text.substring(1, index)
                    resourceRefSet.add(ApkConstants.R_PREFIX + type + "." + text.substring(index + 1).replace('.', '_'))
                }
            } else if (text.startsWith("?")) {
                val index = text.indexOf('/')
                if (index > 1) {
                    resourceRefSet.add(ApkConstants.R_ATTR_PREFIX + "." + text.substring(index + 1).replace('.', '_'))
                }
            }
        }
    }

    fun getResourceRefSet(): kotlin.collections.Set<String> = resourceRefSet
}

