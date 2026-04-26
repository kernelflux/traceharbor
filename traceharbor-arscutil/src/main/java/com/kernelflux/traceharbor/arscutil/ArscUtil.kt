package com.kernelflux.traceharbor.arscutil

import com.kernelflux.traceharbor.arscutil.data.ArscConstants
import com.kernelflux.traceharbor.arscutil.data.ResChunk
import com.kernelflux.traceharbor.arscutil.data.ResPackage
import com.kernelflux.traceharbor.arscutil.data.ResStringBlock
import com.kernelflux.traceharbor.arscutil.data.ResTable
import com.kernelflux.traceharbor.arscutil.data.ResType
import com.kernelflux.traceharbor.javalib.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.collections.get

object ArscUtil {
    private const val TAG = "ArscUtil.ArscUtil"

    @JvmStatic
    fun toUTF16String(buffer: ByteArray): String {
        val charBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asCharBuffer()
        var index = 0
        while (index < charBuffer.length) {
            if (charBuffer.get() == 0x00.toChar()) {
                break
            }
            index++
        }
        charBuffer.limit(index).position(0)
        return charBuffer.toString()
    }

    @JvmStatic
    fun getPackageId(resourceId: Int): Int = (resourceId and -0x1000000) shr 24

    @JvmStatic
    fun getResourceTypeId(resourceId: Int): Int = (resourceId and 0x00FF0000) shr 16

    @JvmStatic
    fun getResourceEntryId(resourceId: Int): Int = resourceId and 0x0000FFFF

    @JvmStatic
    fun findResPackage(resTable: ResTable, packageId: Int): ResPackage? {
        var resPackage: ResPackage? = null
        for (pkg in resTable.packages.orEmpty()) {
            if (pkg.id == packageId) {
                resPackage = pkg
                break
            }
        }
        return resPackage
    }

    @JvmStatic
    fun findResType(resPackage: ResPackage, resourceId: Int): List<ResType> {
        val typeId = (resourceId and 0X00FF0000) shr 16
        val entryId = resourceId and 0x0000FFFF
        val resTypeList = ArrayList<ResType>()
        val resTypeArray: List<ResChunk>? = resPackage.resTypeArray
        if (resTypeArray != null) {
            for (resChunk in resTypeArray) {
                if (resChunk.type == ArscConstants.RES_TABLE_TYPE_TYPE && (resChunk as ResType).id.toInt() == typeId) {
                    val entryCount = resChunk.entryCount
                    if (entryId < entryCount) {
                        val offset = resChunk.entryOffsets!![entryId]
                        if (offset != ArscConstants.NO_ENTRY_INDEX) {
                            resTypeList.add(resChunk)
                        }
                    }
                }
            }
        }
        return resTypeList
    }

    @JvmStatic
    fun removeResource(resTable: ResTable, resourceId: Int, resourceName: String) {
        val resPackage = findResPackage(resTable, getPackageId(resourceId))
        if (resPackage != null) {
            val resTypeList = findResType(resPackage, resourceId)
            var resNameStringPoolIndex = -1
            for (resType in resTypeList) {
                val entryId = getResourceEntryId(resourceId)
                resNameStringPoolIndex = resType.entryTable!![entryId]!!.stringPoolIndex
                resType.removeEntry(entryId)
                resType.refresh()
            }
            if (resNameStringPoolIndex != -1) {
                val pool = resPackage.resNamePool!!
                Log.d(
                    TAG,
                    "try to remove %s (%H), find resource %s",
                    resourceName,
                    resourceId,
                    ResStringBlock.resolveStringPoolEntry(pool.strings!![resNameStringPoolIndex].array(), pool.charSet),
                )
            }
            resPackage.shrinkResNameStringPool()
            resPackage.refresh()
            resTable.refresh()
        }
    }

