package com.kernelflux.traceharbor.resource.hproflib

import com.kernelflux.traceharbor.resource.hproflib.model.Field
import com.kernelflux.traceharbor.resource.hproflib.model.ID

@Suppress("unused")
open class HprofHeapDumpVisitor(
    protected val hdv: HprofHeapDumpVisitor?,
) {
    open fun visitHeapDumpInfo(heapId: Int, heapNameId: ID) {
        hdv?.visitHeapDumpInfo(heapId, heapNameId)
    }

    open fun visitHeapDumpBasicObj(tag: Int, id: ID) {
        hdv?.visitHeapDumpBasicObj(tag, id)
    }

    open fun visitHeapDumpJniLocal(id: ID, threadSerialNumber: Int, stackFrameNumber: Int) {
        hdv?.visitHeapDumpJniLocal(id, threadSerialNumber, stackFrameNumber)
    }

    open fun visitHeapDumpJavaFrame(id: ID, threadSerialNumber: Int, stackFrameNumber: Int) {
        hdv?.visitHeapDumpJavaFrame(id, threadSerialNumber, stackFrameNumber)
    }

    open fun visitHeapDumpNativeStack(id: ID, threadSerialNumber: Int) {
        hdv?.visitHeapDumpNativeStack(id, threadSerialNumber)
    }

    open fun visitHeapDumpThreadBlock(id: ID, threadSerialNumber: Int) {
        hdv?.visitHeapDumpThreadBlock(id, threadSerialNumber)
    }

    open fun visitHeapDumpThreadObject(id: ID, threadSerialNumber: Int, stackFrameNumber: Int) {
        hdv?.visitHeapDumpThreadObject(id, threadSerialNumber, stackFrameNumber)
    }

    open fun visitHeapDumpClass(
        id: ID,
        stackSerialNumber: Int,
        superClassId: ID,
        classLoaderId: ID,
        instanceSize: Int,
        staticFields: Array<Field>,
        instanceFields: Array<Field>,
    ) {
        hdv?.visitHeapDumpClass(
            id,
            stackSerialNumber,
            superClassId,
            classLoaderId,
            instanceSize,
            staticFields,
            instanceFields,
        )
    }

    open fun visitHeapDumpInstance(id: ID, stackId: Int, typeId: ID, instanceData: ByteArray) {
        hdv?.visitHeapDumpInstance(id, stackId, typeId, instanceData)
    }

    open fun visitHeapDumpJniMonitor(id: ID, threadSerialNumber: Int, stackDepth: Int) {
        hdv?.visitHeapDumpJniMonitor(id, threadSerialNumber, stackDepth)
    }

    open fun visitHeapDumpPrimitiveArray(
        tag: Int,
        id: ID,
        stackId: Int,
        numElements: Int,
        typeId: Int,
        elements: ByteArray,
    ) {
        hdv?.visitHeapDumpPrimitiveArray(tag, id, stackId, numElements, typeId, elements)
    }

    open fun visitHeapDumpObjectArray(
        id: ID,
        stackId: Int,
        numElements: Int,
        typeId: ID,
        elements: ByteArray,
    ) {
        hdv?.visitHeapDumpObjectArray(id, stackId, numElements, typeId, elements)
    }

    open fun visitEnd() {
        hdv?.visitEnd()
    }
}

