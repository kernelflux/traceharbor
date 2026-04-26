package com.kernelflux.traceharbor.arscutil.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

class ResTypeSpec : ResChunk() {
    var id: Byte = 0
    var reserved0: Byte = 0
    var reserved1: Short = 0
    var entryCount: Int = 0
    var configFlags: ByteArray? = null

    override fun toBytes(): ByteArray {
        val byteBuffer = ByteBuffer.allocate(chunkSize)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.clear()
        byteBuffer.putShort(type)
        byteBuffer.putShort(headSize)
        byteBuffer.putInt(chunkSize)
        byteBuffer.put(id)
        byteBuffer.put(reserved0)
        byteBuffer.putShort(reserved1)
        byteBuffer.putInt(entryCount)
        if (headPadding > 0) {
            byteBuffer.put(ByteArray(headPadding))
        }
        configFlags?.let { byteBuffer.put(it) }
        if (chunkPadding > 0) {
            byteBuffer.put(ByteArray(chunkPadding))
        }
        byteBuffer.flip()
        return byteBuffer.array()
    }
}
