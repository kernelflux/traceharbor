package com.kernelflux.arscutil.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.HashMap

class ResPackage : ResChunk() {
    var id: Int = 0
    var name: ByteArray? = null
    var resTypePoolOffset: Int = 0
    var lastPublicType: Int = 0
    var resNamePoolOffset: Int = 0
    var lastPublicName: Int = 0
    var resTypePool: ResStringBlock? = null
    var resNamePool: ResStringBlock? = null
    var resTypeArray: List<ResChunk>? = null
    var resProguardPool: ResStringBlock? = null

    fun refresh() {
        resProguardPool?.let {
            resNamePool = it
            it.refresh()
        }
        recomputeChunkSize()
    }

    fun shrinkResNameStringPool() {
        val countMap = HashMap<Int, Int>()
        for (resType in resTypeArray.orEmpty()) {
            if (resType.type == ArscConstants.RES_TABLE_TYPE_TYPE) {
                for (index in (resType as ResType).resNameStringCountMap.keys) {
                    if (!countMap.containsKey(index)) {
                        countMap[index] = 0
                    }
                    countMap[index] = countMap[index]!! + resType.resNameStringCountMap[index]!!
                }
            }
        }

        val pool = resNamePool ?: return
        for (index in 0 until pool.stringCount) {
            if (!countMap.containsKey(index)) {
                pool.strings!![index] = ByteBuffer.wrap(ResStringBlock.encodeStringPoolEntry("", pool.charSet))
            }
        }
        pool.refresh()
    }

    private fun recomputeChunkSize() {
        chunkSize = 0
        chunkSize += headSize
        resTypePool?.let { chunkSize += it.chunkSize }
        resNamePool?.let { chunkSize += it.chunkSize }
        resTypeArray?.forEach { resType ->
            if (resType.chunkSize > 0) {
                chunkSize += resType.chunkSize
            }
        }
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
        byteBuffer.putInt(id)
        byteBuffer.put(name!!)
        byteBuffer.putInt(resTypePoolOffset)
        byteBuffer.putInt(lastPublicType)
        byteBuffer.putInt(resNamePoolOffset)
        byteBuffer.putInt(lastPublicName)
        if (headPadding > 0) {
            byteBuffer.put(ByteArray(headPadding))
        }
        resTypePool?.let { byteBuffer.put(it.toBytes()) }
        resNamePool?.let { byteBuffer.put(it.toBytes()) }
        resTypeArray?.forEach { resChunk ->
            if (resChunk.chunkSize > 0) {
                byteBuffer.put(resChunk.toBytes())
            }
        }
        if (chunkPadding > 0) {
            byteBuffer.put(ByteArray(chunkPadding))
        }
        byteBuffer.flip()
        return byteBuffer.array()
    }
}
