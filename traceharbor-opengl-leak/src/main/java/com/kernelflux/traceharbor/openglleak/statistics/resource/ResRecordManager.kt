package com.kernelflux.traceharbor.openglleak.statistics.resource

import android.annotation.SuppressLint
import com.kernelflux.traceharbor.openglleak.hook.OpenGLHook
import com.kernelflux.traceharbor.openglleak.utils.AutoWrapBuilder
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.Collections
import java.util.Comparator
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicInteger

// TODO: 2022/12/22 should be deprecated and move to native
class ResRecordManager private constructor() {
    private val mCallbackList: MutableList<Callback> = LinkedList()
    private val mInfoList: MutableList<OpenGLInfo> = LinkedList()
    private val mReleaseSurface: MutableList<Long> = LinkedList()
    private val mContextGroup: MutableMap<Long, MutableSet<Long>> = HashMap()
    private val mContextRecord: MutableList<Long> = ArrayList()

    fun createContext(shareContext: Long, newContext: Long) {
        synchronized(mContextRecord) {
            mContextRecord.add(newContext)
        }
        if (shareContext != 0L) {
            synchronized(mContextGroup) {
                var group = mContextGroup[shareContext]
                if (group == null) {
                    group = HashSet()
                    mContextGroup[shareContext] = group
                }
                mContextGroup[newContext] = group
                group.add(newContext)
                group.add(shareContext)
            }
        }
    }

    fun destroyContext(context: Long) {
        synchronized(mContextRecord) {
            mContextRecord.remove(context)
        }
        synchronized(mContextGroup) {
            if (mContextGroup.containsKey(context)) {
                val group = mContextGroup.remove(context)
                group?.remove(context)
            }
        }
    }

    fun gen(gen: OpenGLInfo?) {
        if (gen == null) {
            return
        }
        synchronized(mInfoList) {
            mInfoList.add(gen)
        }
        synchronized(mCallbackList) {
            for (cb in mCallbackList) {
                cb.gen(gen)
            }
        }
    }

    fun delete(del: OpenGLInfo?) {
        if (del == null) {
            return
        }

        val ctxGroup: Set<Long>? =
            synchronized(mContextGroup) {
                mContextGroup[del.getEglContextNativeHandle()]
            }

        val infoDel: OpenGLInfo
        synchronized(mInfoList) {
            // 之前可能释放过
            var index = mInfoList.indexOf(del)
            if (index == -1 && ctxGroup != null) { // is shared context
                for (ctx in ctxGroup) {
                    val sharedInfo = OpenGLInfo(del.getType(), del.getId(), del.getThreadId(), ctx)
                    index = mInfoList.indexOf(sharedInfo)
                    if (index != -1) {
                        TraceHarborLog.d(TAG, "del info found with shared context: %d, %s", index, ctx)
                        break
                    }
                }
            }

            if (index == -1) {
                TraceHarborLog.d(TAG, "del info not found")
                return
            }

            val info = mInfoList[index]
            infoDel = info

            val counter: AtomicInteger? = info.getCounter()
            if (counter != null) {
                counter.set(counter.get() - 1)
                if (counter.get() == 0) {
                    OpenGLHook.releaseNative(info.getNativeStackPtr())
                    info.releaseJavaStacktrace()
                }
            }

            // 释放 memory info
            val memoryInfo = info.getMemoryInfo()
            if (memoryInfo != null) {
                val memNativePtr = memoryInfo.getNativeStackPtr()
                if (memNativePtr != 0L) {
                    OpenGLHook.releaseNative(memNativePtr)
                    memoryInfo.releaseNativeStackPtr()
                    memoryInfo.releaseJavaStacktrace()
                }
            }

            mInfoList.remove(info)
        }

        synchronized(mCallbackList) {
            for (cb in mCallbackList) {
                cb.delete(infoDel)
            }
        }
    }

    fun findOpenGLInfo(type: OpenGLInfo.TYPE, eglContextId: Long, openGLInfoId: Int): OpenGLInfo? {
        synchronized(mInfoList) {
            for (item in mInfoList) {
                if (type == item.getType() && item.getEglContextNativeHandle() == eglContextId && item.getId() == openGLInfoId) {
                    return item
                }
            }
        }
        return null
    }

    fun getNativeStack(item: OpenGLInfo): String {
        synchronized(mInfoList) {
            // 之前可能释放过
            val index = mInfoList.indexOf(item)
            if (index == -1) {
                return "res already released, can not get native stack"
            }

            var ret = ""
            val info = mInfoList[index]
            val nativeStackPtr = info.getNativeStackPtr()
            if (nativeStackPtr != 0L) {
                ret = OpenGLHook.dumpNativeStack(nativeStackPtr)
            }
            return ret
        }
    }

