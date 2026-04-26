package com.kernelflux.traceharbor.resource.hproflib

import com.kernelflux.traceharbor.resource.common.utils.DigestUtil
import com.kernelflux.traceharbor.resource.hproflib.model.Field
import com.kernelflux.traceharbor.resource.hproflib.model.ID
import com.kernelflux.traceharbor.resource.hproflib.model.Type
import com.kernelflux.traceharbor.resource.hproflib.utils.IOUtil
import com.kernelflux.traceharbor.util.TraceHarborLog
import com.kernelflux.traceharbor.util.TraceHarborUtil
import com.tencent.tinker.ziputils.ziputil.TinkerZipEntry
import com.tencent.tinker.ziputils.ziputil.TinkerZipFile
import com.tencent.tinker.ziputils.ziputil.TinkerZipOutputStream
import com.tencent.tinker.ziputils.ziputil.TinkerZipUtil
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.Properties
import java.util.zip.CRC32

class HprofBufferShrinker {
    private val mBmpBufferIds: MutableSet<ID> = HashSet()
    private val mBufferIdToElementDataMap: MutableMap<ID, ByteArray> = HashMap()
    private val mBmpBufferIdToDeduplicatedIdMap: MutableMap<ID, ID> = HashMap()
    private val mStringValueIds: MutableSet<ID> = HashSet()

    private var mBitmapClassNameStringId: ID? = null
    private var mBmpClassId: ID? = null
    private var mMBufferFieldNameStringId: ID? = null
    private var mMRecycledFieldNameStringId: ID? = null

    private var mStringClassNameStringId: ID? = null
    private var mStringClassId: ID? = null
    private var mValueFieldNameStringId: ID? = null

    private var mIdSize = 0
    private var mNullBufferId: ID? = null
    private var mBmpClassInstanceFields: Array<Field>? = null
    private var mStringClassInstanceFields: Array<Field>? = null

    @Throws(IOException::class)
    fun shrink(hprofIn: File, hprofOut: File) {
        var input: FileInputStream? = null
        var output: OutputStream? = null
        try {
            input = FileInputStream(hprofIn)
            output = BufferedOutputStream(FileOutputStream(hprofOut))
            val reader = HprofReader(BufferedInputStream(input))
            reader.accept(HprofInfoCollectVisitor())
            input.channel.position(0)
            reader.accept(HprofKeptBufferCollectVisitor())
            input.channel.position(0)
            reader.accept(HprofBufferShrinkVisitor(HprofWriter(output)))
        } finally {
            if (output != null) {
                try {
                    output.close()
                } catch (_: Throwable) {
                }
            }
            if (input != null) {
                try {
                    input.close()
                } catch (_: Throwable) {
                }
            }
        }
    }

    private inner class HprofInfoCollectVisitor : HprofVisitor(null) {
        override fun visitHeader(text: String, idSize: Int, timestamp: Long) {
            mIdSize = idSize
            mNullBufferId = ID.createNullID(idSize)
        }

        override fun visitStringRecord(id: ID, text: String, timestamp: Int, length: Long) {
            if (mBitmapClassNameStringId == null && "android.graphics.Bitmap" == text) {
                mBitmapClassNameStringId = id
            } else if (mMBufferFieldNameStringId == null && "mBuffer" == text) {
                mMBufferFieldNameStringId = id
            } else if (mMRecycledFieldNameStringId == null && "mRecycled" == text) {
                mMRecycledFieldNameStringId = id
            } else if (mStringClassNameStringId == null && "java.lang.String" == text) {
                mStringClassNameStringId = id
            } else if (mValueFieldNameStringId == null && "value" == text) {
                mValueFieldNameStringId = id
            }
        }

        override fun visitLoadClassRecord(
            serialNumber: Int,
            classObjectId: ID,
            stackTraceSerial: Int,
            classNameStringId: ID,
            timestamp: Int,
            length: Long,
        ) {
            if (mBmpClassId == null && mBitmapClassNameStringId != null && mBitmapClassNameStringId == classNameStringId) {
                mBmpClassId = classObjectId
            } else if (mStringClassId == null && mStringClassNameStringId != null && mStringClassNameStringId == classNameStringId) {
                mStringClassId = classObjectId
            }
        }

        override fun visitHeapDumpRecord(tag: Int, timestamp: Int, length: Long): HprofHeapDumpVisitor {
            return object : HprofHeapDumpVisitor(null) {
                override fun visitHeapDumpClass(
                    id: ID,
                    stackSerialNumber: Int,
                    superClassId: ID,
                    classLoaderId: ID,
                    instanceSize: Int,
                    staticFields: Array<Field>,
                    instanceFields: Array<Field>,
                ) {
                    if (mBmpClassInstanceFields == null && mBmpClassId != null && mBmpClassId == id) {
                        mBmpClassInstanceFields = instanceFields
                    } else if (mStringClassInstanceFields == null && mStringClassId != null && mStringClassId == id) {
                        mStringClassInstanceFields = instanceFields
                    }
                }
            }
        }
    }

