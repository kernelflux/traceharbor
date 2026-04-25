package com.kernelflux.traceharbor.javalib.util

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

object IOUtil {

    /**
     * @return `true` iff [input] is a real ZIP/JAR; the original handler tolerated the
     * legacy "WeChat plugin" case where a file ends in `.jar` but actually contains a
     * plain text path list.
     */
    @JvmStatic
    fun isRealZipOrJar(input: File): Boolean {
        var zf: ZipFile? = null
        return try {
            zf = ZipFile(input)
            true
        } catch (e: Exception) {
            false
        } finally {
            closeQuietly(zf)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun copyFile(src: File, dest: File) {
        if (!dest.exists()) {
            dest.parentFile.mkdirs()
        }
        Files.copy(
            src.toPath(),
            dest.toPath(),
            StandardCopyOption.COPY_ATTRIBUTES,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    /**
     * Copy bytes from [is] to [os] **without** closing either stream.
     * If [buffer] is null/empty a fresh 4 KiB buffer is allocated.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun copyStream(`is`: InputStream, os: OutputStream, buffer: ByteArray?) {
        val buf = if (buffer == null || buffer.isEmpty()) ByteArray(4096) else buffer
        var bytesCopied: Int
        while (`is`.read(buf).also { bytesCopied = it } >= 0) {
            os.write(buf, 0, bytesCopied)
        }
        os.flush()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun copyStream(`is`: InputStream, os: OutputStream) {
        copyStream(`is`, os, null)
    }

    /**
     * Best-effort close. Mirrors original behaviour:
     *  - `Closeable` / `AutoCloseable` / `ZipFile` → swallow throwables.
     *  - anything else → throw `IllegalArgumentException` (caller bug, surface it).
     */
    @JvmStatic
    fun closeQuietly(obj: Any?) {
        if (obj == null) return
        when (obj) {
            is Closeable -> try { obj.close() } catch (ignored: Throwable) {}
            is AutoCloseable -> try { obj.close() } catch (ignored: Throwable) {}
            is ZipFile -> try { obj.close() } catch (ignored: Throwable) {}
            else -> throw IllegalArgumentException("obj $obj is not closeable")
        }
    }
}