    fun getBriefNativeStack(item: OpenGLInfo): String {
        synchronized(mInfoList) {
            // 之前可能释放过
            val index = mInfoList.indexOf(item)
            if (index == -1) {
                return "res already released, can not get native stack"
            }

            var ret = ""
            val info = mInfoList[index]
            val nativeStackPtr = info.getNativeStackPtr()
            if (nativeStackPtr != 0L) {
                ret = OpenGLHook.dumpBriefNativeStack(nativeStackPtr)
            }
            return ret
        }
    }

    fun registerCallback(callback: Callback?) {
        if (callback == null) {
            return
        }
        synchronized(mCallbackList) {
            if (mCallbackList.contains(callback)) {
                return
            }
            mCallbackList.add(callback)
        }
    }

    fun unregisterCallback(callback: Callback?) {
        if (callback == null) {
            return
        }
        synchronized(mCallbackList) {
            mCallbackList.remove(callback)
        }
    }

    fun isGLInfoRelease(item: OpenGLInfo): Boolean {
        synchronized(mInfoList) {
            return !mInfoList.contains(item)
        }
    }

    fun clear() {
        synchronized(mInfoList) {
            mInfoList.clear()
        }
    }

    fun isEglContextReleased(info: OpenGLInfo): Boolean {
        synchronized(mContextRecord) {
            return !mContextRecord.contains(info.getEglContextNativeHandle())
        }
    }

    fun isEglSurfaceReleased(info: OpenGLInfo): Boolean {
        synchronized(mReleaseSurface) {
            val eglDrawSurface = info.getEglDrawSurface()
            val eglReadSurface = info.getEglReadSurface()
            var drawRelease = false
            var readRelease = false
            if (eglReadSurface == 0L || eglDrawSurface == 0L) {
                return true
            }
            for (item in mReleaseSurface) {
                if (item == eglReadSurface) {
                    readRelease = true
                }
                if (item == eglDrawSurface) {
                    drawRelease = true
                }
            }
            if (readRelease && drawRelease) {
                return true
            }
            if (!readRelease) {
                readRelease = !OpenGLHook.isEglSurfaceAlive(eglReadSurface)
            }
            if (!drawRelease) {
                drawRelease = !OpenGLHook.isEglSurfaceAlive(eglDrawSurface)
            }
            if (readRelease) {
                mReleaseSurface.add(eglReadSurface)
            }
            if (drawRelease) {
                mReleaseSurface.add(eglDrawSurface)
            }
            return readRelease && drawRelease
        }
    }

    fun getAllItem(): List<OpenGLInfo> {
        val retList: MutableList<OpenGLInfo> = LinkedList()
        synchronized(mInfoList) {
            for (item in mInfoList) {
                retList.add(item)
            }
        }
        return retList
    }

    fun dumpGLToFile(filePath: String) {
        val targetFile = File(filePath)
        if (targetFile.exists()) {
            targetFile.delete()
        }
        try {
            targetFile.createNewFile()
        } catch (_: IOException) {
        }
        try {
            BufferedWriter(OutputStreamWriter(FileOutputStream(targetFile))).use { writer ->
                writer.write(dumpGLToString())
            }
        } catch (_: IOException) {
        }
    }

    @SuppressLint("DefaultLocale")
    private fun getResListString(resList: List<OpenGLDumpInfo>): String {
        val result = AutoWrapBuilder()
        for (report in resList) {
            val activityName = report.innerInfo.getActivityInfo()?.name
            result
                .append(String.format(" alloc count = %d", report.getAllocCount()))
                .append(String.format(" egl context is release = %s", report.innerInfo.isEglContextReleased()))
                .append(String.format(" egl surface is release = %s", report.innerInfo.isEglSurfaceRelease()))
                .append(String.format(" total size = %s", report.getTotalSize()))
                .append(String.format(" id = %s", report.getAllocIdList()))
                .append(String.format(" activity = %s", activityName))
                .append(String.format(" type = %s", report.innerInfo.getType()))
                .append(String.format(" eglContext = %s", report.innerInfo.getEglContextNativeHandle()))
                .append(String.format(" java stack = %s", report.innerInfo.getJavaStack()))
                .append(String.format(" native stack = %s", report.innerInfo.getNativeStack()))
                .append(if (report.innerInfo.getMemoryInfo() == null) "" else getMemoryInfoStr(report))
                .wrap()
        }
        return result.toString()
    }

    private fun getMemoryInfoStr(reportInfo: OpenGLDumpInfo): String {
        val memoryInfo = reportInfo.innerInfo.getMemoryInfo()
        return reportInfo.getParamsInfos() +
            "\n" +
            String.format(" memory java stack = %s", memoryInfo?.getJavaStack()) +
            "\n" +
            String.format(" memory native stack = %s", memoryInfo?.getNativeStack())
    }