    private inner class HprofKeptBufferCollectVisitor : HprofVisitor(null) {
        override fun visitHeapDumpRecord(tag: Int, timestamp: Int, length: Long): HprofHeapDumpVisitor {
            return object : HprofHeapDumpVisitor(null) {
                override fun visitHeapDumpInstance(id: ID, stackId: Int, typeId: ID, instanceData: ByteArray) {
                    try {
                        if (mBmpClassId != null && mBmpClassId == typeId) {
                            var bufferId: ID? = null
                            var isRecycled: Boolean? = null
                            val bais = ByteArrayInputStream(instanceData)
                            val fields = mBmpClassInstanceFields ?: emptyArray()
                            for (field in fields) {
                                val fieldNameStringId = field.nameId
                                val fieldType =
                                    Type.getType(field.typeId)
                                        ?: throw IllegalStateException("visit bmp instance failed, lost type def of typeId: ${field.typeId}")
                                if (mMBufferFieldNameStringId == fieldNameStringId) {
                                    bufferId = IOUtil.readValue(bais, fieldType, mIdSize) as ID?
                                } else if (mMRecycledFieldNameStringId == fieldNameStringId) {
                                    isRecycled = IOUtil.readValue(bais, fieldType, mIdSize) as Boolean?
                                } else if (bufferId == null || isRecycled == null) {
                                    IOUtil.skipValue(bais, fieldType, mIdSize)
                                } else {
                                    break
                                }
                            }
                            bais.close()
                            val regardAsNotRecycledBmp = isRecycled == null || !isRecycled
                            if (bufferId != null && regardAsNotRecycledBmp && bufferId != mNullBufferId) {
                                mBmpBufferIds.add(bufferId)
                            }
                        } else if (mStringClassId != null && mStringClassId == typeId) {
                            var strValueId: ID? = null
                            val bais = ByteArrayInputStream(instanceData)
                            val fields = mStringClassInstanceFields ?: emptyArray()
                            for (field in fields) {
                                val fieldNameStringId = field.nameId
                                val fieldType =
                                    Type.getType(field.typeId)
                                        ?: throw IllegalStateException("visit string instance failed, lost type def of typeId: ${field.typeId}")
                                if (mValueFieldNameStringId == fieldNameStringId) {
                                    strValueId = IOUtil.readValue(bais, fieldType, mIdSize) as ID?
                                } else if (strValueId == null) {
                                    IOUtil.skipValue(bais, fieldType, mIdSize)
                                } else {
                                    break
                                }
                            }
                            bais.close()
                            if (strValueId != null && strValueId != mNullBufferId) {
                                mStringValueIds.add(strValueId)
                            }
                        }
                    } catch (thr: Throwable) {
                        throw RuntimeException(thr)
                    }
                }

                override fun visitHeapDumpPrimitiveArray(
                    tag: Int,
                    id: ID,
                    stackId: Int,
                    numElements: Int,
                    typeId: Int,
                    elements: ByteArray,
                ) {
                    mBufferIdToElementDataMap[id] = elements
                }
            }
        }

        override fun visitEnd() {
            val duplicateBufferFilterMap: MutableMap<String, ID> = HashMap()
            for ((bufferId, elementData) in mBufferIdToElementDataMap) {
                if (!mBmpBufferIds.contains(bufferId)) {
                    continue
                }
                val buffMd5 = DigestUtil.getMD5String(elementData)
                val mergedBufferId = duplicateBufferFilterMap[buffMd5]
                if (mergedBufferId == null) {
                    duplicateBufferFilterMap[buffMd5] = bufferId
                } else {
                    mBmpBufferIdToDeduplicatedIdMap[mergedBufferId] = mergedBufferId
                    mBmpBufferIdToDeduplicatedIdMap[bufferId] = mergedBufferId
                }
            }
            mBufferIdToElementDataMap.clear()
        }
    }

