package com.kernelflux.traceharbor.openglleak.hook

import android.opengl.EGL14
import android.opengl.GLES30.GL_PIXEL_UNPACK_BUFFER
import androidx.annotation.Keep
import com.kernelflux.traceharbor.openglleak.comm.FuncNameString
import com.kernelflux.traceharbor.openglleak.statistics.BindCenter
import com.kernelflux.traceharbor.openglleak.statistics.resource.MemoryInfo
import com.kernelflux.traceharbor.openglleak.statistics.resource.OpenGLInfo
import com.kernelflux.traceharbor.openglleak.statistics.resource.ResRecordManager
import com.kernelflux.traceharbor.openglleak.utils.ActivityRecorder
import com.kernelflux.traceharbor.openglleak.utils.JavaStacktrace
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.util.concurrent.atomic.AtomicInteger

@Keep
class OpenGLHook private constructor() {
    private var mResourceListener: ResourceListener? = null
    private var mErrorListener: ErrorListener? = null
    private var mBindListener: BindListener? = null
    private var mMemoryListener: MemoryListener? = null

    fun setResourceListener(listener: ResourceListener?) {
        mResourceListener = listener
    }

    fun setErrorListener(listener: ErrorListener?) {
        mErrorListener = listener
    }

    fun setBindListener(listener: BindListener?) {
        mBindListener = listener
    }

    fun setMemoryListener(listener: MemoryListener?) {
        mMemoryListener = listener
    }

    fun hook(targetFuncName: String, index: Int): Boolean {
        return when (targetFuncName) {
            FuncNameString.GL_GET_ERROR -> hookGlGetError(index)
            FuncNameString.GL_GEN_TEXTURES -> hookGlGenTextures(index)
            FuncNameString.GL_DELETE_TEXTURES -> hookGlDeleteTextures(index)
            FuncNameString.GL_GEN_BUFFERS -> hookGlGenBuffers(index)
            FuncNameString.GL_DELETE_BUFFERS -> hookGlDeleteBuffers(index)
            FuncNameString.GL_GEN_FRAMEBUFFERS -> hookGlGenFramebuffers(index)
            FuncNameString.GL_DELETE_FRAMEBUFFERS -> hookGlDeleteFramebuffers(index)
            FuncNameString.GL_GEN_RENDERBUFFERS -> hookGlGenRenderbuffers(index)
            FuncNameString.GL_DELETE_RENDERBUFFERS -> hookGlDeleteRenderbuffers(index)
            FuncNameString.GL_TEX_IMAGE_2D -> hookGlTexImage2D(index)
            FuncNameString.GL_TEX_IMAGE_3D -> hookGlTexImage3D(index)
            FuncNameString.GL_BIND_TEXTURE -> hookGlBindTexture(index)
            FuncNameString.GL_BIND_BUFFER -> hookGlBindBuffer(index)
            FuncNameString.GL_BIND_FRAMEBUFFER -> hookGlBindFramebuffer(index)
            FuncNameString.GL_BIND_RENDERBUFFER -> hookGlBindRenderbuffer(index)
            FuncNameString.GL_BUFFER_DATA -> hookGlBufferData(index)
            FuncNameString.GL_RENDER_BUFFER_STORAGE -> hookGlRenderbufferStorage(index)
            else -> false
        }
    }

    external fun init(): Boolean

    external fun setNativeStackDump(open: Boolean)

    external fun setJavaStackDump(open: Boolean)

    external fun updateCurrActivity(activityInfo: String)

    external fun getResidualQueueSize(): Int

    interface ErrorListener {
        fun onGlError(eid: Int)
    }

    interface BindListener {
        fun onGlBindTexture(target: Int, eglContextId: Long, id: Int)

        fun onGlBindBuffer(target: Int, eglContextId: Long, id: Int)

        fun onGlBindRenderbuffer(target: Int, eglContextId: Long, id: Int)

        fun onGlBindFramebuffer(target: Int, eglContextId: Long, id: Int)
    }

