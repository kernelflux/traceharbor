package com.kernelflux.traceharbor.openglleak.statistics

import android.opengl.GLES20.GL_ARRAY_BUFFER
import android.opengl.GLES20.GL_ELEMENT_ARRAY_BUFFER
import android.opengl.GLES20.GL_RENDERBUFFER
import android.opengl.GLES20.GL_TEXTURE_2D
import android.opengl.GLES20.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y
import android.opengl.GLES20.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z
import android.opengl.GLES20.GL_TEXTURE_CUBE_MAP_POSITIVE_X
import android.opengl.GLES20.GL_TEXTURE_CUBE_MAP_POSITIVE_Y
import android.opengl.GLES20.GL_TEXTURE_CUBE_MAP_POSITIVE_Z
import android.opengl.GLES30.GL_COPY_READ_BUFFER
import android.opengl.GLES30.GL_COPY_WRITE_BUFFER
import android.opengl.GLES30.GL_PIXEL_PACK_BUFFER
import android.opengl.GLES30.GL_PIXEL_UNPACK_BUFFER
import android.opengl.GLES30.GL_TEXTURE_2D_ARRAY
import android.opengl.GLES30.GL_TEXTURE_3D
import android.opengl.GLES30.GL_TRANSFORM_FEEDBACK_BUFFER
import android.opengl.GLES30.GL_UNIFORM_BUFFER
import android.opengl.GLES31.GL_ATOMIC_COUNTER_BUFFER
import android.opengl.GLES31.GL_DISPATCH_INDIRECT_BUFFER
import android.opengl.GLES31.GL_DRAW_INDIRECT_BUFFER
import android.opengl.GLES31.GL_SHADER_STORAGE_BUFFER
import android.opengl.GLES32.GL_TEXTURE_BUFFER
import com.kernelflux.traceharbor.openglleak.statistics.resource.OpenGLInfo
import javax.microedition.khronos.opengles.GL11ExtensionPack.GL_TEXTURE_CUBE_MAP
import javax.microedition.khronos.opengles.GL11ExtensionPack.GL_TEXTURE_CUBE_MAP_NEGATIVE_X

class BindMap private constructor() {
    private val bindTextureMap: MutableMap<Long, MutableMap<Int, OpenGLInfo?>> = HashMap()
    private val bindBufferMap: MutableMap<Long, MutableMap<Int, OpenGLInfo?>> = HashMap()

    // glRenderbufferStorage target only support GL_RENDERBUFFER
    private val bindRenderbufferMap: MutableMap<Long, OpenGLInfo?> = HashMap()

    private fun getBindMapInfo(
        bindMap: MutableMap<Long, MutableMap<Int, OpenGLInfo?>>,
        eglContextNativeHandle: Long,
        target: Int,
    ): OpenGLInfo? {
        synchronized(bindMap) {
            var subTextureMap = bindMap[eglContextNativeHandle]
            if (subTextureMap == null) {
                subTextureMap = HashMap()
                bindMap[eglContextNativeHandle] = subTextureMap
            }
            return subTextureMap[target]
        }
    }

    private fun putInBindMap(
        bindMap: MutableMap<Long, MutableMap<Int, OpenGLInfo?>>,
        target: Int,
        eglContextNativeHandle: Long,
        openGLInfo: OpenGLInfo?,
    ) {
        synchronized(bindMap) {
            var subTextureMap = bindMap[eglContextNativeHandle]
            if (subTextureMap == null) {
                subTextureMap = HashMap()
                bindMap[eglContextNativeHandle] = subTextureMap
            }
            subTextureMap[target] = openGLInfo
        }
    }

    private fun isSupportTarget(type: OpenGLInfo.TYPE, target: Int): Boolean {
        return when (type) {
            OpenGLInfo.TYPE.TEXTURE -> isSupportTargetOfTexture(target)
            OpenGLInfo.TYPE.BUFFER -> isSupportTargetOfBuffer(target)
            OpenGLInfo.TYPE.RENDER_BUFFERS -> isSupportTargetOfRenderbuffer(target)
            else -> false
        }
    }

    private fun isSupportTargetOfTexture(target: Int): Boolean {
        return target == GL_TEXTURE_2D || target == GL_TEXTURE_3D || target == GL_TEXTURE_2D_ARRAY || target == GL_TEXTURE_CUBE_MAP
    }

    private fun isSupportTargetOfBuffer(target: Int): Boolean {
        val targetOnUnder31 =
            target == GL_ARRAY_BUFFER ||
                target == GL_COPY_READ_BUFFER ||
                target == GL_COPY_WRITE_BUFFER ||
                target == GL_ELEMENT_ARRAY_BUFFER ||
                target == GL_PIXEL_PACK_BUFFER ||
                target == GL_PIXEL_UNPACK_BUFFER ||
                target == GL_TRANSFORM_FEEDBACK_BUFFER ||
                target == GL_UNIFORM_BUFFER
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            val targetOn31 =
                target == GL_ATOMIC_COUNTER_BUFFER ||
                    target == GL_DISPATCH_INDIRECT_BUFFER ||
                    target == GL_DRAW_INDIRECT_BUFFER ||
                    target == GL_SHADER_STORAGE_BUFFER
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val targetOn32 = target == GL_TEXTURE_BUFFER
                targetOnUnder31 || targetOn31 || targetOn32
            } else {
                targetOnUnder31 || targetOn31
            }
        }
        return targetOnUnder31
    }

    private fun isSupportTargetOfRenderbuffer(target: Int): Boolean {
        return target == GL_RENDERBUFFER
    }

    fun getBindInfo(type: OpenGLInfo.TYPE, eglContextId: Long, target: Int): OpenGLInfo? {
        return when (type) {
            OpenGLInfo.TYPE.BUFFER -> getBindMapInfo(bindBufferMap, eglContextId, target)
            OpenGLInfo.TYPE.TEXTURE -> {
                var actualTarget = target
                if (is2DCubeMapTarget(target)) {
                    actualTarget = GL_TEXTURE_CUBE_MAP
                }
                getBindMapInfo(bindTextureMap, eglContextId, actualTarget)
            }

            OpenGLInfo.TYPE.RENDER_BUFFERS -> bindRenderbufferMap[eglContextId]
            else -> null
        }
    }

    private fun is2DCubeMapTarget(target: Int): Boolean {
        return target == GL_TEXTURE_CUBE_MAP_POSITIVE_X ||
            target == GL_TEXTURE_CUBE_MAP_NEGATIVE_X ||
            target == GL_TEXTURE_CUBE_MAP_POSITIVE_Y ||
            target == GL_TEXTURE_CUBE_MAP_NEGATIVE_Y ||
            target == GL_TEXTURE_CUBE_MAP_POSITIVE_Z ||
            target == GL_TEXTURE_CUBE_MAP_NEGATIVE_Z
    }

    fun putBindInfo(type: OpenGLInfo.TYPE, target: Int, eglContextId: Long, info: OpenGLInfo?) {
        if (!isSupportTarget(type, target)) {
            return
        }
        when (type) {
            OpenGLInfo.TYPE.BUFFER -> putInBindMap(bindBufferMap, target, eglContextId, info)
            OpenGLInfo.TYPE.TEXTURE -> putInBindMap(bindTextureMap, target, eglContextId, info)
            OpenGLInfo.TYPE.RENDER_BUFFERS -> bindRenderbufferMap[eglContextId] = info
            else -> Unit
        }
    }

    companion object {
        @JvmField
        val mInstance: BindMap = BindMap()

        @JvmStatic
        fun getInstance(): BindMap = mInstance
    }
}

