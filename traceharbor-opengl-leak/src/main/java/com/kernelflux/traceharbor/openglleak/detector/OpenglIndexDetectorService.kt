package com.kernelflux.traceharbor.openglleak.detector

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.kernelflux.traceharbor.openglleak.comm.FuncNameString
import com.kernelflux.traceharbor.openglleak.hook.OpenGLHook
import com.kernelflux.traceharbor.openglleak.utils.EGLHelper
import com.kernelflux.traceharbor.util.TraceHarborLog

class OpenglIndexDetectorService : Service() {
    private val stub: IOpenglIndexDetector.Stub =
        object : IOpenglIndexDetector.Stub() {
            override fun seekIndex(): MutableMap<String, Int>? {
                return seekOpenglFuncIndex()
            }

            override fun destory() {
                stopSelf()
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(0)
            }
        }

    override fun onBind(intent: Intent): IBinder {
        return stub
    }

    private fun seekOpenglFuncIndex(): MutableMap<String, Int>? {
        // 初始化 egl 环境，目的为了初始化 gl 表
        EGLHelper.initOpenGL()
        OpenGLHook.getInstance().init()
        TraceHarborLog.i(TAG, "init env succ")

        val glGenTexturesIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_GEN_TEXTURES)
        TraceHarborLog.i(TAG, "glGenTextures index:$glGenTexturesIndex")
        val glDeleteTexturesIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_DELETE_TEXTURES)
        TraceHarborLog.i(TAG, "glDeleteTextures index:$glDeleteTexturesIndex")

        val glGenBuffersIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_GEN_BUFFERS)
        TraceHarborLog.i(TAG, "glGenBuffers index:$glGenBuffersIndex")
        val glDeleteBuffersIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_DELETE_BUFFERS)
        TraceHarborLog.i(TAG, "glDeleteBuffers index:$glDeleteBuffersIndex")

        val glGenFramebuffersIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_GEN_FRAMEBUFFERS)
        TraceHarborLog.i(TAG, "glGenFramebuffers index:$glGenFramebuffersIndex")
        val glDeleteFramebuffersIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_DELETE_FRAMEBUFFERS)
        TraceHarborLog.i(TAG, "glDeleteFramebuffers index:$glDeleteFramebuffersIndex")

        val glGenRenderbuffersIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_GEN_RENDERBUFFERS)
        TraceHarborLog.i(TAG, "glGenRenderbuffers index:$glGenRenderbuffersIndex")
        val glDeleteRenderbuffersIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_DELETE_RENDERBUFFERS)
        TraceHarborLog.i(TAG, "glDeleteRenderbuffers index:$glDeleteRenderbuffersIndex")

        val glTexImage2DIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_TEX_IMAGE_2D)
        TraceHarborLog.i(TAG, "glTexImage2DIndex index:$glTexImage2DIndex")
        val glTexImage3DIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_TEX_IMAGE_3D)
        TraceHarborLog.i(TAG, "glTexImage3DIndex index:$glTexImage3DIndex")

        val glBindTextureIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_BIND_TEXTURE)
        TraceHarborLog.i(TAG, "glBindTextureIndex index:$glBindTextureIndex")
        val glBindBufferIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_BIND_BUFFER)
        TraceHarborLog.i(TAG, "glBindBufferIndex index:$glBindBufferIndex")
        val glBindFramebufferIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_BIND_FRAMEBUFFER)
        TraceHarborLog.i(TAG, "glBindFramebufferIndex index:$glBindFramebufferIndex")
        val glBindRenderbufferIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_BIND_RENDERBUFFER)
        TraceHarborLog.i(TAG, "glBindRenderbufferIndex index:$glBindRenderbufferIndex")

        val glBufferDataIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_BUFFER_DATA)
        TraceHarborLog.i(TAG, "glBufferData index:$glBufferDataIndex")
        val glRenderbufferStorageIndex = FuncSeeker.getFuncIndex(FuncNameString.GL_RENDER_BUFFER_STORAGE)
        TraceHarborLog.i(TAG, "glRenderbufferStorage index:$glRenderbufferStorageIndex")

        if (
            (
                glGenTexturesIndex * glDeleteTexturesIndex * glGenBuffersIndex * glDeleteBuffersIndex * glGenFramebuffersIndex *
                    glDeleteFramebuffersIndex * glGenRenderbuffersIndex * glDeleteRenderbuffersIndex * glTexImage2DIndex * glTexImage3DIndex *
                    glBindTextureIndex * glBindBufferIndex * glBindFramebufferIndex * glBindRenderbufferIndex *
                    glRenderbufferStorageIndex * glBufferDataIndex
                ) == 0
        ) {
            TraceHarborLog.e(TAG, "seek func index fail!")
            return null
        }

        val out = HashMap<String, Int>()
        out[FuncNameString.GL_GEN_TEXTURES] = glGenTexturesIndex
        out[FuncNameString.GL_DELETE_TEXTURES] = glDeleteTexturesIndex
        out[FuncNameString.GL_GEN_BUFFERS] = glGenBuffersIndex
        out[FuncNameString.GL_DELETE_BUFFERS] = glDeleteBuffersIndex
        out[FuncNameString.GL_GEN_FRAMEBUFFERS] = glGenFramebuffersIndex
        out[FuncNameString.GL_DELETE_FRAMEBUFFERS] = glDeleteFramebuffersIndex
        out[FuncNameString.GL_GEN_RENDERBUFFERS] = glGenRenderbuffersIndex
        out[FuncNameString.GL_DELETE_RENDERBUFFERS] = glDeleteRenderbuffersIndex
        out[FuncNameString.GL_TEX_IMAGE_2D] = glTexImage2DIndex
        out[FuncNameString.GL_TEX_IMAGE_3D] = glTexImage3DIndex
        out[FuncNameString.GL_BIND_TEXTURE] = glBindTextureIndex
        out[FuncNameString.GL_BIND_BUFFER] = glBindBufferIndex
        out[FuncNameString.GL_BIND_FRAMEBUFFER] = glBindFramebufferIndex
        out[FuncNameString.GL_BIND_RENDERBUFFER] = glBindRenderbufferIndex
        out[FuncNameString.GL_BUFFER_DATA] = glBufferDataIndex
        out[FuncNameString.GL_RENDER_BUFFER_STORAGE] = glRenderbufferStorageIndex

        TraceHarborLog.i(TAG, "seek func index succ!")
        return out
    }

    companion object {
        private const val TAG = "traceharbor.OpenglIndexDetectorService"
    }
}

