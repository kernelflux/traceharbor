package com.kernelflux.traceharbor.resource.hproflib

import com.kernelflux.traceharbor.resource.hproflib.model.ID

@Suppress("unused")
open class HprofVisitor(
    protected var hv: HprofVisitor?,
) {
    open fun visitHeader(text: String, idSize: Int, timestamp: Long) {
        hv?.visitHeader(text, idSize, timestamp)
    }

    open fun visitStringRecord(id: ID, text: String, timestamp: Int, length: Long) {
        hv?.visitStringRecord(id, text, timestamp, length)
    }

    open fun visitLoadClassRecord(
        serialNumber: Int,
        classObjectId: ID,
        stackTraceSerial: Int,
        classNameStringId: ID,
        timestamp: Int,
        length: Long,
    ) {
        hv?.visitLoadClassRecord(
            serialNumber,
            classObjectId,
            stackTraceSerial,
            classNameStringId,
            timestamp,
            length,
        )
    }

    open fun visitStackFrameRecord(
        id: ID,
        methodNameId: ID,
        methodSignatureId: ID,
        sourceFileId: ID,
        serial: Int,
        lineNumber: Int,
        timestamp: Int,
        length: Long,
    ) {
        hv?.visitStackFrameRecord(
            id,
            methodNameId,
            methodSignatureId,
            sourceFileId,
            serial,
            lineNumber,
            timestamp,
            length,
        )
    }

    open fun visitStackTraceRecord(
        serialNumber: Int,
        threadSerialNumber: Int,
        frameIds: Array<ID>,
        timestamp: Int,
        length: Long,
    ) {
        hv?.visitStackTraceRecord(serialNumber, threadSerialNumber, frameIds, timestamp, length)
    }

    open fun visitHeapDumpRecord(tag: Int, timestamp: Int, length: Long): HprofHeapDumpVisitor? {
        return hv?.visitHeapDumpRecord(tag, timestamp, length)
    }

    open fun visitUnconcernedRecord(tag: Int, timestamp: Int, length: Long, data: ByteArray) {
        hv?.visitUnconcernedRecord(tag, timestamp, length, data)
    }

    open fun visitEnd() {
        hv?.visitEnd()
    }
}

