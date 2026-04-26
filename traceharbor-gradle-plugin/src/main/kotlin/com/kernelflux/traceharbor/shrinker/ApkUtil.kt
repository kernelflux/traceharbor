/*
 * Tencent is pleased to support the open source community by making wechat-matrix available.
 * Copyright (C) 2018 THL A29 Limited, a Tencent company. All rights reserved.
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kernelflux.traceharbor.shrinker

import com.android.builder.model.SigningConfig
import com.kernelflux.traceharbor.javalib.util.Pair
import com.kernelflux.traceharbor.javalib.util.Util
import com.kernelflux.traceharbor.resguard.ResguardMapping
import org.gradle.api.GradleException
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Array as ReflectArray
import java.util.ArrayList
import java.util.Arrays
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ApkUtil {

    @JvmStatic
    fun parseResourceId(resId: String?): String {
        if (!Util.isNullOrNil(resId) && resId!!.startsWith("0x")) {
            if (resId.length == 10) {
                return resId
            } else if (resId.length < 10) {
                val strBuilder = StringBuilder(resId)
                repeat(10 - resId.length) {
                    strBuilder.append('0')
                }
                return strBuilder.toString()
            }
        }
        return ""
    }

    @JvmStatic
    internal fun entryToResourceName(entry: String?, resguardMapping: ResguardMapping?): String {
        var resourceName = ""
        if (!Util.isNullOrNil(entry)) {
            val typeName: String = if (resguardMapping != null) {
                val lastIndex = entry!!.lastIndexOf('/')
                if (lastIndex == -1) return ""
                val obfuscatedPath = entry.substring(0, lastIndex)
                val originPath = resguardMapping.originPath(obfuscatedPath)
                parseEntryResourceType("$originPath/")
            } else {
                parseEntryResourceType(entry!!)
            }

            var resName = entry.substring(entry.lastIndexOf('/') + 1, entry.indexOf('.'))
            if (resguardMapping != null) {
                resName = resguardMapping.originID(typeName, resName)
            }

            if (!Util.isNullOrNil(typeName) && !Util.isNullOrNil(resName)) {
                resourceName = "R.$typeName.$resName"
            }
        }
        return resourceName
    }

    @JvmStatic
    fun parseEntryResourceType(entry: String): String {
        val prefixLength = entry.indexOf('/')
        if (prefixLength == -1) return ""
        if (!Util.isNullOrNil(entry)) {
            var typeName = entry.substring(prefixLength + 1, entry.lastIndexOf('/'))
            if (!Util.isNullOrNil(typeName)) {
                val index = typeName.indexOf('-')
                if (index >= 0) {
                    typeName = typeName.substring(0, index)
                }
                return typeName
            }
        }
        return ""
    }

    @JvmStatic
    fun isSameResourceType(entries: Set<String>, obfuscatedDirName: String?): Boolean {
        var resType = ""
        for (entry in entries) {
            if (!Util.isNullOrNil(entry)) {
                if (Util.isNullOrNil(resType)) {
                    resType = parseEntryResourceType(entry)
                    continue
                }
                if (resType != parseEntryResourceType(entry)) {
                    return false
                }
            } else {
                return false
            }
        }
        return !Util.isNullOrNil(resType)
    }

    @JvmStatic
    fun parseResourceType(resource: String?): String {
        if (resource.isNullOrEmpty()) return ""
        return resource.substring(resource.indexOf('.') + 1, resource.lastIndexOf('.'))
    }

    @JvmStatic
    fun parseResourceName(resource: String?): String {
        if (resource.isNullOrEmpty()) return ""
        return resource.substring(resource.lastIndexOf('.') + 1)
    }

    @Throws(IOException::class)
    @JvmStatic
    fun readFileContent(inputStream: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        output.use {
            BufferedInputStream(inputStream).use { bufferedInput ->
                val buffer = ByteArray(4096)
                while (true) {
                    val len = bufferedInput.read(buffer)
                    if (len == -1) {
                        break
                    }
                    output.write(buffer, 0, len)
                }
            }
        }
        return output.toByteArray()
    }

    @Throws(IOException::class)
    @JvmStatic
    fun unzipEntry(zipFile: ZipFile, zipEntry: ZipEntry, destFile: String) {
        val file = File(destFile)
        file.parentFile?.mkdirs()
        if (!file.exists()) {
            file.createNewFile()
        }
        BufferedOutputStream(FileOutputStream(file)).use { outputStream ->
            zipFile.getInputStream(zipEntry).use { inputStream ->
                outputStream.write(readFileContent(inputStream))
            }
        }
    }

    @Throws(IOException::class)
    @JvmStatic
    fun addZipEntry(zipOutputStream: ZipOutputStream, zipEntry: ZipEntry, file: File) {
        val writeEntry = ZipEntry(zipEntry.name)
        val content = FileInputStream(file).use { inputStream -> readFileContent(inputStream) }
        if (zipEntry.method == ZipEntry.DEFLATED) {
            writeEntry.method = ZipEntry.DEFLATED
        } else {
            writeEntry.method = ZipEntry.STORED
            val crc32 = CRC32()
            crc32.update(content)
            writeEntry.crc = crc32.value
        }
        writeEntry.size = content.size.toLong()
        zipOutputStream.putNextEntry(writeEntry)
        zipOutputStream.write(content)
        zipOutputStream.flush()
        zipOutputStream.closeEntry()
    }

    @Throws(IOException::class)
    @JvmStatic
    fun addZipEntry(zipOutputStream: ZipOutputStream, zipEntry: ZipEntry, zipFile: ZipFile) {
        val writeEntry = ZipEntry(zipEntry.name)
        val content = zipFile.getInputStream(zipEntry).use { inputStream -> readFileContent(inputStream) }
        if (zipEntry.method == ZipEntry.DEFLATED) {
            writeEntry.method = ZipEntry.DEFLATED
        } else {
            writeEntry.method = ZipEntry.STORED
            writeEntry.crc = zipEntry.crc
            writeEntry.size = zipEntry.size
        }
        zipOutputStream.putNextEntry(writeEntry)
        zipOutputStream.write(content)
        zipOutputStream.flush()
        zipOutputStream.closeEntry()
    }

    @Throws(IOException::class)
    @JvmStatic
    fun addZipEntry(zipOutputStream: ZipOutputStream, zipEntry: ZipEntry, newName: String, zipFile: ZipFile) {
        val writeEntry = ZipEntry(newName)
        val content = zipFile.getInputStream(zipEntry).use { inputStream -> readFileContent(inputStream) }
        if (zipEntry.method == ZipEntry.DEFLATED) {
            writeEntry.method = ZipEntry.DEFLATED
        } else {
            writeEntry.method = ZipEntry.STORED
            writeEntry.crc = zipEntry.crc
            writeEntry.size = zipEntry.size
        }
        zipOutputStream.putNextEntry(writeEntry)
        zipOutputStream.write(content)
        zipOutputStream.flush()
        zipOutputStream.closeEntry()
    }

    @Throws(GradleException::class, IOException::class, InterruptedException::class)
    @JvmStatic
    fun sevenZipFile(sevenZipPath: String, inputFile: String, outputFile: String, deflated: Boolean) {
        val sevenZip = File(sevenZipPath)
        if (!sevenZip.canExecute()) {
            sevenZip.setExecutable(true)
        }
        val processBuilder = ProcessBuilder()
        processBuilder.command(sevenZipPath, "a", "-tzip", outputFile, inputFile, if (deflated) "-mx5" else "-mx0")
        val process = processBuilder.start()
        waitForProcessOutput(process)
        if (process.exitValue() != 0) {
            throw GradleException("7zip apk occur error!")
        }
    }

    @Throws(InterruptedException::class, IOException::class)
    @JvmStatic
    fun waitForProcessOutput(process: Process) {
        process.waitFor()

        val bytes = ByteArray(1024)
        while (process.inputStream.read(bytes) > 0) {
            System.out.write(bytes)
        }
        while (process.errorStream.read(bytes) > 0) {
            System.err.write(bytes)
        }
    }

    @Throws(IOException::class)
    @JvmStatic
    fun readResourceTxtFile(
        resTxtFile: File,
        resourceMap: MutableMap<String, Int>,
        styleableMap: MutableMap<String, Array<Pair<String, Int>>>
    ) {
        BufferedReader(FileReader(resTxtFile)).use { bufferedReader ->
            var line: String? = bufferedReader.readLine()
            var styleable = false
            var styleableName = ""
            val styleableAttrs = ArrayList<String>()
            while (line != null) {
                val columns = line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (columns.size >= 4) {
                    val resourceName = "R.${columns[1]}.${columns[2]}"
                    if (!columns[0].endsWith("[]") && columns[3].startsWith("0x")) {
                        if (styleable) {
                            styleable = false
                            styleableName = ""
                        }
                        val resId = parseResourceId(columns[3])
                        if (!Util.isNullOrNil(resId)) {
                            resourceMap[resourceName] = Integer.decode(resId)
                        }
                    } else if (columns[1] == "styleable") {
                        if (columns[0].endsWith("[]")) {
                            if (columns.size > 5) {
                                styleableAttrs.clear()
                                styleable = true
                                styleableName = "R.${columns[1]}.${columns[2]}"
                                for (i in 4 until columns.size - 1) {
                                    if (columns[i].endsWith(",")) {
                                        styleableAttrs.add(columns[i].substring(0, columns[i].length - 1))
                                    } else {
                                        styleableAttrs.add(columns[i])
                                    }
                                }
                                @Suppress("UNCHECKED_CAST")
                                styleableMap[styleableName] =
                                    ReflectArray.newInstance(Pair::class.java, styleableAttrs.size) as Array<Pair<String, Int>>
                            }
                        } else if (styleable && !Util.isNullOrNil(styleableName)) {
                            val index = columns[3].toInt()
                            val name = "R.${columns[1]}.${columns[2]}"
                            styleableMap[styleableName]?.set(
                                index,
                                Pair(name, Integer.decode(parseResourceId(styleableAttrs[index])))
                            )
                        }
                    } else if (styleable) {
                        styleable = false
                        styleableName = ""
                    }
                }
                line = bufferedReader.readLine()
            }
        }
    }

    @Throws(IOException::class)
    @JvmStatic
    fun shrinkResourceTxtFile(
        resourceTxt: String,
        resourceMap: Map<String, Int>,
        styleableMap: Map<String, Array<Pair<String, Int>>>
    ) {
        BufferedWriter(FileWriter(resourceTxt)).use { bufferedWriter ->
            for (res in resourceMap.keys) {
                val strBuilder = StringBuilder()
                strBuilder.append("int").append(" ")
                    .append(res.substring(2, res.indexOf('.', 2))).append(" ")
                    .append(res.substring(res.indexOf('.', 2) + 1)).append(" ")
                    .append("0x").append(Integer.toHexString(resourceMap[res] ?: 0))
                bufferedWriter.write(strBuilder.toString())
                bufferedWriter.newLine()
            }
            for (styleable in styleableMap.keys) {
                val styleableAttrs = styleableMap[styleable] ?: continue
                val strBuilder = StringBuilder()
                strBuilder.append("int[]").append(" ")
                    .append("styleable").append(" ")
                    .append(styleable.substring(styleable.indexOf('.', 2) + 1)).append(" ")
                    .append("{ ")
                for (i in styleableAttrs.indices) {
                    val value = styleableAttrs[i].right ?: 0
                    if (i != styleableAttrs.size - 1) {
                        strBuilder.append("0x").append(Integer.toHexString(value)).append(", ")
                    } else {
                        strBuilder.append("0x").append(Integer.toHexString(value))
                    }
                }
                strBuilder.append(" }")
                bufferedWriter.write(strBuilder.toString())
                bufferedWriter.newLine()
                for (i in styleableAttrs.indices) {
                    val stringBuilder = StringBuilder()
                    stringBuilder.append("int").append(" ")
                        .append("styleable").append(" ")
                        .append(styleableAttrs[i].left).append(" ")
                        .append(i)
                    bufferedWriter.write(stringBuilder.toString())
                    bufferedWriter.newLine()
                }
            }
        }
    }

    @Throws(GradleException::class, IOException::class, InterruptedException::class)
    @JvmStatic
    fun signApk(apkFilePath: String, apksigner: String, signingConfig: SigningConfig?) {
        if (signingConfig == null) {
            throw GradleException("signingConfig is null")
        }
        val processBuilder = ProcessBuilder()
        val storeFile = signingConfig.storeFile ?: throw GradleException("signingConfig.storeFile is null")
        val commandList = ArrayList(
            Arrays.asList(
                apksigner, "sign", "-v",
                "--ks", storeFile.absolutePath,
                "--ks-pass", "pass:${signingConfig.storePassword}",
                "--key-pass", "pass:${signingConfig.keyPassword}",
                "--ks-key-alias", signingConfig.keyAlias
            )
        )

        if (signingConfig.isV1SigningEnabled) {
            commandList.add("--v1-signing-enabled")
        }
        if (signingConfig.isV2SigningEnabled) {
            commandList.add("--v2-signing-enabled")
        }
        commandList.add(apkFilePath)
        processBuilder.command(commandList)
        val process = processBuilder.start()
        waitForProcessOutput(process)
        if (process.exitValue() != 0) {
            throw GradleException("sign apk occur error!")
        }
    }

    @Throws(GradleException::class, IOException::class, InterruptedException::class)
    @JvmStatic
    fun zipAlignApk(inputFile: String, outputFile: String, zipAlignPath: String) {
        val processBuilder = ProcessBuilder()
        processBuilder.command(zipAlignPath, "-f", "4", inputFile, outputFile)
        val process = processBuilder.start()
        waitForProcessOutput(process)
        if (process.exitValue() != 0) {
            throw GradleException("zipalign apk occur error!")
        }
    }
}