    @JvmStatic
    fun replaceFileResource(
        resTable: ResTable,
        sourceResId: Int,
        sourceFile: String?,
        targetResId: Int,
        targetFile: String?,
    ): Boolean {
        val sourcePkgId = getPackageId(sourceResId)
        val targetPkgId = getPackageId(targetResId)
        Log.d(TAG, "try to replace %H(%s) with %H(%s)", sourceResId, sourceFile, targetResId, targetFile)
        if (sourcePkgId == targetPkgId) {
            val resPackage = findResPackage(resTable, sourcePkgId)
            if (resPackage != null) {
                val targetResTypeList = findResType(resPackage, targetResId)
                var targetFileIndex = -1
                for (targetResType in targetResTypeList) {
                    val entryId = getResourceEntryId(targetResId)
                    val resEntry = targetResType.entryTable!![entryId]!!
                    val isComplex = (resEntry.flag.toInt() and ArscConstants.RES_TABLE_ENTRY_FLAG_COMPLEX.toInt()) != 0
                    val resValue = resEntry.resValue
                    if (!isComplex && resValue != null &&
                        resValue.dataType.toInt() == ArscConstants.RES_VALUE_DATA_TYPE_STRING
                    ) {
                        val globalPool = resTable.globalStringPool!!
                        val filePath = ResStringBlock.resolveStringPoolEntry(
                            globalPool.strings!![resValue.data].array(),
                            globalPool.charSet,
                        )
                        if (filePath == targetFile) {
                            targetFileIndex = resValue.data
                            break
                        } else {
                            Log.w(TAG, "find target file %s, %s was expected", filePath, targetFile)
                            continue
                        }
                    }
                }
                if (targetFileIndex == -1) {
                    Log.w(TAG, "can not find target file %s in resource %H", targetFile, targetResId)
                    return false
                }
                var sourceFileIndex = -1
                val sourceResTypeList = findResType(resPackage, sourceResId)
                for (sourceResType in sourceResTypeList) {
                    val entryId = getResourceEntryId(sourceResId)
                    val resEntry = sourceResType.entryTable!![entryId]!!
                    val isComplex = (resEntry.flag.toInt() and ArscConstants.RES_TABLE_ENTRY_FLAG_COMPLEX.toInt()) != 0
                    val resValue = resEntry.resValue
                    if (!isComplex && resValue != null &&
                        resValue.dataType.toInt() == ArscConstants.RES_VALUE_DATA_TYPE_STRING
                    ) {
                        val globalPool = resTable.globalStringPool!!
                        val filePath = ResStringBlock.resolveStringPoolEntry(
                            globalPool.strings!![resValue.data].array(),
                            globalPool.charSet,
                        )
                        if (filePath == sourceFile) {
                            sourceFileIndex = resValue.data
                            resValue.data = targetFileIndex
                            sourceResType.refresh()
                        } else {
                            Log.w(TAG, "find source file %s, %s was expected", filePath, sourceFile)
                            continue
                        }
                    }
                }
                if (sourceFileIndex != -1) {
                    return true
                }
            }
        } else {
            Log.w(TAG, "sourcePkgId %d != targetPkgId %d, quit replace!", sourcePkgId, targetPkgId)
        }
        return false
    }

