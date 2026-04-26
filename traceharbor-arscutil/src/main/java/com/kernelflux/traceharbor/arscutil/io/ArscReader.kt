package com.kernelflux.traceharbor.arscutil.io

import com.kernelflux.traceharbor.arscutil.ArscUtil
import com.kernelflux.traceharbor.arscutil.data.ArscConstants
import com.kernelflux.traceharbor.arscutil.data.ResChunk
import com.kernelflux.traceharbor.arscutil.data.ResConfig
import com.kernelflux.traceharbor.arscutil.data.ResEntry
import com.kernelflux.traceharbor.arscutil.data.ResMapValue
import com.kernelflux.traceharbor.arscutil.data.ResPackage
import com.kernelflux.traceharbor.arscutil.data.ResStringBlock
import com.kernelflux.traceharbor.arscutil.data.ResTable
import com.kernelflux.traceharbor.arscutil.data.ResType
import com.kernelflux.traceharbor.arscutil.data.ResTypeSpec
import com.kernelflux.traceharbor.arscutil.data.ResValue
import com.kernelflux.traceharbor.javalib.util.Log
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ArscReader @Throws(FileNotFoundException::class) constructor(arscFile: String) {
    private val dataInput: LittleEndianInputStream = LittleEndianInputStream(arscFile)
    private lateinit var globalResTable: ResTable

    init {
        Log.i(TAG, "read From %s", arscFile)
    }

    fun readResourceTable(): ResTable {
        Log.d(TAG, "=============ResTable==============")
        val headStart = 0L
        val resTable = ResTable()
        globalResTable = resTable
        resTable.start = headStart
        resTable.type = dataInput.readShort()
        Log.d(TAG, "table type %d", resTable.type)
        resTable.headSize = dataInput.readShort()
        Log.d(TAG, "head size %d", resTable.headSize)
        resTable.chunkSize = dataInput.readInt()
        Log.d(TAG, "chunk size %f KB", resTable.chunkSize / 1024.0f)
        resTable.packageCount = dataInput.readInt()
        Log.d(TAG, "package count %d", resTable.packageCount)
        val headPaddingSize = (resTable.headSize + headStart - dataInput.getFilePointer()).toInt()
        Log.d(TAG, "head padding size %d", headPaddingSize)
        resTable.headPadding = headPaddingSize
        resTable.globalStringPool = readStringBlock()
        Log.d(TAG, "global string pool pos %d", dataInput.getFilePointer())
        if (resTable.packageCount > 0) {
            val packages = arrayOfNulls<ResPackage>(resTable.packageCount)
            for (i in 0 until resTable.packageCount) {
                packages[i] = readPackage()
            }
            @Suppress("UNCHECKED_CAST")
            resTable.packages = packages as Array<ResPackage>
        }
        val chunkPaddingSize = (resTable.chunkSize + headStart - dataInput.getFilePointer()).toInt()
        Log.d(TAG, "chunk padding size %d", chunkPaddingSize)
        resTable.chunkPadding = chunkPaddingSize
        dataInput.close()
        return resTable
    }

    private fun readPackage(): ResPackage {
        Log.d(TAG, "=============ResPackage==============")
        val headStart = dataInput.getFilePointer()
        Log.d(TAG, "package start %d", headStart)
        val resPackage = ResPackage()
        resPackage.start = headStart
        resPackage.type = dataInput.readShort()
        Log.d(TAG, "package type %d", resPackage.type)
        resPackage.headSize = dataInput.readShort()
        Log.d(TAG, "head size %d", resPackage.headSize)
        resPackage.chunkSize = dataInput.readInt()
        Log.d(TAG, "chunk size %d", resPackage.chunkSize)
        resPackage.id = dataInput.readInt()
        Log.d(TAG, "package id %d", resPackage.id)
        val buffer = ByteArray(256)
        dataInput.read(buffer)
        resPackage.name = buffer
        Log.d(TAG, "package name %s", ArscUtil.toUTF16String(buffer))
        resPackage.resTypePoolOffset = dataInput.readInt()
        Log.d(TAG, "resType pool offset %d", resPackage.resTypePoolOffset)
        resPackage.lastPublicType = dataInput.readInt()
        resPackage.resNamePoolOffset = dataInput.readInt()
        Log.d(TAG, "resName pool offset %d", resPackage.resNamePoolOffset)
        resPackage.lastPublicName = dataInput.readInt()
        val headPaddingSize = (resPackage.headSize + headStart - dataInput.getFilePointer()).toInt()
        Log.d(TAG, "head padding size %d", headPaddingSize)
        resPackage.headPadding = headPaddingSize
        if (resPackage.resTypePoolOffset > 0) {
            dataInput.seek(headStart + resPackage.resTypePoolOffset)
            resPackage.resTypePool = readStringBlock()
        }
        if (resPackage.resNamePoolOffset > 0) {
            dataInput.seek(headStart + resPackage.resNamePoolOffset)
            resPackage.resNamePool = readStringBlock()
        }
        val resTypeList = ArrayList<ResChunk>()
        while (dataInput.getFilePointer() < resPackage.start + resPackage.chunkSize) {
            val type = dataInput.readShort().toInt()
            if (type == ArscConstants.RES_TABLE_TYPE_SPEC_TYPE.toInt()) {
                dataInput.seek(dataInput.getFilePointer() - 2)
                resTypeList.add(readResTypeSpec())
            } else if (type == ArscConstants.RES_TABLE_TYPE_TYPE.toInt()) {
                dataInput.seek(dataInput.getFilePointer() - 2)
                resTypeList.add(readResType(resPackage))
            }
        }
        resPackage.resTypeArray = resTypeList
        val chunkPaddingSize = (resPackage.chunkSize + headStart - dataInput.getFilePointer()).toInt()
        Log.d(TAG, "chunk padding size %d", chunkPaddingSize)
        resPackage.chunkPadding = chunkPaddingSize
        return resPackage
    }

    private fun readResTypeSpec(): ResTypeSpec {
        Log.d(TAG, "==============ResTypeSpec=============")
        val headStart = dataInput.getFilePointer()
        val resTypeSpec = ResTypeSpec()
        resTypeSpec.start = headStart
        resTypeSpec.type = dataInput.readShort()
        Log.d(TAG, "resTypeSpec type %d", resTypeSpec.type)
        resTypeSpec.headSize = dataInput.readShort()
        Log.d(TAG, "resTypeSpec header size %d", resTypeSpec.headSize)
        resTypeSpec.chunkSize = dataInput.readInt()
        Log.d(TAG, "resTypeSpec chunk size %d", resTypeSpec.chunkSize)
        resTypeSpec.id = dataInput.readByte()
        Log.d(TAG, "resTypeSpec type id %d", resTypeSpec.id)
        resTypeSpec.reserved0 = dataInput.readByte()
        resTypeSpec.reserved1 = dataInput.readShort()
        resTypeSpec.entryCount = dataInput.readInt()
        Log.d(TAG, "resTypeSpec entry count %d", resTypeSpec.entryCount)
        val headPaddingSize = (resTypeSpec.headSize + headStart - dataInput.getFilePointer()).toInt()
        Log.d(TAG, "head padding size %d", headPaddingSize)
        resTypeSpec.headPadding = headPaddingSize
        if (resTypeSpec.chunkSize - resTypeSpec.headSize > 0) {
            val buffer = ByteArray(resTypeSpec.chunkSize - resTypeSpec.headSize)
            dataInput.read(buffer)
            resTypeSpec.configFlags = buffer
        }
        val chunkPaddingSize = (resTypeSpec.chunkSize + headStart - dataInput.getFilePointer()).toInt()
        Log.d(TAG, "chunk padding size %d", chunkPaddingSize)
        resTypeSpec.chunkPadding = chunkPaddingSize
        return resTypeSpec
    }

    private fun readResType(resPackage: ResPackage): ResType {
        Log.d(TAG, "=============ResType==============")
        val headStart = dataInput.getFilePointer()
        val resType = ResType()
        resType.start = headStart
        resType.type = dataInput.readShort()
        Log.d(TAG, "resType type %d", resType.type)
        resType.headSize = dataInput.readShort()
        Log.d(TAG, "resType header size %d", resType.headSize)
        resType.chunkSize = dataInput.readInt()
        Log.d(TAG, "resType chunk size %d", resType.chunkSize)
        resType.id = dataInput.readByte()
        resType.reserved0 = dataInput.readByte()
        resType.reserved1 = dataInput.readShort()
        resType.entryCount = dataInput.readInt()
        resType.entryTableOffset = dataInput.readInt()
        resType.resConfigFlags = readResConfig()
        val headPaddingSize = (resType.headSize + headStart - dataInput.getFilePointer()).toInt()
        Log.d(TAG, "head padding size %d", headPaddingSize)
        resType.headPadding = headPaddingSize
        if (resType.entryCount > 0) {
            val resEntryOffsets = ArrayList<Int>()
            for (i in 0 until resType.entryCount) {
                resEntryOffsets.add(dataInput.readInt())
            }
            resType.entryOffsets = resEntryOffsets
        }
        dataInput.seek(headStart + resType.entryTableOffset)
        val entryTable = ArrayList<ResEntry?>()
        for (i in 0 until resType.entryCount) {
            if (resType.entryOffsets!![i] != ArscConstants.NO_ENTRY_INDEX) {
                entryTable.add(readResEntry(resPackage, headStart + resType.entryTableOffset + resType.entryOffsets!![i]))
            } else {
                entryTable.add(null)
            }
        }
        resType.entryTable = entryTable
        val chunkPaddingSize = (resType.chunkSize + headStart - dataInput.getFilePointer()).toInt()
        Log.d(TAG, "chunk padding size %d", chunkPaddingSize)
        resType.chunkPadding = chunkPaddingSize
        return resType
    }

    private fun readResEntry(resPackage: ResPackage, start: Long): ResEntry {
        Log.d(TAG, "==============ResEntry=============")
        dataInput.seek(start)
        val resEntry = ResEntry()
        resEntry.size = dataInput.readShort()
        resEntry.flag = dataInput.readShort()
        Log.d(TAG, "resEntry flag %d", resEntry.flag)
        resEntry.stringPoolIndex = dataInput.readInt()

        val entryName = ResStringBlock.resolveStringPoolEntry(
            resPackage.resNamePool!!.strings!![resEntry.stringPoolIndex].array(),
            resPackage.resNamePool!!.charSet,
        )
        Log.d(TAG, "entryName %s", entryName)
        resEntry.entryName = entryName

        if ((resEntry.flag.toInt() and ArscConstants.RES_TABLE_ENTRY_FLAG_COMPLEX.toInt()) == 0) {
            resEntry.resValue = readResValue()
        } else {
            resEntry.parent = dataInput.readInt()
            resEntry.pairCount = dataInput.readInt()
            if (resEntry.pairCount > 0) {
                val mapValues = ArrayList<ResMapValue>()
                for (i in 0 until resEntry.pairCount) {
                    mapValues.add(readResMapValue())
                }
                resEntry.resMapValues = mapValues
            }
        }
        return resEntry
    }

    private fun readResValue(): ResValue {
        Log.d(TAG, "============ResValue===============")
        val resValue = ResValue()
        resValue.size = dataInput.readShort()
        resValue.setResvered(dataInput.readByte())
        resValue.dataType = dataInput.readByte()
        Log.d(TAG, "resValue data type %d", resValue.dataType)
        resValue.data = dataInput.readInt()

        if (resValue.dataType.toInt() == ArscConstants.RES_VALUE_DATA_TYPE_STRING) {
            val globalPool = globalResTable.globalStringPool!!
            Log.d(
                TAG,
                "resValue string %s",
                ResStringBlock.resolveStringPoolEntry(globalPool.strings!![resValue.data].array(), globalPool.charSet),
            )
        } else {
            Log.d(TAG, "resValue %s", resValue.printData())
        }

        return resValue
    }

    private fun readResMapValue(): ResMapValue {
        Log.d(TAG, "==============ResMapValue=============")
        val resValue = ResMapValue()
        resValue.name = dataInput.readInt()
        resValue.resValue = readResValue()
        return resValue
    }

    private fun readResConfig(): ResConfig {
        Log.d(TAG, "==============ResConfig=============")
        val config = ResConfig()
        config.size = dataInput.readInt()
        if (config.size > 4) {
            val buffer = ByteArray(config.size - 4)
            dataInput.read(buffer)
            config.content = buffer
        }
        return config
    }

    private fun readStringBlock(): ResStringBlock {
        Log.d(TAG, "==============ResStringBlock=============")
        val headStart = dataInput.getFilePointer()
        val stringPool = ResStringBlock()
        stringPool.start = headStart
        stringPool.type = dataInput.readShort()
        Log.d(TAG, "stringPool type %d", stringPool.type)
        stringPool.headSize = dataInput.readShort()
        Log.d(TAG, "stringPool head size %d", stringPool.headSize)
        stringPool.chunkSize = dataInput.readInt()
        Log.d(TAG, "stringPool chunk size %d", stringPool.chunkSize)
        stringPool.stringCount = dataInput.readInt()
        Log.d(TAG, "stringPool string count %d", stringPool.stringCount)
        stringPool.styleCount = dataInput.readInt()
        Log.d(TAG, "stringPool style count %d", stringPool.styleCount)
        stringPool.flag = dataInput.readInt()
        Log.d(TAG, "stringPool flag %d", stringPool.flag)
        stringPool.stringStart = dataInput.readInt()
        Log.d(TAG, "stringPool string start %d", stringPool.stringStart)
        stringPool.styleStart = dataInput.readInt()
        Log.d(TAG, "stringPool style start %d", stringPool.styleStart)
        val headPaddingSize = (stringPool.headSize + headStart - dataInput.getFilePointer()).toInt()
        Log.d(TAG, "head padding size %d", headPaddingSize)
        stringPool.headPadding = headPaddingSize
        dataInput.seek(headStart + stringPool.headSize)
        if (stringPool.stringCount > 0) {
            val stringOffsets = ArrayList<Int>()
            for (i in 0 until stringPool.stringCount) {
                stringOffsets.add(dataInput.readInt())
            }
            stringPool.stringOffsets = stringOffsets
        }
        if (stringPool.styleCount > 0) {
            val styleOffsets = ArrayList<Int>()
            for (i in 0 until stringPool.styleCount) {
                styleOffsets.add(dataInput.readInt())
            }
            stringPool.styleOffsets = styleOffsets
        }
        dataInput.seek(headStart + stringPool.stringStart)
        if (stringPool.stringCount > 0) {
            val strings = ArrayList<ByteBuffer>()
            val stringIndexMap = HashMap<String, Int>()
            for (i in 0 until stringPool.stringCount) {
                val buffer: ByteArray = if (i < stringPool.stringCount - 1) {
                    ByteArray(stringPool.stringOffsets!![i + 1] - stringPool.stringOffsets!![i])
                } else {
                    if (stringPool.styleCount > 0) {
                        ByteArray(stringPool.styleStart - (stringPool.stringOffsets!![i] + stringPool.stringStart))
                    } else {
                        ByteArray(stringPool.chunkSize - stringPool.stringStart - stringPool.stringOffsets!![i])
                    }
                }
                dataInput.read(buffer)
                strings.add(ByteBuffer.allocate(buffer.size))
                strings[i].order(ByteOrder.LITTLE_ENDIAN)
                strings[i].clear()
                strings[i].put(buffer)
                stringIndexMap[ResStringBlock.resolveStringPoolEntry(buffer, stringPool.charSet)] = i
            }
            stringPool.strings = strings
            stringPool.stringIndexMap = stringIndexMap
        }
        if (stringPool.styleCount > 0) {
            val styleBytes = ByteArray(stringPool.chunkSize - stringPool.styleStart)
            dataInput.read(styleBytes)
            stringPool.styles = styleBytes
        }

        val chunkPaddingSize = (stringPool.chunkSize + headStart - dataInput.getFilePointer()).toInt()
        Log.d(TAG, "chunk padding size %d", chunkPaddingSize)
        stringPool.chunkPadding = chunkPaddingSize
        return stringPool
    }

    companion object {
        private const val TAG = "ArscUtil.ArscReader"
    }
}
