package com.kernelflux.traceharbor.openglleak.statistics.resource

import android.opengl.GLES20.GL_TEXTURE_2D
import android.opengl.GLES20.GL_TEXTURE_CUBE_MAP_NEGATIVE_X
import android.opengl.GLES20.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y
import android.opengl.GLES20.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z
import android.opengl.GLES20.GL_TEXTURE_CUBE_MAP_POSITIVE_X
import android.opengl.GLES20.GL_TEXTURE_CUBE_MAP_POSITIVE_Y
import android.opengl.GLES20.GL_TEXTURE_CUBE_MAP_POSITIVE_Z
import android.opengl.GLES30.GL_TEXTURE_2D_ARRAY
import android.opengl.GLES30.GL_TEXTURE_3D
import com.kernelflux.traceharbor.openglleak.hook.OpenGLHook
import com.kernelflux.traceharbor.openglleak.utils.JavaStacktrace
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.util.Arrays

class MemoryInfo(
    val resType: OpenGLInfo.TYPE,
) {
    var target: Int = 0
    var internalFormat: Int = 0
    var width: Int = 0
    var height: Int = 0
    var id: Int = 0
    var eglContextId: Long = 0
    var usage: Int = 0

    private var mJavaTrace: JavaStacktrace.Trace? = null
    private var mNativeStackPtr: Long = 0

    // use for buffer & renderbuffer
    private var mSize: Long = 0

    // only use for textures
    private var mFaces: Array<FaceInfo?>? = null

    init {
        if (resType == OpenGLInfo.TYPE.TEXTURE) {
            val cubeMapFaceCount = 6
            mFaces = arrayOfNulls(cubeMapFaceCount)
        }
    }

    private fun getFaceId(target: Int): Int {
        return when (target) {
            GL_TEXTURE_2D, GL_TEXTURE_3D, GL_TEXTURE_2D_ARRAY, GL_TEXTURE_CUBE_MAP_POSITIVE_X -> 0
            GL_TEXTURE_CUBE_MAP_NEGATIVE_X -> 1
            GL_TEXTURE_CUBE_MAP_POSITIVE_Y -> 2
            GL_TEXTURE_CUBE_MAP_NEGATIVE_Y -> 3
            GL_TEXTURE_CUBE_MAP_POSITIVE_Z -> 4
            GL_TEXTURE_CUBE_MAP_NEGATIVE_Z -> 5
            else -> -1
        }
    }

    fun releaseNativeStackPtr() {
        mNativeStackPtr = 0
    }

    fun setTexturesInfo(
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
        javatrace: JavaStacktrace.Trace?,
        nativeStackPtr: Long,
    ) {
        val faceId = getFaceId(target)
        if (faceId == -1) {
            TraceHarborLog.e("MicroMsg.OpenGLHook", "setTexturesInfo faceId = -1, target = $target")
            return
        }

        if (this.mNativeStackPtr != 0L) {
            OpenGLHook.releaseNative(this.mNativeStackPtr)
        }

        if (this.mJavaTrace != null) {
            javatrace?.reduceReference()
        }

        this.mJavaTrace = javatrace
        this.mNativeStackPtr = nativeStackPtr

        val currentFaces = mFaces ?: return
        var faceInfo = currentFaces[faceId]
        if (faceInfo == null) {
            faceInfo = FaceInfo()
        }

        faceInfo.target = target
        faceInfo.id = id
        faceInfo.eglContextNativeHandle = eglContextId
        faceInfo.level = level
        faceInfo.internalFormat = internalFormat
        faceInfo.width = width
        faceInfo.height = height
        faceInfo.depth = depth
        faceInfo.border = border
        faceInfo.format = format
        faceInfo.type = type
        faceInfo.size = size

        currentFaces[faceId] = faceInfo
    }

    fun getJavaStack(): String = mJavaTrace?.content ?: ""

    fun getNativeStack(): String = if (mNativeStackPtr != 0L) OpenGLHook.dumpNativeStack(mNativeStackPtr) else ""

    fun getNativeStackPtr(): Long = mNativeStackPtr

    fun getFaces(): Array<FaceInfo?>? = mFaces

    fun getSize(): Long {
        var actualSize = 0L
        if (resType == OpenGLInfo.TYPE.TEXTURE) {
            val textureFaces = mFaces
            if (textureFaces != null) {
                for (faceInfo in textureFaces) {
                    if (faceInfo != null) {
                        actualSize += faceInfo.size
                    }
                }
            }
        } else {
            actualSize = mSize
        }
        return actualSize
    }

    fun setBufferInfo(
        target: Int,
        usage: Int,
        id: Int,
        eglContextId: Long,
        size: Long,
        backtrace: JavaStacktrace.Trace?,
        nativeStackPtr: Long,
    ) {
        this.target = target
        this.usage = usage
        this.id = id
        this.eglContextId = eglContextId
        this.mSize = size

        if (this.mJavaTrace != null) {
            this.mJavaTrace?.reduceReference()
        }

        if (this.mNativeStackPtr != 0L) {
            OpenGLHook.releaseNative(this.mNativeStackPtr)
        }

        this.mJavaTrace = backtrace
        this.mNativeStackPtr = nativeStackPtr
    }

    fun setRenderbufferInfo(
        target: Int,
        width: Int,
        height: Int,
        internalFormat: Int,
        id: Int,
        eglContextId: Long,
        size: Long,
        backtrace: JavaStacktrace.Trace?,
        nativeStackPtr: Long,
    ) {
        this.target = target
        this.width = width
        this.height = height
        this.internalFormat = internalFormat
        this.id = id
        this.eglContextId = eglContextId
        this.mSize = size

        if (this.mJavaTrace != null) {
            this.mJavaTrace?.reduceReference()
        }

        if (this.mNativeStackPtr != 0L) {
            OpenGLHook.releaseNative(this.mNativeStackPtr)
        }

        this.mJavaTrace = backtrace
        this.mNativeStackPtr = nativeStackPtr
    }

    fun releaseJavaStacktrace() {
        if (mJavaTrace != null) {
            mJavaTrace?.reduceReference()
            mJavaTrace = null
        }
    }

    override fun toString(): String {
        return "MemoryInfo{" +
            "target=$target" +
            ", internalFormat=$internalFormat" +
            ", width=$width" +
            ", height=$height" +
            ", id=$id" +
            ", eglContextId=$eglContextId" +
            ", usage=$usage" +
            ", javaStack='" + getJavaStack() + '\'' +
            ", nativeStack='" + getNativeStack() + '\'' +
            ", resType=$resType" +
            ", size=" + getSize() +
            ", faces=" + Arrays.toString(mFaces) +
            '}'
    }
}

