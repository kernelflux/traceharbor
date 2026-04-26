package com.kernelflux.traceharbor.resource.hproflib

import com.kernelflux.traceharbor.resource.hproflib.model.Field
import com.kernelflux.traceharbor.resource.hproflib.model.ID
import com.kernelflux.traceharbor.resource.hproflib.model.Type
import com.kernelflux.traceharbor.resource.hproflib.utils.IOUtil
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

class HprofWriter(
    private val mStreamOut: OutputStream,
) : HprofVisitor(null) {
    private var mIdSize = 0
    private val mHeapDumpOut = ByteArrayOutputStream()

    override fun visitHeader(text: String, idSize: Int, timestamp: Long) {
        try {
            if (idSize <= 0 || idSize >= (Int.MAX_VALUE shr 1)) {
                throw IOException("bad idSize: $idSize")
            }
            mIdSize = idSize
            IOUtil.writeNullTerminatedString(mStreamOut, text)
            IOUtil.writeBEInt(mStreamOut, idSize)
            IOUtil.writeBELong(mStreamOut, timestamp)
        } catch (thr: Throwable) {
            throw RuntimeException(thr)
        }
    }

    override fun visitStringRecord(id: ID, text: String, timestamp: Int, length: Long) {
        try {
            mStreamOut.write(HprofConstants.RECORD_TAG_STRING)
            IOUtil.writeBEInt(mStreamOut, timestamp)
            IOUtil.writeBEInt(mStreamOut, length.toInt())
            mStreamOut.write(id.getBytes())
            IOUtil.writeString(mStreamOut, text)
        } catch (thr: Throwable) {
            throw RuntimeException(thr)
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
        try {
            mStreamOut.write(HprofConstants.RECORD_TAG_LOAD_CLASS)
            IOUtil.writeBEInt(mStreamOut, timestamp)
            IOUtil.writeBEInt(mStreamOut, length.toInt())
            IOUtil.writeBEInt(mStreamOut, serialNumber)
            mStreamOut.write(classObjectId.getBytes())
            IOUtil.writeBEInt(mStreamOut, stackTraceSerial)
            mStreamOut.write(classNameStringId.getBytes())
        } catch (thr: Throwable) {
            throw RuntimeException(thr)
        }
    }

    override fun visitStackFrameRecord(
        id: ID,
        methodNameId: ID,
        methodSignatureId: ID,
        sourceFileId: ID,
        serial: Int,
        lineNumber: Int,
        timestamp: Int,
        length: Long,
    ) {
        try {
            mStreamOut.write(HprofConstants.RECORD_TAG_STACK_FRAME)
            IOUtil.writeBEInt(mStreamOut, timestamp)
            IOUtil.writeBEInt(mStreamOut, length.toInt())
            mStreamOut.write(id.getBytes())
            mStreamOut.write(methodNameId.getBytes())
            mStreamOut.write(methodSignatureId.getBytes())
            mStreamOut.write(sourceFileId.getBytes())
            IOUtil.writeBEInt(mStreamOut, serial)
            IOUtil.writeBEInt(mStreamOut, lineNumber)
        } catch (thr: Throwable) {
            throw RuntimeException(thr)
        }
    }

    override fun visitStackTraceRecord(
        serialNumber: Int,
        threadSerialNumber: Int,
        frameIds: Array<ID>,
        timestamp: Int,
        length: Long,
    ) {
        try {
            mStreamOut.write(HprofConstants.RECORD_TAG_STACK_TRACE)
            IOUtil.writeBEInt(mStreamOut, timestamp)
            IOUtil.writeBEInt(mStreamOut, length.toInt())
            IOUtil.writeBEInt(mStreamOut, serialNumber)
            IOUtil.writeBEInt(mStreamOut, threadSerialNumber)
            IOUtil.writeBEInt(mStreamOut, frameIds.size)
            for (frameId in frameIds) {
                mStreamOut.write(frameId.getBytes())
            }
        } catch (thr: Throwable) {
            throw RuntimeException(thr)
        }
    }

    override fun visitHeapDumpRecord(tag: Int, timestamp: Int, length: Long): HprofHeapDumpVisitor {
        try {
            return HprofHeapDumpWriter(tag, timestamp, length)
        } catch (thr: Throwable) {
            throw RuntimeException(thr)
        }
    }

    override fun visitUnconcernedRecord(tag: Int, timestamp: Int, length: Long, data: ByteArray) {
        try {
            mStreamOut.write(tag)
            IOUtil.writeBEInt(mStreamOut, timestamp)
            IOUtil.writeBEInt(mStreamOut, length.toInt())
            mStreamOut.write(data, 0, length.toInt())
        } catch (thr: Throwable) {
            throw RuntimeException(thr)
        }
    }

    override fun visitEnd() {
        try {
            mStreamOut.flush()
        } catch (thr: Throwable) {
            throw RuntimeException(thr)
        }
    }

    private inner class HprofHeapDumpWriter(
        private val mTag: Int,
        private val mTimestamp: Int,
        private val mOrigLength: Long,
    ) : HprofHeapDumpVisitor(null) {
        override fun visitHeapDumpInfo(heapId: Int, heapNameId: ID) {
            try {
                mHeapDumpOut.write(HprofConstants.HEAPDUMP_ROOT_HEAP_DUMP_INFO)
                IOUtil.writeBEInt(mHeapDumpOut, heapId)
                mHeapDumpOut.write(heapNameId.getBytes())
            } catch (thr: Throwable) {
                throw RuntimeException(thr)
            }
        }

        override fun visitHeapDumpBasicObj(tag: Int, id: ID) {
            try {
                mHeapDumpOut.write(tag)
                mHeapDumpOut.write(id.getBytes())
                if (tag == HprofConstants.HEAPDUMP_ROOT_JNI_GLOBAL) {
                    IOUtil.skip(mHeapDumpOut, mIdSize.toLong())
                }
            } catch (thr: Throwable) {
                throw RuntimeException(thr)
            }
        }

        override fun visitHeapDumpJniLocal(id: ID, threadSerialNumber: Int, stackFrameNumber: Int) {
            try {
                mHeapDumpOut.write(HprofConstants.HEAPDUMP_ROOT_JNI_LOCAL)
                mHeapDumpOut.write(id.getBytes())
                IOUtil.writeBEInt(mHeapDumpOut, threadSerialNumber)
                IOUtil.writeBEInt(mHeapDumpOut, stackFrameNumber)
            } catch (thr: Throwable) {
                throw RuntimeException(thr)
            }
        }

        override fun visitHeapDumpJavaFrame(id: ID, threadSerialNumber: Int, stackFrameNumber: Int) {
            try {
                mHeapDumpOut.write(HprofConstants.HEAPDUMP_ROOT_JAVA_FRAME)
                mHeapDumpOut.write(id.getBytes())
                IOUtil.writeBEInt(mHeapDumpOut, threadSerialNumber)
                IOUtil.writeBEInt(mHeapDumpOut, stackFrameNumber)
            } catch (thr: Throwable) {
                throw RuntimeException(thr)
            }
        }

        override fun visitHeapDumpNativeStack(id: ID, threadSerialNumber: Int) {
            try {
                mHeapDumpOut.write(HprofConstants.HEAPDUMP_ROOT_NATIVE_STACK)
                mHeapDumpOut.write(id.getBytes())
                IOUtil.writeBEInt(mHeapDumpOut, threadSerialNumber)
            } catch (thr: Throwable) {
                throw RuntimeException(thr)
            }
        }

        override fun visitHeapDumpThreadBlock(id: ID, threadSerialNumber: Int) {
            try {
                mHeapDumpOut.write(HprofConstants.HEAPDUMP_ROOT_THREAD_BLOCK)
                mHeapDumpOut.write(id.getBytes())
                IOUtil.writeBEInt(mHeapDumpOut, threadSerialNumber)
            } catch (thr: Throwable) {
                throw RuntimeException(thr)
            }
        }

        override fun visitHeapDumpThreadObject(id: ID, threadSerialNumber: Int, stackFrameNumber: Int) {
            try {
                mHeapDumpOut.write(HprofConstants.HEAPDUMP_ROOT_THREAD_OBJECT)
                mHeapDumpOut.write(id.getBytes())
                IOUtil.writeBEInt(mHeapDumpOut, threadSerialNumber)
                IOUtil.writeBEInt(mHeapDumpOut, stackFrameNumber)
            } catch (thr: Throwable) {
                throw RuntimeException(thr)
            }
        }

        override fun visitHeapDumpClass(
            id: ID,
            stackSerialNumber: Int,
            superClassId: ID,
            classLoaderId: ID,
            instanceSize: Int,
            staticFields: Array<Field>,
            instanceFields: Array<Field>,
        ) {
            try {
                mHeapDumpOut.write(HprofConstants.HEAPDUMP_ROOT_CLASS_DUMP)
                mHeapDumpOut.write(id.getBytes())
                IOUtil.writeBEInt(mHeapDumpOut, stackSerialNumber)
                mHeapDumpOut.write(superClassId.getBytes())
                mHeapDumpOut.write(classLoaderId.getBytes())
                IOUtil.skip(mHeapDumpOut, (mIdSize shl 2).toLong())
                IOUtil.writeBEInt(mHeapDumpOut, instanceSize)

                IOUtil.writeBEShort(mHeapDumpOut, 0)

                IOUtil.writeBEShort(mHeapDumpOut, staticFields.size)
                for (field in staticFields) {
                    IOUtil.writeID(mHeapDumpOut, field.nameId)
                    mHeapDumpOut.write(field.typeId)
                    IOUtil.writeValue(mHeapDumpOut, field.staticValue)
                }

                IOUtil.writeBEShort(mHeapDumpOut, instanceFields.size)
                for (field in instanceFields) {
                    IOUtil.writeID(mHeapDumpOut, field.nameId)
                    mHeapDumpOut.write(field.typeId)
                }
            } catch (thr: Throwable) {
                throw RuntimeException(thr)
            }
        }

        override fun visitHeapDumpInstance(id: ID, stackId: Int, typeId: ID, instanceData: ByteArray) {
            try {
                mHeapDumpOut.write(HprofConstants.HEAPDUMP_ROOT_INSTANCE_DUMP)
                mHeapDumpOut.write(id.getBytes())
                IOUtil.writeBEInt(mHeapDumpOut, stackId)
                mHeapDumpOut.write(typeId.getBytes())
                IOUtil.writeBEInt(mHeapDumpOut, instanceData.size)
                mHeapDumpOut.write(instanceData)
            } catch (thr: Throwable) {
                throw RuntimeException(thr)
            }
        }

        override fun visitHeapDumpObjectArray(
            id: ID,
            stackId: Int,
            numElements: Int,
            typeId: ID,
            elements: ByteArray,
        ) {
            try {
                mHeapDumpOut.write(HprofConstants.HEAPDUMP_ROOT_OBJECT_ARRAY_DUMP)
                mHeapDumpOut.write(id.getBytes())
                IOUtil.writeBEInt(mHeapDumpOut, stackId)
                IOUtil.writeBEInt(mHeapDumpOut, numElements)
                mHeapDumpOut.write(typeId.getBytes())
                val remaining = numElements * mIdSize
                mHeapDumpOut.write(elements, 0, remaining)
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
            try {
                mHeapDumpOut.write(tag)
                mHeapDumpOut.write(id.getBytes())
                IOUtil.writeBEInt(mHeapDumpOut, stackId)
                IOUtil.writeBEInt(mHeapDumpOut, numElements)
                mHeapDumpOut.write(typeId)
                val type = Type.getType(typeId)
                    ?: throw IllegalStateException("cannot find primitive type for typeId: $typeId")
                val remaining = numElements * type.getSize(mIdSize)
                mHeapDumpOut.write(elements, 0, remaining)
            } catch (thr: Throwable) {
                throw RuntimeException(thr)
            }
        }

        override fun visitHeapDumpJniMonitor(id: ID, threadSerialNumber: Int, stackDepth: Int) {
            try {
                mHeapDumpOut.write(HprofConstants.HEAPDUMP_ROOT_JNI_MONITOR)
                mHeapDumpOut.write(id.getBytes())
                IOUtil.writeBEInt(mHeapDumpOut, threadSerialNumber)
                IOUtil.writeBEInt(mHeapDumpOut, stackDepth)
            } catch (thr: Throwable) {
                throw RuntimeException(thr)
            }
        }

        override fun visitEnd() {
            try {
                mStreamOut.write(mTag)
                IOUtil.writeBEInt(mStreamOut, mTimestamp)
                IOUtil.writeBEInt(mStreamOut, mHeapDumpOut.size())
                mStreamOut.write(mHeapDumpOut.toByteArray())
                mHeapDumpOut.reset()
            } catch (thr: Throwable) {
                throw RuntimeException(thr)
            }
        }
    }
}

