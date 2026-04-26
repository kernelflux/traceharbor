package com.kernelflux.traceharbor.arscutil.data

import java.lang.Float
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ResValue {
    var size: Short = 0
    private var res0: Byte = 0
    var dataType: Byte = 0
    var data: Int = 0

    fun setResvered(res: Byte) {
        this.res0 = res
    }

    fun printData(): String {
        return when (dataType.toInt()) {
            ArscConstants.RES_VALUE_DATA_TYPE_NULL -> "[Null]"
            ArscConstants.RES_VALUE_DATA_TYPE_REFERENCE -> "reference:$data"
            ArscConstants.RES_VALUE_DATA_TYPE_STRING -> "string:$data"
            ArscConstants.RES_VALUE_DATA_TYPE_FLOAT -> "float:" + Float.intBitsToFloat(data)
            ArscConstants.RES_VALUE_DATA_TYPE_INT_DEC -> "integer:" + String.format("%d", data)
            ArscConstants.RES_VALUE_DATA_TYPE_INT_HEX -> "integer:" + String.format("%x", data)
            ArscConstants.RES_VALUE_DATA_TYPE_INT_BOOLEAN -> "boolean:" + String.format("%b", data)
            ArscConstants.RES_VALUE_DATA_TYPE_INT_COLOR_ARGB8 -> "color:" + String.format("#%8x", data)
            ArscConstants.RES_VALUE_DATA_TYPE_INT_COLOR_RGB8 -> "color:" + String.format("#%6x", data)
            ArscConstants.RES_VALUE_DATA_TYPE_INT_COLOR_ARGB4 -> "color:" + String.format("#%4x", data)
            ArscConstants.RES_VALUE_DATA_TYPE_INT_COLOR_RGB4 -> "color:" + String.format("#%3x", data)
            else -> "other:$data"
        }
    }

    fun toBytes(): ByteArray {
        val byteBuffer = ByteBuffer.allocate(size.toInt())
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.clear()
        byteBuffer.putShort(size)
        byteBuffer.put(res0)
        byteBuffer.put(dataType)
        byteBuffer.putInt(data)
        byteBuffer.flip()
        return byteBuffer.array()
    }
}
