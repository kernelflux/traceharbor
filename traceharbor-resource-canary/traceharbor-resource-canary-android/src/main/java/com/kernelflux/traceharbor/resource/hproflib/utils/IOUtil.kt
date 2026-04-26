package com.kernelflux.traceharbor.resource.hproflib.utils

import com.kernelflux.traceharbor.resource.hproflib.model.ID
import com.kernelflux.traceharbor.resource.hproflib.model.Type
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset

@Suppress("unused")
object IOUtil {
    @Throws(IOException::class)
    fun readLEShort(input: InputStream): Short {
        val b0 = input.read()
        val b1 = input.read()
        if ((b0 or b1) < 0) {
            throw EOFException()
        }
        return ((b1 shl 8) or b0).toShort()
    }

    @Throws(IOException::class)
    fun readLEInt(input: InputStream): Int {
        val b0 = input.read()
        val b1 = input.read()
        val b2 = input.read()
        val b3 = input.read()
        if ((b0 or b1 or b2 or b3) < 0) {
            throw EOFException()
        }
        return (b3 shl 24) + (b2 shl 16) + (b1 shl 8) + b0
    }

    @Throws(IOException::class)
    fun readLELong(input: InputStream): Long {
        val length = 8
        val buf = ByteArray(length)
        readFully(input, buf, 0, length.toLong())
        return ((buf[7].toLong() shl 56) +
            ((buf[6].toInt() and 0xFF).toLong() shl 48) +
            ((buf[5].toInt() and 0xFF).toLong() shl 40) +
            ((buf[4].toInt() and 0xFF).toLong() shl 32) +
            ((buf[3].toInt() and 0xFF).toLong() shl 24) +
            ((buf[2].toInt() and 0xFF) shl 16) +
            ((buf[1].toInt() and 0xFF) shl 8) +
            (buf[0].toInt() and 0xFF).toLong())
    }

    @Throws(IOException::class)
    fun readBEShort(input: InputStream): Short {
        val b0 = input.read()
        val b1 = input.read()
        if ((b0 or b1) < 0) {
            throw EOFException()
        }
        return ((b0 shl 8) or b1).toShort()
    }

    @Throws(IOException::class)
    fun readBEInt(input: InputStream): Int {
        val b0 = input.read()
        val b1 = input.read()
        val b2 = input.read()
        val b3 = input.read()
        if ((b0 or b1 or b2 or b3) < 0) {
            throw EOFException()
        }
        return (b0 shl 24) + (b1 shl 16) + (b2 shl 8) + b3
    }

    @Throws(IOException::class)
    fun readBELong(input: InputStream): Long {
        val length = 8
        val buf = ByteArray(length)
        readFully(input, buf, 0, length.toLong())
        return ((buf[0].toLong() shl 56) +
            ((buf[1].toInt() and 0xFF).toLong() shl 48) +
            ((buf[2].toInt() and 0xFF).toLong() shl 40) +
            ((buf[3].toInt() and 0xFF).toLong() shl 32) +
            ((buf[4].toInt() and 0xFF).toLong() shl 24) +
            ((buf[5].toInt() and 0xFF) shl 16) +
            ((buf[6].toInt() and 0xFF) shl 8) +
            (buf[7].toInt() and 0xFF).toLong())
    }

    @Throws(IOException::class)
    fun readFully(input: InputStream, buf: ByteArray, off: Int, length: Long) {
        var n = 0
        while (n < length) {
            val count = input.read(buf, n, (length - n).toInt())
            if (count < 0) {
                throw EOFException()
            }
            n += count
        }
    }

    @Throws(IOException::class)
    fun readNullTerminatedString(input: InputStream): String {
        val sb = StringBuilder()
        var c = input.read()
        while (c != 0) {
            sb.append(c.toChar())
            c = input.read()
        }
        return sb.toString()
    }

    @Throws(IOException::class)
    fun readString(input: InputStream, length: Long): String {
        val buf = ByteArray(length.toInt())
        readFully(input, buf, 0, length)
        return String(buf, Charset.forName("UTF-8"))
    }

    @Throws(IOException::class)
    fun readID(input: InputStream, idSize: Int): ID {
        val idBytes = ByteArray(idSize)
        readFully(input, idBytes, 0, idSize.toLong())
        return ID(idBytes)
    }

