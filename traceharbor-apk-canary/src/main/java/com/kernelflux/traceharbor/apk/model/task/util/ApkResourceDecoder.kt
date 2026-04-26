package com.kernelflux.traceharbor.apk.model.task.util

import brut.androlib.AndrolibException
import brut.androlib.res.data.ResPackage
import brut.androlib.res.data.ResResource
import brut.androlib.res.data.ResTable
import brut.androlib.res.data.ResValuesFile
import brut.androlib.res.data.value.ResFileValue
import brut.androlib.res.decoder.ARSCDecoder
import brut.androlib.res.decoder.AXmlResourceParser
import brut.androlib.res.decoder.ResAttrDecoder
import brut.androlib.res.util.ExtMXSerializer
import brut.androlib.res.xml.ResValuesXmlSerializable
import com.kernelflux.traceharbor.javalib.util.FileUtil
import com.kernelflux.traceharbor.javalib.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Map
import java.util.Set
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

@Suppress("PMD")
object ApkResourceDecoder {
    private const val TAG = "TraceHarbor.ApkResourceDecoder"

    const val PROPERTY_SERIALIZER_INDENTATION = "http://xmlpull.org/v1/doc/properties.html#serializer-indentation"
    const val PROPERTY_SERIALIZER_LINE_SEPARATOR = "http://xmlpull.org/v1/doc/properties.html#serializer-line-separator"
    const val PROPERTY_DEFAULT_ENCNDING = "DEFAULT_ENCODING"

    @JvmStatic
    fun createAXmlParser(): AXmlResourceParser {
        val resourceParser = AXmlResourceParser()
        val resTable = ResTable()
        resourceParser.attrDecoder = ResAttrDecoder()
        resourceParser.attrDecoder.currentPackage = ResPackage(resTable, 0, null)
        return resourceParser
    }

    @JvmStatic
    @Throws(IOException::class, AndrolibException::class)
    fun createAXmlParser(arscFile: File?): AXmlResourceParser {
        val resourceParser = createAXmlParser()
        val resTable = ResTable()
        decodeArscFile(arscFile, resTable)
        resourceParser.attrDecoder.currentPackage = resTable.listMainPackages().iterator().next()
        return resourceParser
    }

    @JvmStatic
    @Throws(IOException::class, AndrolibException::class)
    fun decodeArscFile(file: File?, resTable: ResTable) {
        if (file != null && FileUtil.isLegalFile(file)) {
            val inputStream = BufferedInputStream(FileInputStream(file))
            try {
                try {
                    val resPackages = ARSCDecoder.decode(inputStream, false, true, resTable).packages
                    val mainPackage = getMainPackage(resPackages)
                    if (mainPackage != null) {
                        resTable.addPackage(mainPackage, true)
                    }
                    val frameworkPackages = loadFrameworkPackage(resTable)
                    for (sysPackage in frameworkPackages) {
                        resTable.addPackage(sysPackage, false)
                    }
                } finally {
                    inputStream.close()
                }
            } catch (e: IOException) {
                throw e
            }
        }
    }

    @Throws(IOException::class, AndrolibException::class)
    private fun loadFrameworkPackage(resTable: ResTable): Array<ResPackage> {
        var resPackages = emptyArray<ResPackage>()
        val jarInput =
            ApkResourceDecoder::class.java.getResourceAsStream("/android/android-framework.jar")
                ?: throw IOException("android framework jar not found in resources")
        val zipInputStream = ZipInputStream(jarInput)
        var entry: ZipEntry? = zipInputStream.nextEntry
        var bufInputStream: BufferedInputStream? = null
        try {
            try {
                while (entry != null) {
                    if (entry.name == "resources.arsc") {
                        bufInputStream = BufferedInputStream(zipInputStream)
                        resPackages = ARSCDecoder.decode(bufInputStream, false, true, resTable).packages
                        break
                    }
                    entry = zipInputStream.nextEntry
                }
            } finally {
                bufInputStream?.close()
            }
        } catch (e: IOException) {
            throw e
        }
        return resPackages
    }

    private fun getMainPackage(resPackages: Array<ResPackage>?): ResPackage? {
        var pkg: ResPackage? = null
        if (resPackages != null && resPackages.isNotEmpty()) {
            if (resPackages.size == 1) {
                pkg = resPackages[0]
            } else {
                var id = 0
                var value = 0
                var index = 0
                var resPackage: ResPackage
                for (i in resPackages.indices) {
                    resPackage = resPackages[i]
                    if (resPackage.resSpecCount > value && !resPackage.name.equals("android", ignoreCase = true)) {
                        value = resPackage.resSpecCount
                        id = resPackage.id
                        index = i
                    }
                }

                // if id is still 0, we only have one pkgId which is "android" -> 1
                return if (id == 0) resPackages[0] else resPackages[index]
            }
        }
        return pkg
    }

