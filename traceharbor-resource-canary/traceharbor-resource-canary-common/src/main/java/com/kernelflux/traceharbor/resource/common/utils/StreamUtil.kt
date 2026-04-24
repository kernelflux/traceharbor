package com.kernelflux.traceharbor.resource.common.utils

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object StreamUtil {

    @JvmStatic
    fun closeQuietly(target: Any?) {
        if (target == null) return
        try {
            when (target) {
                is Closeable -> target.close()
                is ZipFile   -> target.close()
            }
        } catch (ignored: Throwable) {
            // Ignored.
        }
    }

    @JvmStatic
    fun preventZipSlip(output: File, zipEntryName: String): Boolean {
        return try {
            zipEntryName.contains("..") &&
                File(output, zipEntryName).canonicalPath
                    .startsWith(output.canonicalPath + File.separator)
        } catch (e: IOException) {
            e.printStackTrace()
            true
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun extractZipEntry(zipFile: ZipFile, targetEntry: ZipEntry, output: File) {
        if (preventZipSlip(output, targetEntry.name)) {
            throw IllegalStateException("extractZipEntry entry ${targetEntry.name} failed!")
        }

        var input: InputStream? = null
        var out: OutputStream? = null
        try {
            input = BufferedInputStream(zipFile.getInputStream(targetEntry))
            out   = BufferedOutputStream(FileOutputStream(output))
            val buffer = ByteArray(4096)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                out.write(buffer, 0, read)
            }
        } finally {
            closeQuietly(out)
            closeQuietly(input)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun copyFileToStream(input: File, out: OutputStream) {
        var src: InputStream? = null
        val buffer = ByteArray(4096)
        try {
            src = BufferedInputStream(FileInputStream(input))
            while (true) {
                val read = src.read(buffer)
                if (read <= 0) break
                out.write(buffer, 0, read)
            }
            out.flush()
        } finally {
            closeQuietly(src)
        }
    }
}
