package com.kernelflux.arscutil.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class ResStringBlock : ResChunk() {
    var stringCount: Int = 0
    var styleCount: Int = 0
    var flag: Int = 0
    var stringStart: Int = 0
    var styleStart: Int = 0
    var stringOffsets: MutableList<Int>? = null
    var styleOffsets: MutableList<Int>? = null
    var strings: MutableList<ByteBuffer>? = null
    var styles: ByteArray? = null
    var stringIndexMap: MutableMap<String, Int>? = null

    val charSet: Charset
        get() = if ((flag and ArscConstants.RES_STRING_POOL_UTF8_FLAG) != 0) {
            StandardCharsets.UTF_8
        } else {
            StandardCharsets.UTF_16LE
        }

    fun refresh() {
        chunkSize = 0
        chunkSize += headSize
        chunkSize += stringCount * 4
        chunkSize += styleCount * 4

        val localStrings = strings
        if (localStrings != null) {
            stringStart = headSize + styleCount * 4 + stringCount * 4

            stringIndexMap?.let { map ->
                map.clear()
                for (i in 0 until stringCount) {
                    map[resolveStringPoolEntry(localStrings[i].array(), charSet)] = i
                }
            }

            val offsets = stringOffsets
            offsets?.clear()
            if (stringCount > 0 && offsets != null) {
                offsets.add(0)
                for (i in 1 until stringCount) {
                    offsets.add(offsets[i - 1] + localStrings[i - 1].limit())
                }
                if (styleCount > 0) {
                    styleStart = stringStart + offsets[stringCount - 1] + localStrings[stringCount - 1].limit()
                }
                for (buffer in localStrings) {
                    chunkSize += buffer.limit()
                }
            }
        }

        styles?.let { chunkSize += it.size }
        if (chunkSize % 4 != 0) {
            chunkPadding = 4 - chunkSize % 4
            chunkSize += chunkPadding
        } else {
            chunkPadding = 0
        }
    }

    override fun toBytes(): ByteArray {
        val byteBuffer = ByteBuffer.allocate(chunkSize)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.clear()
        byteBuffer.putShort(type)
        byteBuffer.putShort(headSize)
        byteBuffer.putInt(chunkSize)
        byteBuffer.putInt(stringCount)
        byteBuffer.putInt(styleCount)
        byteBuffer.putInt(flag)
        byteBuffer.putInt(stringStart)
        byteBuffer.putInt(styleStart)
        if (headPadding > 0) {
            byteBuffer.put(ByteArray(headPadding))
        }
        stringOffsets?.forEach { byteBuffer.putInt(it) }
        styleOffsets?.forEach { byteBuffer.putInt(it) }
        strings?.forEach { byteBuffer.put(it.array()) }
        styles?.let { byteBuffer.put(it) }
        if (chunkPadding > 0) {
            byteBuffer.put(ByteArray(chunkPadding))
        }
        byteBuffer.flip()
        return byteBuffer.array()
    }

    companion object {
        @JvmStatic
        fun resolveStringPoolEntry(buffer: ByteArray, charSet: Charset): String {
            var str: String
            var len: Int
            if (charSet == StandardCharsets.UTF_8) {
                len = buffer[0].toInt()
                if ((len and 0x80) != 0) {
                    val high = buffer[1]
                    len = ((len and 0x7f) shl 8) or high.toInt()
                }
                str = String(buffer, 2, buffer.size - 2 - 1, charSet)
            } else {
                val byteBuffer = ByteBuffer.allocate(4)
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
                byteBuffer.clear()
                byteBuffer.put(buffer, 0, 2)
                byteBuffer.flip()
                len = byteBuffer.short.toInt()
                if ((len and 0x8000) != 0) {
                    val high = byteBuffer.short
                    len = ((len and 0x7fff) shl 16) or high.toInt()
                }
                str = String(buffer, byteBuffer.limit(), buffer.size - 4, charSet)
            }
            return str
        }

        @JvmStatic
        fun encodeStringPoolEntry(str: String, charSet: Charset): ByteArray {
            val content = str.toByteArray(charSet)
            val len = str.length
            val resultBuf: ByteBuffer
            if (charSet == StandardCharsets.UTF_8) {
                resultBuf = ByteBuffer.allocate(content.size + 2 + 1)
                resultBuf.order(ByteOrder.LITTLE_ENDIAN)
                if (len > 0xFF) {
                    resultBuf.put((((len and 0x7F00) shr 8) or 0x80).toByte())
                    resultBuf.put((len and 0xFF).toByte())
                } else {
                    resultBuf.put((len and 0xFF).toByte())
                    resultBuf.put((len and 0xFF).toByte())
                }
            } else {
                if (len > 0xFFFF) {
                    resultBuf = ByteBuffer.allocate(content.size + 4 + 2)
                    resultBuf.order(ByteOrder.LITTLE_ENDIAN)
                    resultBuf.putShort((((len and 0x7FFF0000) shr 16) or 0x8000).toShort())
                    resultBuf.putShort((len and 0xFFFF).toShort())
                } else {
                    resultBuf = ByteBuffer.allocate(content.size + 2 + 2)
                    resultBuf.order(ByteOrder.LITTLE_ENDIAN)
                    resultBuf.putShort((len and 0xFFFF).toShort())
                }
            }
            resultBuf.put(content)
            resultBuf.rewind()
            return resultBuf.array()
        }
    }
}
