package com.kernelflux.arscutil.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

class ResMapValue {
    var name: Int = 0
    var resValue: ResValue? = null

    fun toBytes(): ByteArray {
        val value = resValue ?: throw NullPointerException("resValue == null")
        val byteBuffer = ByteBuffer.allocate(4 + value.size)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.clear()
        byteBuffer.putInt(name)
        byteBuffer.put(value.toBytes())
        byteBuffer.flip()
        return byteBuffer.array()
    }
}