    private inner class HprofBufferShrinkVisitor(
        hprofWriter: HprofWriter,
    ) : HprofVisitor(hprofWriter) {
        override fun visitHeapDumpRecord(tag: Int, timestamp: Int, length: Long): HprofHeapDumpVisitor {
            return object : HprofHeapDumpVisitor(super.visitHeapDumpRecord(tag, timestamp, length)) {
                override fun visitHeapDumpInstance(id: ID, stackId: Int, typeId: ID, instanceData: ByteArray) {
                    try {
                        if (typeId == mBmpClassId) {
                            var bufferId: ID? = null
                            var bufferIdPos = 0
                            val bais = ByteArrayInputStream(instanceData)
                            val fields = mBmpClassInstanceFields ?: emptyArray()
                            for (field in fields) {
                                val fieldNameStringId = field.nameId
                                val fieldType =
                                    Type.getType(field.typeId)
                                        ?: throw IllegalStateException("visit instance failed, lost type def of typeId: ${field.typeId}")
                                if (mMBufferFieldNameStringId == fieldNameStringId) {
                                    bufferId = IOUtil.readValue(bais, fieldType, mIdSize) as ID?
                                    break
                                } else {
                                    bufferIdPos += IOUtil.skipValue(bais, fieldType, mIdSize)
                                }
                            }
                            if (bufferId != null) {
                                val deduplicatedId = mBmpBufferIdToDeduplicatedIdMap[bufferId]
                                if (deduplicatedId != null && bufferId != deduplicatedId && bufferId != mNullBufferId) {
                                    modifyIdInBuffer(instanceData, bufferIdPos, deduplicatedId)
                                }
                            }
                        }
                    } catch (thr: Throwable) {
                        throw RuntimeException(thr)
                    }
                    super.visitHeapDumpInstance(id, stackId, typeId, instanceData)
                }

                private fun modifyIdInBuffer(buf: ByteArray, off: Int, newId: ID) {
                    val byteBuffer = ByteBuffer.wrap(buf)
                    byteBuffer.position(off)
                    byteBuffer.put(newId.getBytes())
                }

                override fun visitHeapDumpPrimitiveArray(
                    tag: Int,
                    id: ID,
                    stackId: Int,
                    numElements: Int,
                    typeId: Int,
                    elements: ByteArray,
                ) {
                    val deduplicatedID = mBmpBufferIdToDeduplicatedIdMap[id]
                    if (deduplicatedID == null || id != deduplicatedID) {
                        if (!mStringValueIds.contains(id)) {
                            return
                        }
                    }
                    super.visitHeapDumpPrimitiveArray(tag, id, stackId, numElements, typeId, elements)
                }
            }
        }
    }

    companion object {
        const val TAG = "TraceHarbor.HprofBufferShrinker"
        private const val PROPERTY_NAME = "extra.info"

        @JvmStatic
        fun addExtraInfo(shrinkResultFile: File?, properties: Properties): Boolean {
            if (shrinkResultFile == null || !shrinkResultFile.exists()) {
                return false
            }
            if (properties.isEmpty) {
                return true
            }
            val start = System.currentTimeMillis()
            var propertiesOutputStream: OutputStream? = null
            val propertiesFile = File(shrinkResultFile.parentFile, PROPERTY_NAME)
            val tempFile = File(shrinkResultFile.absolutePath + "_temp")

            try {
                propertiesOutputStream = BufferedOutputStream(FileOutputStream(propertiesFile, false))
                properties.store(propertiesOutputStream, null)
            } catch (throwable: Throwable) {
                TraceHarborLog.e(TAG, "save property error:$throwable")
                return false
            } finally {
                TraceHarborUtil.closeQuietly(propertiesOutputStream)
            }

            var out: TinkerZipOutputStream? = null
            var zipFile: TinkerZipFile? = null
            try {
                out = TinkerZipOutputStream(BufferedOutputStream(FileOutputStream(tempFile)))
                zipFile = TinkerZipFile(shrinkResultFile)
                val entries = zipFile.entries()

                while (entries.hasMoreElements()) {
                    val zipEntry = entries.nextElement()
                    if (zipEntry == null) {
                        throw RuntimeException("zipEntry is null when get from oldApk")
                    }
                    val name = zipEntry.name
                    if (name.contains("../")) {
                        continue
                    }
                    TinkerZipUtil.extractTinkerEntry(zipFile, zipEntry, out)
                }
                val crc = getCRC32(propertiesFile)
                if (crc == null) {
                    TraceHarborLog.e(TAG, "new crc is null")
                    return false
                }
                val propertyEntry = TinkerZipEntry(propertiesFile.name)
                TinkerZipUtil.extractLargeModifyFile(propertyEntry, propertiesFile, crc, out)
            } catch (e: IOException) {
                TraceHarborLog.e(TAG, "zip property error:$e")
                return false
            } finally {
                TraceHarborUtil.closeQuietly(zipFile)
                TraceHarborUtil.closeQuietly(out)
                propertiesFile.delete()
            }

            shrinkResultFile.delete()
            if (!tempFile.renameTo(shrinkResultFile)) {
                TraceHarborLog.e(TAG, "rename error")
                return false
            }

            TraceHarborLog.i(
                TAG,
                "addExtraInfo end, path: %s, cost time: %d",
                shrinkResultFile.absolutePath,
                System.currentTimeMillis() - start,
            )
            return true
        }

        private fun getCRC32(file: File): Long? {
            val crc32 = CRC32()
            var fileInputStream: FileInputStream? = null
            return try {
                fileInputStream = FileInputStream(file)
                val buffer = ByteArray(8192)
                var length: Int
                while (fileInputStream.read(buffer).also { length = it } != -1) {
                    crc32.update(buffer, 0, length)
                }
                crc32.value
            } catch (_: IOException) {
                null
            } finally {
                TraceHarborUtil.closeQuietly(fileInputStream)
            }
        }
    }
}