    interface MemoryListener {
        fun onGlTexImage2D(
            target: Int,
            level: Int,
            internalFormat: Int,
            width: Int,
            height: Int,
            border: Int,
            format: Int,
            type: Int,
            id: Int,
            eglContextId: Long,
            size: Long,
        )

        fun onGlTexImage3D(
            target: Int,
            level: Int,
            internalFormat: Int,
            width: Int,
            height: Int,
            depth: Int,
            border: Int,
            format: Int,
            type: Int,
            id: Int,
            eglContextId: Long,
            size: Long,
        )

        fun onGlBufferData(target: Int, usage: Int, id: Int, eglContextId: Long, size: Long)

        fun onGlRenderbufferStorage(
            target: Int,
            width: Int,
            height: Int,
            internalFormat: Int,
            id: Int,
            eglContextId: Long,
            size: Long,
        )
    }

    interface ResourceListener {
        fun onEglContextCreate(info: OpenGLInfo)

        fun onEglContextDestroy(info: OpenGLInfo)

        fun onGlGenTextures(info: OpenGLInfo)

        fun onGlDeleteTextures(info: OpenGLInfo)

        fun onGlGenBuffers(info: OpenGLInfo)

        fun onGlDeleteBuffers(info: OpenGLInfo)

        fun onGlGenFramebuffers(info: OpenGLInfo)

        fun onGlDeleteFramebuffers(info: OpenGLInfo)

        fun onGlGenRenderbuffers(info: OpenGLInfo)

        fun onGlDeleteRenderbuffers(info: OpenGLInfo)
    }

