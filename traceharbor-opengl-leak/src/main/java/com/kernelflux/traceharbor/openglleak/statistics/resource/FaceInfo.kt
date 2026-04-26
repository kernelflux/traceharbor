package com.kernelflux.traceharbor.openglleak.statistics.resource

class FaceInfo {
    var size: Long = 0
    var target: Int = 0
    var id: Int = 0
    var eglContextNativeHandle: Long = 0
    var level: Int = 0
    var internalFormat: Int = 0
    var width: Int = 0
    var height: Int = 0
    var depth: Int = 0
    var border: Int = 0
    var format: Int = 0
    var type: Int = 0

    override fun toString(): String {
        return "FaceInfo{" +
            "size=$size" +
            ", target=$target" +
            ", id=$id" +
            ", eglContextNativeHandle=$eglContextNativeHandle" +
            ", level=$level" +
            ", internalFormat=$internalFormat" +
            ", width=$width" +
            ", height=$height" +
            ", depth=$depth" +
            ", border=$border" +
            ", format=$format" +
            ", type=$type" +
            '}'
    }
}

