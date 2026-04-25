package com.kernelflux.traceharbor.iocanary.core

class IOIssue(
    @JvmField val type: Int,
    @JvmField val path: String?,
    @JvmField val fileSize: Long,
    @JvmField val opCnt: Int,
    @JvmField val bufferSize: Long,
    @JvmField val opCostTime: Long,
    @JvmField val opType: Int,
    @JvmField val opSize: Long,
    @JvmField val threadName: String?,
    @JvmField val stack: String?,
    @JvmField val repeatReadCnt: Int,
)
