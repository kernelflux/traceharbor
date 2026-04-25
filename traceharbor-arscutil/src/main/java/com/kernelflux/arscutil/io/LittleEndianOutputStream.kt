package com.kernelflux.arscutil.io

import java.io.FileNotFoundException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LittleEndianOutputStream : OutputStream {
    private val original: RandomAccessFile

    @Throws(FileNotFoundException::class)
    constructor(file: String) : this(RandomAccessFile(file, "rw"))

    constructor(original: RandomAccessFile) {
        this.original = original
    }

    override fun write(b: Int) {
        original.write(b)
    }

    fun writeShort(data: Short) {
        val byteBuffer = ByteBuffer.allocate(2)
        byteBuffer.clear()
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.putShort(data)
        byteBuffer.flip()
        original.write(byteBuffer.array())
    }

    fun writeInt(data: Int) {
        val byteBuffer = ByteBuffer.allocate(4)
        byteBuffer.clear()
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.putInt(data)
        byteBuffer.flip()
        original.write(byteBuffer.array())
    }

    fun writeByte(data: Byte) {
        original.write(data.toInt())
    }

    fun writeByte(buffer: ByteArray) {
        original.write(buffer)
    }

    fun writeByte(buffer: ByteArray, offset: Int, length: Int) {
        original.write(buffer, offset, length)
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
