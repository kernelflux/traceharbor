package com.kernelflux.traceharbor.resource.hproflib

import com.kernelflux.traceharbor.resource.hproflib.model.Field
import com.kernelflux.traceharbor.resource.hproflib.model.ID
import com.kernelflux.traceharbor.resource.hproflib.model.Type
import com.kernelflux.traceharbor.resource.hproflib.utils.IOUtil
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

class HprofReader(
    private val mStreamIn: InputStream,
) {
    private var mIdSize = 0

    @Throws(IOException::class)
    fun accept(hv: HprofVisitor) {
        acceptHeader(hv)
        acceptRecord(hv)
        hv.visitEnd()
    }

    @Throws(IOException::class)
    private fun acceptHeader(hv: HprofVisitor) {
        val text = IOUtil.readNullTerminatedString(mStreamIn)
        val idSize = IOUtil.readBEInt(mStreamIn)
        if (idSize <= 0 || idSize >= (Int.MAX_VALUE shr 1)) {
            throw IOException("bad idSize: $idSize")
        }
        val timestamp = IOUtil.readBELong(mStreamIn)
        mIdSize = idSize
        hv.visitHeader(text, idSize, timestamp)
    }

    @Throws(IOException::class)
    private fun acceptRecord(hv: HprofVisitor) {
        try {
            while (true) {
                val tag = mStreamIn.read()
                val timestamp = IOUtil.readBEInt(mStreamIn)
                val length = IOUtil.readBEInt(mStreamIn).toLong() and 0x00000000FFFFFFFFL
                when (tag) {
                    HprofConstants.RECORD_TAG_STRING -> acceptStringRecord(timestamp, length, hv)
                    HprofConstants.RECORD_TAG_LOAD_CLASS -> acceptLoadClassRecord(timestamp, length, hv)
                    HprofConstants.RECORD_TAG_STACK_FRAME -> acceptStackFrameRecord(timestamp, length, hv)
                    HprofConstants.RECORD_TAG_STACK_TRACE -> acceptStackTraceRecord(timestamp, length, hv)
                    HprofConstants.RECORD_TAG_HEAP_DUMP,
                    HprofConstants.RECORD_TAG_HEAP_DUMP_SEGMENT,
                    -> acceptHeapDumpRecord(tag, timestamp, length, hv)

                    else -> acceptUnconcernedRecord(tag, timestamp, length, hv)
                }
            }
        } catch (_: EOFException) {
        }
    }

    @Throws(IOException::class)
    private fun acceptStringRecord(timestamp: Int, length: Long, hv: HprofVisitor) {
        val id = IOUtil.readID(mStreamIn, mIdSize)
        val text = IOUtil.readString(mStreamIn, length - mIdSize)
        hv.visitStringRecord(id, text, timestamp, length)
    }

    @Throws(IOException::class)
    private fun acceptLoadClassRecord(timestamp: Int, length: Long, hv: HprofVisitor) {
        val serialNumber = IOUtil.readBEInt(mStreamIn)
        val classObjectId = IOUtil.readID(mStreamIn, mIdSize)
        val stackTraceSerial = IOUtil.readBEInt(mStreamIn)
        val classNameStringId = IOUtil.readID(mStreamIn, mIdSize)
        hv.visitLoadClassRecord(serialNumber, classObjectId, stackTraceSerial, classNameStringId, timestamp, length)
    }

    @Throws(IOException::class)
    private fun acceptStackFrameRecord(timestamp: Int, length: Long, hv: HprofVisitor) {
        val id = IOUtil.readID(mStreamIn, mIdSize)
        val methodNameId = IOUtil.readID(mStreamIn, mIdSize)
        val methodSignatureId = IOUtil.readID(mStreamIn, mIdSize)
        val sourceFileId = IOUtil.readID(mStreamIn, mIdSize)
        val serial = IOUtil.readBEInt(mStreamIn)
        val lineNumber = IOUtil.readBEInt(mStreamIn)
        hv.visitStackFrameRecord(id, methodNameId, methodSignatureId, sourceFileId, serial, lineNumber, timestamp, length)
    }

    @Throws(IOException::class)
    private fun acceptStackTraceRecord(timestamp: Int, length: Long, hv: HprofVisitor) {
        val serialNumber = IOUtil.readBEInt(mStreamIn)
        val threadSerialNumber = IOUtil.readBEInt(mStreamIn)
        val numFrames = IOUtil.readBEInt(mStreamIn)
        val frameIds = Array(numFrames) { IOUtil.readID(mStreamIn, mIdSize) }
        hv.visitStackTraceRecord(serialNumber, threadSerialNumber, frameIds, timestamp, length)
    }

    @Throws(IOException::class)
    private fun acceptHeapDumpRecord(tag: Int, timestamp: Int, length: Long, hv: HprofVisitor) {
        val hdv = hv.visitHeapDumpRecord(tag, timestamp, length)
        if (hdv == null) {
            IOUtil.skip(mStreamIn, length)
            return
        }
        var remaining = length
        while (remaining > 0) {
            val heapDumpTag = mStreamIn.read()
            --remaining
            when (heapDumpTag) {
                HprofConstants.HEAPDUMP_ROOT_UNKNOWN -> {
                    hdv.visitHeapDumpBasicObj(heapDumpTag, IOUtil.readID(mStreamIn, mIdSize))
                    remaining -= mIdSize.toLong()
                }

                HprofConstants.HEAPDUMP_ROOT_JNI_GLOBAL -> {
                    hdv.visitHeapDumpBasicObj(heapDumpTag, IOUtil.readID(mStreamIn, mIdSize))
                    IOUtil.skip(mStreamIn, mIdSize.toLong())
                    remaining -= (mIdSize shl 1).toLong()
                }

                HprofConstants.HEAPDUMP_ROOT_JNI_LOCAL -> remaining -= acceptJniLocal(hdv).toLong()
                HprofConstants.HEAPDUMP_ROOT_JAVA_FRAME -> remaining -= acceptJavaFrame(hdv).toLong()
                HprofConstants.HEAPDUMP_ROOT_NATIVE_STACK -> remaining -= acceptNativeStack(hdv).toLong()

                HprofConstants.HEAPDUMP_ROOT_STICKY_CLASS -> {
                    hdv.visitHeapDumpBasicObj(heapDumpTag, IOUtil.readID(mStreamIn, mIdSize))
                    remaining -= mIdSize.toLong()
                }

                HprofConstants.HEAPDUMP_ROOT_THREAD_BLOCK -> remaining -= acceptThreadBlock(hdv).toLong()

                HprofConstants.HEAPDUMP_ROOT_MONITOR_USED -> {
                    hdv.visitHeapDumpBasicObj(heapDumpTag, IOUtil.readID(mStreamIn, mIdSize))
                    remaining -= mIdSize.toLong()
                }

                HprofConstants.HEAPDUMP_ROOT_THREAD_OBJECT -> remaining -= acceptThreadObject(hdv).toLong()
                HprofConstants.HEAPDUMP_ROOT_CLASS_DUMP -> remaining -= acceptClassDump(hdv).toLong()
                HprofConstants.HEAPDUMP_ROOT_INSTANCE_DUMP -> remaining -= acceptInstanceDump(hdv).toLong()
                HprofConstants.HEAPDUMP_ROOT_OBJECT_ARRAY_DUMP -> remaining -= acceptObjectArrayDump(hdv).toLong()
                HprofConstants.HEAPDUMP_ROOT_PRIMITIVE_ARRAY_DUMP,
                HprofConstants.HEAPDUMP_ROOT_PRIMITIVE_ARRAY_NODATA_DUMP,
                -> remaining -= acceptPrimitiveArrayDump(heapDumpTag, hdv).toLong()

                HprofConstants.HEAPDUMP_ROOT_HEAP_DUMP_INFO -> remaining -= acceptHeapDumpInfo(hdv).toLong()

                HprofConstants.HEAPDUMP_ROOT_INTERNED_STRING,
                HprofConstants.HEAPDUMP_ROOT_FINALIZING,
                HprofConstants.HEAPDUMP_ROOT_DEBUGGER,
                HprofConstants.HEAPDUMP_ROOT_REFERENCE_CLEANUP,
                HprofConstants.HEAPDUMP_ROOT_VM_INTERNAL,
                HprofConstants.HEAPDUMP_ROOT_UNREACHABLE,
                -> {
                    hdv.visitHeapDumpBasicObj(heapDumpTag, IOUtil.readID(mStreamIn, mIdSize))
                    remaining -= mIdSize.toLong()
                }

                HprofConstants.HEAPDUMP_ROOT_JNI_MONITOR -> remaining -= acceptJniMonitor(hdv).toLong()
                else -> {
                    throw IllegalArgumentException(
                        "acceptHeapDumpRecord loop with unknown tag $heapDumpTag with ${mStreamIn.available()} bytes possibly remaining",
                    )
                }
            }
        }
        hdv.visitEnd()
    }

    @Throws(IOException::class)
    private fun acceptUnconcernedRecord(tag: Int, timestamp: Int, length: Long, hv: HprofVisitor) {
        val data = ByteArray(length.toInt())
        IOUtil.readFully(mStreamIn, data, 0, length)
        hv.visitUnconcernedRecord(tag, timestamp, length, data)
    }

    @Throws(IOException::class)
    private fun acceptHeapDumpInfo(hdv: HprofHeapDumpVisitor): Int {
        val heapId = IOUtil.readBEInt(mStreamIn)
        val heapNameId = IOUtil.readID(mStreamIn, mIdSize)
        hdv.visitHeapDumpInfo(heapId, heapNameId)
        return 4 + mIdSize
    }

    @Throws(IOException::class)
    private fun acceptJniLocal(hdv: HprofHeapDumpVisitor): Int {
        val id = IOUtil.readID(mStreamIn, mIdSize)
        val threadSerialNumber = IOUtil.readBEInt(mStreamIn)
        val stackFrameNumber = IOUtil.readBEInt(mStreamIn)
        hdv.visitHeapDumpJniLocal(id, threadSerialNumber, stackFrameNumber)
        return mIdSize + 4 + 4
    }

    @Throws(IOException::class)
    private fun acceptJavaFrame(hdv: HprofHeapDumpVisitor): Int {
        val id = IOUtil.readID(mStreamIn, mIdSize)
        val threadSerialNumber = IOUtil.readBEInt(mStreamIn)
        val stackFrameNumber = IOUtil.readBEInt(mStreamIn)
        hdv.visitHeapDumpJavaFrame(id, threadSerialNumber, stackFrameNumber)
        return mIdSize + 4 + 4
    }

    @Throws(IOException::class)
    private fun acceptNativeStack(hdv: HprofHeapDumpVisitor): Int {
        val id = IOUtil.readID(mStreamIn, mIdSize)
        val threadSerialNumber = IOUtil.readBEInt(mStreamIn)
        hdv.visitHeapDumpNativeStack(id, threadSerialNumber)
        return mIdSize + 4
    }

    @Throws(IOException::class)
    private fun acceptThreadBlock(hdv: HprofHeapDumpVisitor): Int {
        val id = IOUtil.readID(mStreamIn, mIdSize)
        val threadSerialNumber = IOUtil.readBEInt(mStreamIn)
        hdv.visitHeapDumpThreadBlock(id, threadSerialNumber)
        return mIdSize + 4
    }

    @Throws(IOException::class)
    private fun acceptThreadObject(hdv: HprofHeapDumpVisitor): Int {
        val id = IOUtil.readID(mStreamIn, mIdSize)
        val threadSerialNumber = IOUtil.readBEInt(mStreamIn)
        val stackFrameNumber = IOUtil.readBEInt(mStreamIn)
        hdv.visitHeapDumpThreadObject(id, threadSerialNumber, stackFrameNumber)
        return mIdSize + 4 + 4
    }

    @Throws(IOException::class)
    private fun acceptClassDump(hdv: HprofHeapDumpVisitor): Int {
        val id = IOUtil.readID(mStreamIn, mIdSize)
        val stackSerialNumber = IOUtil.readBEInt(mStreamIn)
        val superClassId = IOUtil.readID(mStreamIn, mIdSize)
        val classLoaderId = IOUtil.readID(mStreamIn, mIdSize)
        IOUtil.skip(mStreamIn, (mIdSize shl 2).toLong())
        val instanceSize = IOUtil.readBEInt(mStreamIn)

        var bytesRead = (7 * mIdSize) + 4 + 4
        var numEntries = IOUtil.readBEShort(mStreamIn).toInt()
        bytesRead += 2
        for (i in 0 until numEntries) {
            IOUtil.skip(mStreamIn, 2)
            bytesRead += 2 + skipValue()
        }

        numEntries = IOUtil.readBEShort(mStreamIn).toInt()
        val staticFields = arrayOfNulls<Field>(numEntries)
        bytesRead += 2
        for (i in 0 until numEntries) {
            val nameId = IOUtil.readID(mStreamIn, mIdSize)
            val typeId = mStreamIn.read()
            val type = Type.getType(typeId)
                ?: throw IllegalStateException("accept class failed, lost type def of typeId: $typeId")
            val staticValue = IOUtil.readValue(mStreamIn, type, mIdSize)
            staticFields[i] = Field(typeId, nameId, staticValue)
            bytesRead += mIdSize + 1 + type.getSize(mIdSize)
        }

        numEntries = IOUtil.readBEShort(mStreamIn).toInt()
        val instanceFields = arrayOfNulls<Field>(numEntries)
        bytesRead += 2
        for (i in 0 until numEntries) {
            val nameId = IOUtil.readID(mStreamIn, mIdSize)
            val typeId = mStreamIn.read()
            instanceFields[i] = Field(typeId, nameId, null)
            bytesRead += mIdSize + 1
        }

        hdv.visitHeapDumpClass(
            id,
            stackSerialNumber,
            superClassId,
            classLoaderId,
            instanceSize,
            staticFields.requireNoNulls(),
            instanceFields.requireNoNulls(),
        )
        return bytesRead
    }

    @Throws(IOException::class)
    private fun acceptInstanceDump(hdv: HprofHeapDumpVisitor): Int {
        val id = IOUtil.readID(mStreamIn, mIdSize)
        val stackId = IOUtil.readBEInt(mStreamIn)
        val typeId = IOUtil.readID(mStreamIn, mIdSize)
        val remaining = IOUtil.readBEInt(mStreamIn)
        val instanceData = ByteArray(remaining)
        IOUtil.readFully(mStreamIn, instanceData, 0, remaining.toLong())
        hdv.visitHeapDumpInstance(id, stackId, typeId, instanceData)
        return mIdSize + 4 + mIdSize + 4 + remaining
    }

    @Throws(IOException::class)
    private fun acceptObjectArrayDump(hdv: HprofHeapDumpVisitor): Int {
        val id = IOUtil.readID(mStreamIn, mIdSize)
        val stackId = IOUtil.readBEInt(mStreamIn)
        val numElements = IOUtil.readBEInt(mStreamIn)
        val typeId = IOUtil.readID(mStreamIn, mIdSize)
        val remaining = numElements * mIdSize
        val elements = ByteArray(remaining)
        IOUtil.readFully(mStreamIn, elements, 0, remaining.toLong())
        hdv.visitHeapDumpObjectArray(id, stackId, numElements, typeId, elements)
        return mIdSize + 4 + 4 + mIdSize + remaining
    }

    @Throws(IOException::class)
    private fun acceptPrimitiveArrayDump(tag: Int, hdv: HprofHeapDumpVisitor): Int {
        val id = IOUtil.readID(mStreamIn, mIdSize)
        val stackId = IOUtil.readBEInt(mStreamIn)
        val numElements = IOUtil.readBEInt(mStreamIn)
        val typeId = mStreamIn.read()
        val type = Type.getType(typeId)
            ?: throw IllegalStateException("accept primitive array failed, lost type def of typeId: $typeId")
        val remaining = numElements * type.getSize(mIdSize)
        val elements = ByteArray(remaining)
        IOUtil.readFully(mStreamIn, elements, 0, remaining.toLong())
        hdv.visitHeapDumpPrimitiveArray(tag, id, stackId, numElements, typeId, elements)
        return mIdSize + 4 + 4 + 1 + remaining
    }

    @Throws(IOException::class)
    private fun acceptJniMonitor(hdv: HprofHeapDumpVisitor): Int {
        val id = IOUtil.readID(mStreamIn, mIdSize)
        val threadSerialNumber = IOUtil.readBEInt(mStreamIn)
        val stackDepth = IOUtil.readBEInt(mStreamIn)
        hdv.visitHeapDumpJniMonitor(id, threadSerialNumber, stackDepth)
        return mIdSize + 4 + 4
    }

    @Throws(IOException::class)
    private fun skipValue(): Int {
        val typeId = mStreamIn.read()
        val type = Type.getType(typeId)
            ?: throw IllegalStateException("failure to skip type, cannot find type def of typeid: $typeId")
        val size = type.getSize(mIdSize)
        IOUtil.skip(mStreamIn, size.toLong())
        return size + 1
    }
}