    companion object {
        private const val TAG = "MicroMsg.OpenGLHook"

        @JvmField
        val mInstance: OpenGLHook = OpenGLHook()

        init {
            System.loadLibrary("traceharbor-opengl-leak")
        }

        @JvmStatic
        fun getInstance(): OpenGLHook = mInstance

        @JvmStatic
        private external fun hookGlGenTextures(index: Int): Boolean

        @JvmStatic
        private external fun hookGlDeleteTextures(index: Int): Boolean

        @JvmStatic
        private external fun hookGlGenBuffers(index: Int): Boolean

        @JvmStatic
        private external fun hookGlDeleteBuffers(index: Int): Boolean

        @JvmStatic
        private external fun hookGlGenFramebuffers(index: Int): Boolean

        @JvmStatic
        private external fun hookGlDeleteFramebuffers(index: Int): Boolean

        @JvmStatic
        private external fun hookGlGenRenderbuffers(index: Int): Boolean

        @JvmStatic
        private external fun hookGlDeleteRenderbuffers(index: Int): Boolean

        @JvmStatic
        private external fun hookGlGetError(index: Int): Boolean

        @JvmStatic
        private external fun hookGlTexImage2D(index: Int): Boolean

        @JvmStatic
        private external fun hookGlTexImage3D(index: Int): Boolean

        @JvmStatic
        private external fun hookGlBindTexture(index: Int): Boolean

        @JvmStatic
        private external fun hookGlBindBuffer(index: Int): Boolean

        @JvmStatic
        private external fun hookGlBindFramebuffer(index: Int): Boolean

        @JvmStatic
        private external fun hookGlBindRenderbuffer(index: Int): Boolean

        @JvmStatic
        private external fun hookGlBufferData(index: Int): Boolean

        @JvmStatic
        private external fun hookGlRenderbufferStorage(index: Int): Boolean

        @JvmStatic
        external fun hookEgl(): Boolean

        @JvmStatic
        external fun dumpNativeStack(nativeStackPtr: Long): String

        @JvmStatic
        external fun dumpBriefNativeStack(nativeStackPtr: Long): String

        @JvmStatic
        external fun releaseNative(nativeStackPtr: Long)

        @JvmStatic
        external fun isEglContextAlive(eglContext: Long): Boolean

        @JvmStatic
        external fun isEglSurfaceAlive(eglSurface: Long): Boolean

        @JvmStatic
        fun onGlGenTextures(
            ids: IntArray,
            threadId: String,
            throwable: Int,
            nativeStackPtr: Long,
            eglContext: Long,
            eglDrawSurface: Long,
            eglReadSurface: Long,
            activityInfo: String,
        ) {
            if (ids.isNotEmpty()) {
                val counter = AtomicInteger(ids.size)
                val trace = JavaStacktrace.getBacktraceValue(throwable)
                for (id in ids) {
                    val openGLInfo =
                        OpenGLInfo(
                            OpenGLInfo.TYPE.TEXTURE,
                            id,
                            threadId,
                            eglContext,
                            eglDrawSurface,
                            eglReadSurface,
                            trace,
                            nativeStackPtr,
                            ActivityRecorder.revertActivityInfo(activityInfo),
                            counter,
                        )
                    ResRecordManager.getInstance().gen(openGLInfo)
                    getInstance().mResourceListener?.onGlGenTextures(openGLInfo)
                }
            }
        }

        @JvmStatic
        fun onGlDeleteTextures(ids: IntArray, threadId: String, eglContext: Long) {
            if (ids.isNotEmpty()) {
                for (id in ids) {
                    val openGLInfo = OpenGLInfo(OpenGLInfo.TYPE.TEXTURE, id, threadId, eglContext)
                    ResRecordManager.getInstance().delete(openGLInfo)
                    getInstance().mResourceListener?.onGlDeleteTextures(openGLInfo)
                }
            }
        }

        @JvmStatic
        fun onGlGenBuffers(
            ids: IntArray,
            threadId: String,
            throwable: Int,
            nativeStackPtr: Long,
            eglContext: Long,
            eglDrawSurface: Long,
            eglReadSurface: Long,
            activityInfo: String,
        ) {
            if (ids.isNotEmpty()) {
                val counter = AtomicInteger(ids.size)
                val trace = JavaStacktrace.getBacktraceValue(throwable)
                for (id in ids) {
                    val openGLInfo =
                        OpenGLInfo(
                            OpenGLInfo.TYPE.BUFFER,
                            id,
                            threadId,
                            eglContext,
                            eglDrawSurface,
                            eglReadSurface,
                            trace,
                            nativeStackPtr,
                            ActivityRecorder.revertActivityInfo(activityInfo),
                            counter,
                        )
                    ResRecordManager.getInstance().gen(openGLInfo)
                    getInstance().mResourceListener?.onGlGenBuffers(openGLInfo)
                }
            }
        }

        @JvmStatic
        fun onGlDeleteBuffers(ids: IntArray, threadId: String, eglContext: Long) {
            if (ids.isNotEmpty()) {
                for (id in ids) {
                    val openGLInfo = OpenGLInfo(OpenGLInfo.TYPE.BUFFER, id, threadId, eglContext)
                    ResRecordManager.getInstance().delete(openGLInfo)
                    getInstance().mResourceListener?.onGlDeleteBuffers(openGLInfo)
                }
            }
        }

        @JvmStatic
        fun onGlGenFramebuffers(
            ids: IntArray,
            threadId: String,
            throwable: Int,
            nativeStackPtr: Long,
            eglContext: Long,
            eglDrawSurface: Long,
            eglReadSurface: Long,
            activityInfo: String,
        ) {
            if (ids.isNotEmpty()) {
                val counter = AtomicInteger(ids.size)
                val trace = JavaStacktrace.getBacktraceValue(throwable)
                for (id in ids) {
                    val openGLInfo =
                        OpenGLInfo(
                            OpenGLInfo.TYPE.FRAME_BUFFERS,
                            id,
                            threadId,
                            eglContext,
                            eglDrawSurface,
                            eglReadSurface,
                            trace,
                            nativeStackPtr,
                            ActivityRecorder.revertActivityInfo(activityInfo),
                            counter,
                        )
                    ResRecordManager.getInstance().gen(openGLInfo)
                    getInstance().mResourceListener?.onGlGenFramebuffers(openGLInfo)
                }
            }
        }

        @JvmStatic
        fun onGlDeleteFramebuffers(ids: IntArray, threadId: String, eglContext: Long) {
            if (ids.isNotEmpty()) {
                for (id in ids) {
                    val openGLInfo = OpenGLInfo(OpenGLInfo.TYPE.FRAME_BUFFERS, id, threadId, eglContext)
                    ResRecordManager.getInstance().delete(openGLInfo)
                    getInstance().mResourceListener?.onGlDeleteFramebuffers(openGLInfo)
                }
            }
        }

        @JvmStatic
        fun onGlGenRenderbuffers(
            ids: IntArray,
            threadId: String,
            throwable: Int,
            nativeStackPtr: Long,
            eglContext: Long,
            eglDrawSurface: Long,
            eglReadSurface: Long,
            activityInfo: String,
        ) {
            if (ids.isNotEmpty()) {
                val counter = AtomicInteger(ids.size)
                val trace = JavaStacktrace.getBacktraceValue(throwable)
                for (id in ids) {
                    val openGLInfo =
                        OpenGLInfo(
                            OpenGLInfo.TYPE.RENDER_BUFFERS,
                            id,
                            threadId,
                            eglContext,
                            eglDrawSurface,
                            eglReadSurface,
                            trace,
                            nativeStackPtr,
                            ActivityRecorder.revertActivityInfo(activityInfo),
                            counter,
                        )
                    ResRecordManager.getInstance().gen(openGLInfo)
                    getInstance().mResourceListener?.onGlGenRenderbuffers(openGLInfo)
                }
            }
        }

        @JvmStatic
        fun onGlDeleteRenderbuffers(ids: IntArray, threadId: String, eglContext: Long) {
            if (ids.isNotEmpty()) {
                for (id in ids) {
                    val openGLInfo = OpenGLInfo(OpenGLInfo.TYPE.RENDER_BUFFERS, id, threadId, eglContext)
                    ResRecordManager.getInstance().delete(openGLInfo)
                    getInstance().mResourceListener?.onGlDeleteRenderbuffers(openGLInfo)
                }
            }
        }

        @JvmStatic
        fun onEglContextCreate(
            threadId: String,
            throwable: Int,
            nativeStackPtr: Long,
            eglContext: Long,
            shareContext: Long,
            activityInfo: String,
        ) {
            val counter = AtomicInteger(1)
            val trace = JavaStacktrace.getBacktraceValue(throwable)
            val openGLInfo =
                OpenGLInfo(
                    OpenGLInfo.TYPE.EGL_CONTEXT,
                    -1,
                    threadId,
                    eglContext,
                    0,
                    0,
                    trace,
                    nativeStackPtr,
                    ActivityRecorder.revertActivityInfo(activityInfo),
                    counter,
                )
            ResRecordManager.getInstance().gen(openGLInfo)
            ResRecordManager.getInstance().createContext(shareContext, eglContext)
            getInstance().mResourceListener?.onEglContextCreate(openGLInfo)
        }

        @JvmStatic
        fun onEglContextDestroy(threadId: String, eglContext: Long, ret: Int) {
            val openGLInfo = OpenGLInfo(OpenGLInfo.TYPE.EGL_CONTEXT, -1, threadId, eglContext)
            if (ret == EGL14.EGL_FALSE) {
                TraceHarborLog.e(
                    TAG,
                    "eglContextDestroy failed: thread=%s, context=%s, ret=%s, errno=%s",
                    threadId,
                    eglContext,
                    ret,
                    EGL14.eglGetError(),
                )
                return
            }
            ResRecordManager.getInstance().delete(openGLInfo)
            ResRecordManager.getInstance().destroyContext(eglContext)
            getInstance().mResourceListener?.onEglContextDestroy(openGLInfo)
        }

        @JvmStatic
        fun onGetError(eid: Int) {
            getInstance().mErrorListener?.onGlError(eid)
        }

        @JvmStatic
        fun onGlBindTexture(target: Int, id: Int, eglContext: Long) {
            var info: OpenGLInfo? = null
            if (id != 0) {
                info = ResRecordManager.getInstance().findOpenGLInfo(OpenGLInfo.TYPE.TEXTURE, eglContext, id)
            }
            BindCenter.getInstance().glBindResource(OpenGLInfo.TYPE.TEXTURE, target, eglContext, info)
            getInstance().mBindListener?.onGlBindTexture(target, eglContext, id)
        }

        @JvmStatic
        fun onGlBindBuffer(target: Int, id: Int, eglContext: Long) {
            var info: OpenGLInfo? = null
            if (id != 0) {
                info = ResRecordManager.getInstance().findOpenGLInfo(OpenGLInfo.TYPE.BUFFER, eglContext, id)
            }
            BindCenter.getInstance().glBindResource(OpenGLInfo.TYPE.BUFFER, target, eglContext, info)
            getInstance().mBindListener?.onGlBindBuffer(target, eglContext, id)
        }

        @JvmStatic
        fun onGlBindFramebuffer(target: Int, id: Int, eglContext: Long) {
            var info: OpenGLInfo? = null
            if (id != 0) {
                info = ResRecordManager.getInstance().findOpenGLInfo(OpenGLInfo.TYPE.FRAME_BUFFERS, eglContext, id)
            }
            BindCenter.getInstance().glBindResource(OpenGLInfo.TYPE.FRAME_BUFFERS, target, eglContext, info)
            getInstance().mBindListener?.onGlBindFramebuffer(target, eglContext, id)
        }

        @JvmStatic
        fun onGlBindRenderbuffer(target: Int, id: Int, eglContext: Long) {
            var info: OpenGLInfo? = null
            if (id != 0) {
                info = ResRecordManager.getInstance().findOpenGLInfo(OpenGLInfo.TYPE.RENDER_BUFFERS, eglContext, id)
            }
            BindCenter.getInstance().glBindResource(OpenGLInfo.TYPE.RENDER_BUFFERS, target, eglContext, info)
            getInstance().mBindListener?.onGlBindRenderbuffer(target, eglContext, id)
        }

        @JvmStatic
        fun onGlTexImage2D(
            target: Int,
            level: Int,
            internalFormat: Int,
            width: Int,
            height: Int,
            border: Int,
            format: Int,
            type: Int,
            size: Long,
            throwable: Int,
            nativeStack: Long,
            eglContext: Long,
        ) {
            val openGLInfo =
                BindCenter
                    .getInstance()
                    .findCurrentResourceIdByTarget(OpenGLInfo.TYPE.TEXTURE, eglContext, target)
            if (openGLInfo == null) {
                TraceHarborLog.e(
                    TAG,
                    "onGlTexImage2D: getCurrentResourceIdByTarget openGLID == null, maybe didn't call glBindTextures()",
                )
                return
            }

            val javatrace = JavaStacktrace.getBacktraceValue(throwable)
            var memoryInfo = openGLInfo.getMemoryInfo()
            if (memoryInfo == null) {
                memoryInfo = MemoryInfo(OpenGLInfo.TYPE.TEXTURE)
            }
            memoryInfo.setTexturesInfo(
                target,
                level,
                internalFormat,
                width,
                height,
                0,
                border,
                format,
                type,
                openGLInfo.getId(),
                openGLInfo.getEglContextNativeHandle(),
                size,
                javatrace,
                nativeStack,
            )
            openGLInfo.setMemoryInfo(memoryInfo)
            getInstance().mMemoryListener?.onGlTexImage2D(
                target,
                level,
                internalFormat,
                width,
                height,
                border,
                format,
                type,
                openGLInfo.getId(),
                openGLInfo.getEglContextNativeHandle(),
                size,
            )
        }

        @JvmStatic
        fun onGlTexImage3D(
            target: Int,
            level: Int,
            internalFormat: Int,
            width: Int,
            height: Int,
            depth: Int,
            border: Int,
            format: Int,
            type: Int,
            size: Long,
            throwable: Int,
            nativeStack: Long,
            eglContext: Long,
        ) {
            val openGLInfo =
                BindCenter
                    .getInstance()
                    .findCurrentResourceIdByTarget(OpenGLInfo.TYPE.TEXTURE, eglContext, target)
            if (openGLInfo == null) {
                TraceHarborLog.e(
                    TAG,
                    "onGlTexImage3D: getCurrentResourceIdByTarget result == null, maybe didn't call glBindTextures()",
                )
                return
            }

            val javatrace = JavaStacktrace.getBacktraceValue(throwable)
            var memoryInfo = openGLInfo.getMemoryInfo()
            if (memoryInfo == null) {
                memoryInfo = MemoryInfo(OpenGLInfo.TYPE.TEXTURE)
            }
            memoryInfo.setTexturesInfo(
                target,
                level,
                internalFormat,
                width,
                height,
                depth,
                border,
                format,
                type,
                openGLInfo.getId(),
                openGLInfo.getEglContextNativeHandle(),
                size,
                javatrace,
                nativeStack,
            )
            openGLInfo.setMemoryInfo(memoryInfo)
            getInstance().mMemoryListener?.onGlTexImage3D(
                target,
                level,
                internalFormat,
                width,
                height,
                depth,
                border,
                format,
                type,
                openGLInfo.getId(),
                openGLInfo.getEglContextNativeHandle(),
                size,
            )
        }

        @JvmStatic
        fun onGlBufferData(
            target: Int,
            usage: Int,
            size: Long,
            throwable: Int,
            nativeStack: Long,
            eglContext: Long,
        ) {
            val openGLInfo =
                BindCenter
                    .getInstance()
                    .findCurrentResourceIdByTarget(OpenGLInfo.TYPE.BUFFER, eglContext, target)
            if (openGLInfo == null) {
                TraceHarborLog.e(
                    TAG,
                    "onGlBufferData: getCurrentResourceIdByTarget result == null, maybe didn't call glBindBuffer()",
                )
                return
            }

            val actualSize = if (target == GL_PIXEL_UNPACK_BUFFER) size * 2 else size
            val javatrace = JavaStacktrace.getBacktraceValue(throwable)
            var memoryInfo = openGLInfo.getMemoryInfo()
            if (memoryInfo == null) {
                memoryInfo = MemoryInfo(OpenGLInfo.TYPE.BUFFER)
            }
            memoryInfo.setBufferInfo(
                target,
                usage,
                openGLInfo.getId(),
                openGLInfo.getEglContextNativeHandle(),
                actualSize,
                javatrace,
                nativeStack,
            )
            openGLInfo.setMemoryInfo(memoryInfo)
            getInstance().mMemoryListener?.onGlBufferData(
                target,
                usage,
                openGLInfo.getId(),
                openGLInfo.getEglContextNativeHandle(),
                size,
            )
        }

        @JvmStatic
        fun onGlRenderbufferStorage(
            target: Int,
            internalformat: Int,
            width: Int,
            height: Int,
            size: Long,
            key: Int,
            nativeStack: Long,
            eglContext: Long,
        ) {
            val openGLInfo =
                BindCenter
                    .getInstance()
                    .findCurrentResourceIdByTarget(OpenGLInfo.TYPE.RENDER_BUFFERS, eglContext, target)
            if (openGLInfo == null) {
                TraceHarborLog.e(
                    TAG,
                    "onGlRenderbufferStorage: getCurrentResourceIdByTarget result == null, maybe didn't call glBindRenderbuffer()",
                )
                return
            }

            val javatrace = JavaStacktrace.getBacktraceValue(key)
            var memoryInfo = openGLInfo.getMemoryInfo()
            if (memoryInfo == null) {
                memoryInfo = MemoryInfo(OpenGLInfo.TYPE.RENDER_BUFFERS)
            }
            memoryInfo.setRenderbufferInfo(
                target,
                width,
                height,
                internalformat,
                openGLInfo.getId(),
                openGLInfo.getEglContextNativeHandle(),
                size,
                javatrace,
                nativeStack,
            )
            openGLInfo.setMemoryInfo(memoryInfo)
            getInstance().mMemoryListener?.onGlRenderbufferStorage(
                target,
                width,
                height,
                internalformat,
                openGLInfo.getId(),
                openGLInfo.getEglContextNativeHandle(),
                size,
            )
        }

        @JvmStatic
        fun getThrowable(): Int = JavaStacktrace.getBacktraceKey()
    }
}

