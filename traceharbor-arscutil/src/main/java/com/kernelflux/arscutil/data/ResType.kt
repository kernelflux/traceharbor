package com.kernelflux.arscutil.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.HashMap

class ResType : ResChunk() {
    var id: Byte = 0
    var reserved0: Byte = 0
    var reserved1: Short = 0
    var entryCount: Int = 0
    var entryTableOffset: Int = 0
    var resConfigFlags: ResConfig? = null
    var entryOffsets: MutableList<Int>? = null
    var entryTable: MutableList<ResEntry?>? = null
        set(value) {
            field = value
            if (value != null) {
                updateResNameReferenceCount()
            }
        }

    val resNameStringCountMap: MutableMap<Int, Int> = HashMap()

    fun removeEntry(entryId: Int) {
        entryTable!![entryId] = null
        entryOffsets!![entryId] = ArscConstants.NO_ENTRY_INDEX
    }

    private fun updateResNameReferenceCount() {
        resNameStringCountMap.clear()
        val table = entryTable ?: return
        for (i in 0 until entryCount) {
            val entry = table[i] ?: continue
            val resNameStringPoolIndex = entry.stringPoolIndex
            if (!resNameStringCountMap.containsKey(resNameStringPoolIndex)) {
                resNameStringCountMap[resNameStringPoolIndex] = 0
            }
            resNameStringCountMap[resNameStringPoolIndex] =
                resNameStringCountMap[resNameStringPoolIndex]!! + 1
        }
    }

    fun refresh() {
        val table = entryTable
        val offsets = entryOffsets
        if (table != null && offsets != null) {
            var lastOffset = 0
            for (i in 0 until entryCount) {
                if (offsets[i] != ArscConstants.NO_ENTRY_INDEX) {
                    offsets[i] = lastOffset
                    lastOffset += table[i]!!.toBytes().size
                }
            }
            updateResNameReferenceCount()
        }
        recomputeChunkSize()
    }

    private fun recomputeChunkSize() {
        chunkSize = 0
        chunkSize += headSize
        var realEntryCount = 0
        entryOffsets?.let { chunkSize += it.size * 4 }
        entryTable?.forEach { entry ->
            if (entry != null) {
                realEntryCount++
                chunkSize += entry.toBytes().size
            }
        }
        if (realEntryCount == 0) {
            entryCount = 0
            chunkSize = 0
            chunkPadding = 0
        } else {
            if (chunkSize % 4 != 0) {
                chunkPadding = 4 - chunkSize % 4
                chunkSize += chunkPadding
            } else {
                chunkPadding = 0
            }
        }
    }

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
        byteBuffer.putInt(entryTableOffset)
        resConfigFlags?.let { byteBuffer.put(it.toBytes()) }
        if (headPadding > 0) {
            byteBuffer.put(ByteArray(headPadding))
        }
        entryOffsets?.forEach { byteBuffer.putInt(it) }
        entryTable?.forEach { entry ->
            if (entry != null) {
                byteBuffer.put(entry.toBytes())
            }
        }
        if (chunkPadding > 0) {
            byteBuffer.put(ByteArray(chunkPadding))
        }
        byteBuffer.flip()
        return byteBuffer.array()
    }
}
