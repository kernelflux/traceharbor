package com.kernelflux.arscutil.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

class ResConfig {
    var size: Int = 0
    var content: ByteArray? = null

    fun toBytes(): ByteArray {
        val byteBuffer = ByteBuffer.allocate(size)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.clear()
        byteBuffer.putInt(size)
        val bytes = content
        if (bytes != null && bytes.isNotEmpty()) {
            byteBuffer.put(bytes)
        }
        byteBuffer.flip()
        return byteBuffer.array()
    }
}