    @Throws(IOException::class)
    fun readValue(input: InputStream, type: Type, idSize: Int): Any? =
        when (type) {
            Type.OBJECT -> readID(input, idSize)
            Type.BOOLEAN -> input.read() != 0
            Type.CHAR -> readBEShort(input).toInt().toChar()
            Type.FLOAT -> Float.fromBits(readBEInt(input))
            Type.DOUBLE -> Double.fromBits(readBELong(input))
            Type.BYTE -> input.read().toByte()
            Type.SHORT -> readBEShort(input)
            Type.INT -> readBEInt(input)
            Type.LONG -> readBELong(input)
        }

    @Throws(IOException::class)
    fun skip(input: InputStream, n: Long) {
        var skipped = 0L
        while (skipped < n) {
            val actualSkipped = input.skip(n - skipped)
            if (actualSkipped < 0) {
                throw EOFException()
            }
            skipped += actualSkipped
        }
    }

    @Throws(IOException::class)
    fun skipValue(input: InputStream, type: Type, idSize: Int): Int {
        val actualIdSize = type.getSize(idSize)
        skip(input, actualIdSize.toLong())
        return actualIdSize
    }

    @Throws(IOException::class)
    fun writeLEShort(output: OutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write((value ushr 8) and 0xFF)
    }

    @Throws(IOException::class)
    fun writeLEInt(output: OutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write((value ushr 8) and 0xFF)
        output.write((value ushr 16) and 0xFF)
        output.write((value ushr 24) and 0xFF)
    }

    @Throws(IOException::class)
    fun writeLELong(output: OutputStream, value: Long) {
        val length = 8
        val buf = ByteArray(length)
        buf[7] = (value ushr 56).toByte()
        buf[6] = (value ushr 48).toByte()
        buf[5] = (value ushr 40).toByte()
        buf[4] = (value ushr 32).toByte()
        buf[3] = (value ushr 24).toByte()
        buf[2] = (value ushr 16).toByte()
        buf[1] = (value ushr 8).toByte()
        buf[0] = value.toByte()
        output.write(buf, 0, length)
    }

    @Throws(IOException::class)
    fun writeBEShort(output: OutputStream, value: Int) {
        output.write((value ushr 8) and 0xFF)
        output.write(value and 0xFF)
    }

    @Throws(IOException::class)
    fun writeBEInt(output: OutputStream, value: Int) {
        output.write((value ushr 24) and 0xFF)
        output.write((value ushr 16) and 0xFF)
        output.write((value ushr 8) and 0xFF)
        output.write(value and 0xFF)
    }

    @Throws(IOException::class)
    fun writeBELong(output: OutputStream, value: Long) {
        val length = 8
        val buf = ByteArray(length)
        buf[0] = (value ushr 56).toByte()
        buf[1] = (value ushr 48).toByte()
        buf[2] = (value ushr 40).toByte()
        buf[3] = (value ushr 32).toByte()
        buf[4] = (value ushr 24).toByte()
        buf[5] = (value ushr 16).toByte()
        buf[6] = (value ushr 8).toByte()
        buf[7] = value.toByte()
        output.write(buf, 0, length)
    }

    @Throws(IOException::class)
    fun writeString(output: OutputStream, text: String) {
        val length = text.length
        output.write(text.toByteArray(Charset.forName("UTF-8")), 0, length)
    }

    @Throws(IOException::class)
    fun writeNullTerminatedString(output: OutputStream, text: String) {
        output.write(text.toByteArray(Charset.forName("UTF-8")))
        output.write(0)
    }

    @Throws(IOException::class)
    fun writeID(output: OutputStream, id: ID) {
        output.write(id.getBytes())
    }

    @Throws(IOException::class)
    fun writeValue(output: OutputStream, value: Any?) {
        when (value) {
            null -> throw IllegalArgumentException("value is null.")
            is ID -> writeID(output, value)
            is Boolean -> output.write(if (value) 1 else 0)
            is Char -> writeBEShort(output, value.code)
            is Float -> writeBEInt(output, value.toRawBits())
            is Double -> writeBELong(output, value.toRawBits())
            is Byte -> output.write(value.toInt())
            is Short -> writeBEShort(output, value.toInt())
            is Int -> writeBEInt(output, value)
            is Long -> writeBELong(output, value)
            else -> throw IllegalArgumentException("bad value type: ${value.javaClass.name}")
        }
    }

    @Throws(IOException::class)
    fun skip(output: OutputStream, size: Long) {
        val emptyBuf = ByteArray(4096)
        for (i in 0 until (size shr 12)) {
            output.write(emptyBuf)
        }
        output.write(emptyBuf, 0, (size and ((1L shl 12) - 1)).toInt())
    }
}

