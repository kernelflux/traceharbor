package com.kernelflux.traceharbor.openglleak.utils

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Build
import androidx.annotation.RequiresApi

object EGLHelper {
    private var mEGLDisplay: EGLDisplay? = null
    private var mEglConfig: EGLConfig? = null
    private var mEglContext: EGLContext? = null
    private var mEglSurface: EGLSurface? = null

    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    fun initOpenGL(): EGLContext? {
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (mEGLDisplay === EGL14.EGL_NO_DISPLAY) {
            return null
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            return null
        }

        val eglConfigAttribList = intArrayOf(
            EGL14.EGL_RED_SIZE,
            8,
            EGL14.EGL_GREEN_SIZE,
            8,
            EGL14.EGL_BLUE_SIZE,
            8,
            EGL14.EGL_NONE,
        )
        val numEglConfigs = IntArray(1)
        val eglConfigs = arrayOfNulls<EGLConfig>(1)
        if (!EGL14.eglChooseConfig(mEGLDisplay, eglConfigAttribList, 0, eglConfigs, 0, eglConfigs.size, numEglConfigs, 0)) {
            return null
        }
        mEglConfig = eglConfigs[0]

        val eglContextAttribList = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION,
            2,
            EGL14.EGL_NONE,
        )

        mEglContext = EGL14.eglCreateContext(mEGLDisplay, mEglConfig, EGL14.EGL_NO_CONTEXT, eglContextAttribList, 0)
        if (mEglContext === EGL14.EGL_NO_CONTEXT) {
            return null
        }

        val surfaceAttribList = intArrayOf(
            EGL14.EGL_WIDTH,
            64,
            EGL14.EGL_HEIGHT,
            64,
            EGL14.EGL_NONE,
        )

        // Java 线程不进行实际绘制，因此创建 PbufferSurface 而非 WindowSurface
        mEglSurface = EGL14.eglCreatePbufferSurface(mEGLDisplay, mEglConfig, surfaceAttribList, 0)
        if (mEglSurface === EGL14.EGL_NO_SURFACE) {
            return null
        }

        if (!EGL14.eglMakeCurrent(mEGLDisplay, mEglSurface, mEglSurface, mEglContext)) {
            return null
        }

        GLES20.glFlush()
        return mEglContext
    }

    @JvmStatic
    fun initOpenGLSharedContext(shareContext: EGLContext?): EGLContext? {
        if (shareContext == null) {
            return null
        }

        val eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay === EGL14.EGL_NO_DISPLAY) {
            return null
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            return null
        }

        val eglConfigAttribList = intArrayOf(
            EGL14.EGL_RED_SIZE,
            8,
            EGL14.EGL_GREEN_SIZE,
            8,
            EGL14.EGL_BLUE_SIZE,
            8,
            EGL14.EGL_NONE,
        )
        val numEglConfigs = IntArray(1)
        val eglConfigs = arrayOfNulls<EGLConfig>(1)
        if (!EGL14.eglChooseConfig(eglDisplay, eglConfigAttribList, 0, eglConfigs, 0, eglConfigs.size, numEglConfigs, 0)) {
            return null
        }

        val eglContextAttribList = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION,
            2,
            EGL14.EGL_NONE,
        )

        val eglContext = EGL14.eglCreateContext(eglDisplay, eglConfigs[0], shareContext, eglContextAttribList, 0)
        if (eglContext === EGL14.EGL_NO_CONTEXT) {
            return null
        }

        val surfaceAttribList = intArrayOf(
            EGL14.EGL_WIDTH,
            64,
            EGL14.EGL_HEIGHT,
            64,
            EGL14.EGL_NONE,
        )

        // Java 线程不进行实际绘制，因此创建 PbufferSurface 而非 WindowSurface
        mEglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfigs[0], surfaceAttribList, 0)
        if (mEglSurface === EGL14.EGL_NO_SURFACE) {
            return null
        }

        if (!EGL14.eglMakeCurrent(eglDisplay, mEglSurface, mEglSurface, eglContext)) {
            return null
        }

        GLES20.glFlush()
        return eglContext
    }
}

