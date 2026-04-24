package com.kernelflux.traceharbor.javalib.util

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object FileUtil {
    private const val TAG = "TraceHarbor.FileUtil"

    @JvmField
    val BUFFER_SIZE: Int = 16384

    @JvmStatic
    fun isLegalFile(file: File?): Boolean =
        file != null && file.exists() && file.canRead() && file.isFile && file.length() > 0

    @JvmStatic
    fun isLegalFile(filename: String): Boolean = isLegalFile(File(filename))

    @JvmStatic
    fun getFileOrDirectorySize(directory: File?): Long {
        if (directory == null || !directory.exists()) return 0
        if (directory.isFile) return directory.length()
        var total = 0L
        directory.listFiles()?.forEach { f ->
            total += if (f.isDirectory) getFileOrDirectorySize(f) else f.length()
        }
        return total
    }

    @JvmStatic
    fun safeDeleteFile(file: File?): Boolean {
        if (file == null) return true
        if (file.exists()) {
            val deleted = file.delete()
            if (!deleted) {
                Log.e(TAG, "Failed to delete file, try to delete when exit. path: " + file.path)
                file.deleteOnExit()
            }
            return deleted
        }
        return true
    }

    @JvmStatic
    fun deleteDir(dir: String?): Boolean {
        if (dir == null) return false
        return deleteDir(File(dir))
    }

    @JvmStatic
    fun deleteDir(file: File?): Boolean {
        if (file == null || !file.exists()) return false
        if (file.isFile) {
            safeDeleteFile(file)
        } else if (file.isDirectory) {
            file.listFiles()?.let { children ->
                for (sub in children) deleteDir(sub)
                safeDeleteFile(file)
            }
        }
        return true
    }

    /** Closes the given [Closeable]. Suppresses any [IOException]. */
    @JvmStatic
    fun closeQuietly(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Failed to close resource", e)
        }
    }

    @JvmStatic
    fun closeZip(zipFile: ZipFile?) {
        try {
            zipFile?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Failed to close resource", e)
        }
    }

    @JvmStatic
    fun ensureFileDirectory(file: File?) {
        if (file == null) return
        val parent = file.parentFile
        if (!parent.exists()) parent.mkdirs()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun copyResourceUsingStream(name: String, dest: File) {
        val parent = dest.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()

        var input: InputStream? = null
        var output: FileOutputStream? = null
        try {
            input = FileUtil::class.java.getResourceAsStream("/$name")
            output = FileOutputStream(dest, false)
            val buffer = ByteArray(BUFFER_SIZE)
            var length: Int
            while (input!!.read(buffer).also { length = it } > 0) {
                output.write(buffer, 0, length)
            }
        } finally {
            closeQuietly(input)
            closeQuietly(output)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun copyFileUsingStream(source: File, dest: File) {
        val parent = dest.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()

        var input: FileInputStream? = null
        var output: FileOutputStream? = null
        try {
            input = FileInputStream(source)
            output = FileOutputStream(dest, false)
            val buffer = ByteArray(BUFFER_SIZE)
            var length: Int
            while (input.read(buffer).also { length = it } > 0) {
                output.write(buffer, 0, length)
            }
        } finally {
            closeQuietly(input)
            closeQuietly(output)
        }
    }

    @JvmStatic
    fun checkDirectory(dir: String): Boolean {
        val dirObj = File(dir)
        deleteDir(dirObj)
        if (!dirObj.exists()) dirObj.mkdirs()
        return true
    }

    @JvmStatic
    fun readFileAsString(filePath: String): String {
        if (!File(filePath).exists()) return ""
        val data = StringBuffer()
        var fileReader: Reader? = null
        var inputStream: InputStream? = null
        try {
            inputStream = FileInputStream(filePath)
            fileReader = InputStreamReader(inputStream, Charsets.UTF_8)
            val buf = CharArray(BUFFER_SIZE)
            var numRead: Int
            while (fileReader.read(buf).also { numRead = it } != -1) {
                data.append(String(buf, 0, numRead))
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "file op readFileAsString e type:%s, e msg:%s, filePath:%s",
                e.javaClass.simpleName, e.message, filePath,
            )
            return ""
        } finally {
            try {
                closeQuietly(fileReader)
                closeQuietly(inputStream)
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "file op readFileAsString close e type:%s, e msg:%s, filePath:%s",
                    e.javaClass.simpleName, e.message, filePath,
                )
            }
        }
        return data.toString()
    }

    @JvmStatic
    fun unzip(filePath: String, destFolder: String) {
        var zipFile: ZipFile? = null
        var bos: BufferedOutputStream? = null
        var bis: BufferedInputStream? = null
        try {
            zipFile = ZipFile(filePath)
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry: ZipEntry = entries.nextElement()
                val entryName = entry.name

                if (Util.preventZipSlip(File(destFolder), entryName)) {
                    Log.e(TAG, "writeEntry entry %s failed!", entryName)
                    continue
                }

                if (entry.isDirectory) {
                    File(destFolder, entry.name).mkdirs()
                    continue
                }
                bis = BufferedInputStream(zipFile.getInputStream(entry))
                val file = File(destFolder, entry.name)
                file.parentFile?.let { p -> if (!p.exists()) p.mkdirs() }
                val data = ByteArray(BUFFER_SIZE)
                bos = BufferedOutputStream(FileOutputStream(file), data.size)
                var count: Int
                while (bis.read(data, 0, data.size).also { count = it } != -1) {
                    bos.write(data, 0, count)
                }
                bos.flush()
                closeQuietly(bos)
            }
        } catch (e: Exception) {
            // ignore — original swallowed all exceptions silently.
        } finally {
            closeZip(zipFile)
            closeQuietly(bis)
            closeQuietly(bos)
        }
    }

    @JvmStatic
    fun zip(srcFolder: String, destZip: String) {
        var fos: FileOutputStream? = null
        var zos: ZipOutputStream? = null
        try {
            val dir = File(srcFolder)
            val filesListInDir = ArrayList<String>()
            populateFilesList(filesListInDir, dir)
            fos = FileOutputStream(destZip)
            zos = ZipOutputStream(fos)
            for (filePath in filesListInDir) {
                val ze = ZipEntry(filePath.substring(dir.absolutePath.length + 1))
                zos.putNextEntry(ze)
                val fis = FileInputStream(filePath)
                val buffer = ByteArray(BUFFER_SIZE)
                var len: Int
                while (fis.read(buffer).also { len = it } > 0) {
                    zos.write(buffer, 0, len)
                }
                zos.closeEntry()
                fis.close()
            }
            zos.close()
            fos.close()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            closeQuietly(zos)
            closeQuietly(fos)
        }
    }

    @Throws(IOException::class)
    private fun populateFilesList(filesListInDir: MutableList<String>, dir: File) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isFile) {
                filesListInDir.add(file.absolutePath)
            } else {
                populateFilesList(filesListInDir, file)
            }
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun addZipEntry(zipOutputStream: ZipOutputStream, zipEntry: ZipEntry, inputStream: InputStream) {
        try {
            zipOutputStream.putNextEntry(zipEntry)
            val buffer = ByteArray(BUFFER_SIZE)
            var length: Int
            while (inputStream.read(buffer, 0, buffer.size).also { length = it } != -1) {
                zipOutputStream.write(buffer, 0, length)
                zipOutputStream.flush()
            }
        } catch (e: ZipException) {
            Log.e(TAG, "addZipEntry err!")
        } finally {
            closeQuietly(inputStream)
            zipOutputStream.closeEntry()
        }
    }

    @JvmStatic
    fun isClassFile(string: String?): Boolean {
        if (string == null) return false
        return CLASS_FILE_PATTERN.matcher(string).find()
    }

    private val CLASS_FILE_PATTERN: Pattern = Pattern.compile("^[\\S|\\s]*.class$")
}
