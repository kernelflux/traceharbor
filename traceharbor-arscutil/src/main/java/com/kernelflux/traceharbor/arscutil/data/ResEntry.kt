package com.kernelflux.traceharbor.arscutil.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

class ResEntry {
    var size: Short = 0
    var flag: Short = 0
    var stringPoolIndex: Int = 0
    var entryName: String? = null
    var resValue: ResValue? = null
    var parent: Int = 0
    var pairCount: Int = 0
    var resMapValues: List<ResMapValue>? = null

    fun toBytes(): ByteArray {
        val headBuffer = ByteBuffer.allocate(size.toInt())
        headBuffer.order(ByteOrder.LITTLE_ENDIAN)
        headBuffer.clear()
        headBuffer.putShort(size)
        headBuffer.putShort(flag)
        headBuffer.putInt(stringPoolIndex)

        var totalSize = size.toInt()
        val content = ArrayList<ByteBuffer>()

        if ((flag.toInt() and ArscConstants.RES_TABLE_ENTRY_FLAG_COMPLEX.toInt()) == 0) {
            content.add(ByteBuffer.wrap(resValue!!.toBytes()))
        } else {
            headBuffer.putInt(parent)
            headBuffer.putInt(pairCount)
            if (pairCount > 0) {
                val values = resMapValues.orEmpty()
                for (value in values) {
                    content.add(ByteBuffer.wrap(value.toBytes()))
                }
            }
        }
        for (value in content) {
            totalSize += value.limit()
        }
        headBuffer.flip()
        val finalBuffer = ByteBuffer.allocate(totalSize)
        finalBuffer.order(ByteOrder.LITTLE_ENDIAN)
        finalBuffer.clear()
        finalBuffer.put(headBuffer.array())
        for (value in content) {
            finalBuffer.put(value.array())
        }

        finalBuffer.flip()
        return finalBuffer.array()
    }
}
