package com.kernelflux.traceharbor.openglleak.statistics.resource

class OpenGLDumpInfo(
    @JvmField val innerInfo: OpenGLInfo,
) {
    private val idList: MutableList<Int> = ArrayList()
    private val paramsList: MutableList<String> = ArrayList()
    private var totalSize: Long = 0
    private var allocCount: Int = 1

    init {
        idList.add(innerInfo.getId())
        appendParamsInfos(innerInfo.getMemoryInfo())
        val memoryInfo = innerInfo.getMemoryInfo()
        if (memoryInfo != null) {
            totalSize += memoryInfo.getSize()
        }
    }

    fun appendParamsInfos(memoryInfo: MemoryInfo?) {
        if (memoryInfo == null) {
            return
        }
        val resType = memoryInfo.resType
        if (resType == OpenGLInfo.TYPE.TEXTURE) {
            val faces = memoryInfo.getFaces()
            if (faces != null) {
                for (faceInfo in faces) {
                    if (faceInfo != null) {
                        paramsList.add(faceInfo.toString())
                    }
                }
            }
        } else if (resType == OpenGLInfo.TYPE.BUFFER) {
            paramsList.add(
                "MemoryInfo{" +
                    "target=" + memoryInfo.target +
                    ", id=" + memoryInfo.id +
                    ", eglContextNativeHandle='" + memoryInfo.eglContextId + '\'' +
                    ", usage=" + memoryInfo.usage +
                    ", size=" + memoryInfo.getSize() +
                    '}',
            )
        } else if (resType == OpenGLInfo.TYPE.RENDER_BUFFERS) {
            paramsList.add(
                "MemoryInfo{" +
                    "target=" + memoryInfo.target +
                    ", id=" + memoryInfo.id +
                    ", eglContextNativeHandle='" + memoryInfo.eglContextId + '\'' +
                    ", internalFormat=" + memoryInfo.internalFormat +
                    ", width=" + memoryInfo.width +
                    ", height=" + memoryInfo.height +
                    ", size=" + memoryInfo.getSize() +
                    '}',
            )
        }
    }

    fun getParamsInfos(): String {
        val result = StringBuilder()
        for (i in paramsList.indices) {
            result.append(" ")
                .append(paramsList[i])
                .append("\n")
        }
        return result.toString()
    }

    fun incAllocRecord(id: Int) {
        allocCount++
        idList.add(id)
    }

    fun appendSize(size: Long) {
        totalSize += size
    }

    fun getTotalSize(): Long = totalSize

    fun getAllocIdList(): String = idList.toString()

    fun getAllocCount(): Int = allocCount
}

