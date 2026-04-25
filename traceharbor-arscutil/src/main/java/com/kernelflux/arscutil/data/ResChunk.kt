package com.kernelflux.arscutil.data

abstract class ResChunk {
    var start: Long = 0
    var type: Short = 0
    var headSize: Short = 0
    var chunkSize: Int = 0
    var headPadding: Int = 0
    var chunkPadding: Int = 0

    abstract fun toBytes(): ByteArray
}