    @JvmStatic
    fun replaceResEntryName(resTable: ResTable, resIdProguard: Map<Int, String>) {
        val updatePackages = HashSet<ResPackage>()
        for (resId in resIdProguard.keys) {
            val resPackage = findResPackage(resTable, getPackageId(resId))
            if (resPackage != null) {
                if (resPackage.resProguardPool == null) {
                    val namePool = resPackage.resNamePool!!
                    val resProguardBlock = ResStringBlock()
                    resProguardBlock.type = namePool.type
                    resProguardBlock.start = namePool.start
                    resProguardBlock.headSize = namePool.headSize
                    resProguardBlock.headPadding = namePool.headPadding
                    resProguardBlock.chunkPadding = namePool.chunkPadding
                    resProguardBlock.styleCount = namePool.styleCount
                    resProguardBlock.flag = namePool.flag
                    resProguardBlock.stringStart = namePool.stringStart
                    resProguardBlock.styleOffsets = namePool.styleOffsets
                    resProguardBlock.styles = namePool.styles
                    resProguardBlock.strings = ArrayList()
                    resProguardBlock.stringOffsets = ArrayList()
                    resProguardBlock.stringIndexMap = HashMap()
                    resPackage.resProguardPool = resProguardBlock
                }

                val resTypeList = findResType(resPackage, resId)
                for (resType in resTypeList) {
                    val entryId = getResourceEntryId(resId)
                    val resEntry = resType.entryTable!![entryId]!!
                    resEntry.entryName = resIdProguard[resId]
                    val proguardPool = resPackage.resProguardPool!!
                    if (!proguardPool.stringIndexMap!!.containsKey(resEntry.entryName)) {
                        proguardPool.strings!!.add(
                            ByteBuffer.wrap(ResStringBlock.encodeStringPoolEntry(resEntry.entryName!!, proguardPool.charSet)),
                        )
                        proguardPool.stringCount = proguardPool.strings!!.size
                        proguardPool.stringIndexMap!![resEntry.entryName!!] = proguardPool.stringCount - 1
                    }
                }
                updatePackages.add(resPackage)
            }
        }

        for (resPackage in updatePackages) {
            val resTypeArray = resPackage.resTypeArray
            if (resTypeArray != null) {
                for (resChunk in resTypeArray) {
                    if (resChunk.type == ArscConstants.RES_TABLE_TYPE_TYPE) {
                        val resType = resChunk as ResType
                        for (resEntry in resType.entryTable.orEmpty()) {
                            if (resEntry != null) {
                                val proguardPool = resPackage.resProguardPool!!
                                if (!proguardPool.stringIndexMap!!.containsKey(resEntry.entryName)) {
                                    proguardPool.strings!!.add(
                                        ByteBuffer.wrap(ResStringBlock.encodeStringPoolEntry(resEntry.entryName!!, proguardPool.charSet)),
                                    )
                                    proguardPool.stringCount = proguardPool.strings!!.size
                                    proguardPool.stringIndexMap!![resEntry.entryName!!] = proguardPool.stringCount - 1
                                }
                                resEntry.stringPoolIndex = proguardPool.stringIndexMap!![resEntry.entryName]!!
                            }
                        }
                    }
                }
            }
            resPackage.refresh()
        }
        resTable.refresh()
    }

    @JvmStatic
    fun replaceResFileName(resTable: ResTable, resId: Int, srcFileName: String?, targetFileName: String?): Boolean {
        Log.d(TAG, "try to replace resource (%H) file %s with %s", resId, srcFileName, targetFileName)
        val resPackage = findResPackage(resTable, getPackageId(resId))
        var result = false
        if (resPackage != null) {
            val resTypeList = findResType(resPackage, resId)
            for (resType in resTypeList) {
                val entryId = getResourceEntryId(resId)
                val resEntry = resType.entryTable!![entryId]!!
                val resValue = resEntry.resValue!!
                if (resValue.dataType.toInt() == ArscConstants.RES_VALUE_DATA_TYPE_STRING) {
                    val globalPool = resTable.globalStringPool!!
                    val filePath = ResStringBlock.resolveStringPoolEntry(
                        globalPool.strings!![resValue.data].array(),
                        globalPool.charSet,
                    )
                    if (filePath == srcFileName) {
                        globalPool.strings!![resValue.data] =
                            ByteBuffer.wrap(ResStringBlock.encodeStringPoolEntry(targetFileName!!, globalPool.charSet))
                        result = true
                        break
                    }
                }
            }
            if (result) {
                resTable.globalStringPool!!.refresh()
                resTable.refresh()
            } else {
                Log.w(TAG, "srcFile %s not referenced by resource (%H)", srcFileName, resId)
            }
        }
        return result
    }
}
