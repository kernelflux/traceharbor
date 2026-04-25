package com.kernelflux.arscutil.io

import java.io.FileNotFoundException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LittleEndianInputStream : InputStream {
    private val original: RandomAccessFile

    @Throws(FileNotFoundException::class)
    constructor(file: String) : this(RandomAccessFile(file, "r"))

    constructor(original: RandomAccessFile) {
        this.original = original
    }

    override fun read(): Int = original.read()

    fun readShort(): Short {
        val byteBuffer = ByteBuffer.allocate(2)
        byteBuffer.clear()
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.put(original.readByte())
        byteBuffer.put(original.readByte())
        byteBuffer.flip()
        return byteBuffer.short
    }

    fun readInt(): Int {
        val byteBuffer = ByteBuffer.allocate(4)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.clear()
        for (i in 1..4) {
            byteBuffer.put(original.readByte())
        }
        byteBuffer.flip()
        return byteBuffer.int
    }

    fun readByte(): Byte = original.readByte()

    fun readByte(buffer: ByteArray) {
        readByte(buffer, 0, buffer.size)
    }

    fun readByte(buffer: ByteArray, offset: Int, length: Int) {
        val byteBuffer = ByteBuffer.allocate(length)
        byteBuffer.clear()
        for (i in 1..length) {
            byteBuffer.put(original.readByte())
        }
        byteBuffer.flip()
        byteBuffer.get(buffer, offset, length)
    }

    fun seek(pos: Long) {
        original.seek(pos)
    }

    fun getFilePointer(): Long = original.filePointer

    fun getFileLength(): Long = original.length()

    override fun close() {
        super.close()
        original.close()
    }
}