    @SuppressLint("DefaultLocale")
    fun dumpGLToString(): String {
        val infoMap: MutableMap<Long, OpenGLDumpInfo> = HashMap()
        for (i in mInfoList.indices) {
            val info = mInfoList[i]
            val javaHash = info.getJavaStack().hashCode()
            val nativeHash = info.getNativeStack().hashCode()

            val memoryInfo = info.getMemoryInfo()
            val memoryJavaHash = memoryInfo?.getJavaStack()?.hashCode() ?: 0
            val memoryNativeHash = memoryInfo?.getNativeStack()?.hashCode() ?: 0

            val isEGLRelease = if (info.isEglContextReleased()) 1 else 0
            val infoHash = (
                javaHash +
                    nativeHash +
                    memoryNativeHash +
                    memoryJavaHash +
                    info.getEglContextNativeHandle() +
                    (info.getActivityInfo()?.hashCode() ?: 0) +
                    info.getThreadId().hashCode() +
                    isEGLRelease
                )

            val oldInfo = infoMap[infoHash]
            if (oldInfo == null) {
                infoMap[infoHash] = OpenGLDumpInfo(info)
            } else {
                // resource part
                val isSameType = info.getType() == oldInfo.innerInfo.getType()
                val isSameThread = info.getThreadId() == oldInfo.innerInfo.getThreadId()
                val isSameEglContext = info.getEglContextNativeHandle() == oldInfo.innerInfo.getEglContextNativeHandle()
                val isSameActivity = info.getActivityInfo() == oldInfo.innerInfo.getActivityInfo()
                val isSameEGLStatus = info.isEglContextReleased() == oldInfo.innerInfo.isEglContextReleased()

                if (isSameType && isSameThread && isSameEglContext && isSameActivity && isSameEGLStatus) {
                    oldInfo.incAllocRecord(info.getId())
                    if (oldInfo.innerInfo.getMemoryInfo() != null) {
                        oldInfo.appendParamsInfos(info.getMemoryInfo())
                        oldInfo.appendSize(info.getMemoryInfo()?.getSize() ?: 0)
                    }
                    infoMap[infoHash] = oldInfo
                }
            }
        }

        val textureList: MutableList<OpenGLDumpInfo> = ArrayList()
        val bufferList: MutableList<OpenGLDumpInfo> = ArrayList()
        val framebufferList: MutableList<OpenGLDumpInfo> = ArrayList()
        val renderbufferList: MutableList<OpenGLDumpInfo> = ArrayList()
        val eglContextList: MutableList<OpenGLDumpInfo> = ArrayList()

        for (reportInfo in infoMap.values) {
            if (reportInfo.innerInfo.getType() == OpenGLInfo.TYPE.TEXTURE) {
                textureList.add(reportInfo)
            }
            if (reportInfo.innerInfo.getType() == OpenGLInfo.TYPE.BUFFER) {
                bufferList.add(reportInfo)
            }
            if (reportInfo.innerInfo.getType() == OpenGLInfo.TYPE.FRAME_BUFFERS) {
                framebufferList.add(reportInfo)
            }
            if (reportInfo.innerInfo.getType() == OpenGLInfo.TYPE.RENDER_BUFFERS) {
                renderbufferList.add(reportInfo)
            }
            if (reportInfo.innerInfo.getType() == OpenGLInfo.TYPE.EGL_CONTEXT) {
                eglContextList.add(reportInfo)
            }
        }

        val comparator =
            Comparator<OpenGLDumpInfo> { o1, o2 ->
                when {
                    o2.getTotalSize() - o1.getTotalSize() > 0 -> 1
                    o2.getTotalSize() - o1.getTotalSize() == 0L -> 0
                    else -> -1
                }
            }

        Collections.sort(textureList, comparator)
        Collections.sort(bufferList, comparator)
        Collections.sort(framebufferList, comparator)
        Collections.sort(renderbufferList, comparator)
        Collections.sort(eglContextList, comparator)

        val builder = AutoWrapBuilder()
        builder
            .appendDotted()
            .appendWithSpace(String.format("textures Count = %d", textureList.size), 3)
            .appendWithSpace(String.format("buffer Count = %d", bufferList.size), 3)
            .appendWithSpace(String.format("framebuffer Count = %d", framebufferList.size), 3)
            .appendWithSpace(String.format("renderbuffer Count = %d", renderbufferList.size), 3)
            .appendWithSpace(String.format("egl context Count = %d", eglContextList.size), 3)
            .appendDotted()
            .appendWave()
            .appendWithSpace("texture part :", 3)
            .appendWave()
            .append(getResListString(textureList))
            .appendWave()
            .appendWithSpace("buffers part :", 3)
            .appendWave()
            .append(getResListString(bufferList))
            .appendWave()
            .appendWithSpace("renderbuffer part :", 3)
            .appendWave()
            .append(getResListString(renderbufferList))
            .appendWave()
            .appendWithSpace("egl context part :", 3)
            .appendWave()
            .append(getResListString(eglContextList))
            .wrap()

        return builder.toString()
    }

    interface Callback {
        fun gen(res: OpenGLInfo)

        fun delete(res: OpenGLInfo)
    }

    companion object {
        private const val TAG = "TraceHarbor.ResRecordManager"

        @JvmField
        val mInstance: ResRecordManager = ResRecordManager()

        @JvmStatic
        fun getInstance(): ResRecordManager = mInstance
    }
}

