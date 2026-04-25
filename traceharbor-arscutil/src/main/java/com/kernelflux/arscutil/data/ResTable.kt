package com.kernelflux.arscutil.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

class ResTable : ResChunk() {
    var packageCount: Int = 0
    var globalStringPool: ResStringBlock? = null
    var packages: Array<ResPackage>? = null

    fun refresh() {
        recomputeChunkSize()
    }

    private fun recomputeChunkSize() {
        chunkSize = 0
        chunkSize += headSize
        globalStringPool?.let { chunkSize += it.chunkSize }
        packages?.forEach { chunkSize += it.chunkSize }
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
        byteBuffer.putInt(packageCount)
        if (headPadding > 0) {
            byteBuffer.put(ByteArray(headPadding))
        }
        globalStringPool?.let { byteBuffer.put(it.toBytes()) }
        packages?.forEach { byteBuffer.put(it.toBytes()) }
        if (chunkPadding > 0) {
            byteBuffer.put(ByteArray(chunkPadding))
        }
        byteBuffer.flip()
        return byteBuffer.array()
    }
}