    @Throws(AndrolibException::class, IOException::class)
    private fun decodeResResource(
        res: ResResource,
        inDir: File,
        xmlParser: AXmlResourceParser,
        nonValueReferences: MutableMap<String, MutableSet<String>>,
    ) {
        val fileValue = res.value as ResFileValue
        val inFileName = fileValue.strippedPath
        val typeName = res.resSpec.type.name

        try {
            val inFile = File(inDir, inFileName)
            if (!FileUtil.isLegalFile(inFile)) {
                return
            }

            if (!inFileName.endsWith(".xml")) {
                return
            }

            val inputStream = FileInputStream(inFile)
            val xmlDecoder = XmlPullResourceRefDecoder(xmlParser)
            xmlDecoder.decode(inputStream, null)
            val resource = ApkConstants.R_PREFIX + typeName + "." + inFile.name.substring(0, inFile.name.lastIndexOf('.'))
            if (!nonValueReferences.containsKey(resource)) {
                nonValueReferences[resource] = xmlDecoder.getResourceRefSet().toMutableSet()
            } else {
                nonValueReferences[resource]!!.addAll(xmlDecoder.getResourceRefSet())
            }
        } catch (ex: AndrolibException) {
            Log.e(TAG, ex.message ?: "")
        }
    }

    @Throws(IOException::class, AndrolibException::class)
    private fun decodeResValues(
        resValuesFile: ResValuesFile,
        xmlParser: XmlPullParser,
        serializer: ExtMXSerializer,
        references: MutableSet<String>,
    ) {
        val outStream = ByteArrayOutputStream()
        serializer.setOutput(outStream, null)
        serializer.startDocument(null, null)
        serializer.startTag(null, "resources")

        for (res in resValuesFile.listResources()) {
            if (resValuesFile.isSynthesized(res)) {
                continue
            }
            (res.value as ResValuesXmlSerializable).serializeToResValuesXml(serializer, res)
        }
        serializer.endTag(null, "resources")
        serializer.newLine()
        serializer.endDocument()
        serializer.flush()
        outStream.close()
        val inputStream = ByteArrayInputStream(outStream.toByteArray())
        val xmlDecoder = XmlPullResourceRefDecoder(xmlParser)
        xmlDecoder.decode(inputStream, null)
        references.addAll(xmlDecoder.getResourceRefSet())
    }

    @JvmStatic
    @Throws(IOException::class, AndrolibException::class, XmlPullParserException::class)
    fun decodeResourcesRef(
        manifestFile: File,
        arscFile: File,
        resDir: File?,
        nonValueReferences: MutableMap<String, MutableSet<String>>,
        valueReferences: MutableSet<String>,
    ) {
        if (!FileUtil.isLegalFile(manifestFile)) {
            Log.w(TAG, "File %s is illegal!", ApkConstants.MANIFEST_FILE_NAME)
            return
        }
        if (!FileUtil.isLegalFile(arscFile)) {
            Log.w(TAG, "File %s is illegal!", ApkConstants.ARSC_FILE_NAME)
            return
        }
        if (resDir != null && resDir.exists() && resDir.isDirectory) {
            // decode arsc file
            val resTable = ResTable()
            decodeArscFile(arscFile, resTable)

            val aXmlResourceParser = createAXmlParser(arscFile)
            val xmlPullParser = XmlPullParserFactory.newInstance().newPullParser()
            val serializer = createXmlSerializer()
            for (pkg in resTable.listMainPackages()) {
                aXmlResourceParser.attrDecoder.currentPackage = pkg
                for (resSource in pkg.listFiles()) {
                    decodeResResource(resSource, resDir, aXmlResourceParser, nonValueReferences)
                }

                for (valuesFile in pkg.listValuesFiles()) {
                    decodeResValues(valuesFile, xmlPullParser, serializer, valueReferences)
                }
            }

            // decode manifest file
            val xmlDecoder = XmlPullResourceRefDecoder(aXmlResourceParser)
            val inputStream: InputStream = FileInputStream(manifestFile)
            xmlDecoder.decode(inputStream, null)
            valueReferences.addAll(xmlDecoder.getResourceRefSet())
        } else {
            Log.w(TAG, "Res dir is illegal!")
        }
    }

    private fun createXmlSerializer(): ExtMXSerializer {
        val serializer = ExtMXSerializer()
        serializer.setProperty(PROPERTY_SERIALIZER_INDENTATION, "   ")
        serializer.setProperty(PROPERTY_SERIALIZER_LINE_SEPARATOR, System.lineSeparator())
        serializer.setProperty(PROPERTY_DEFAULT_ENCNDING, "utf-8")
        serializer.setDisabledAttrEscape(true)
        return serializer
    }
}

