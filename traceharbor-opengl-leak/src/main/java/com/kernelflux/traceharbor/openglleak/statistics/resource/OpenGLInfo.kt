package com.kernelflux.traceharbor.openglleak.statistics.resource

import com.kernelflux.traceharbor.openglleak.utils.ActivityRecorder
import com.kernelflux.traceharbor.openglleak.utils.JavaStacktrace
import java.util.Objects
import java.util.concurrent.atomic.AtomicInteger

open class OpenGLInfo {
    enum class TYPE {
        TEXTURE,
        BUFFER,
        FRAME_BUFFERS,
        RENDER_BUFFERS,
        EGL_CONTEXT,
    }

    private var mThreadId: String = ""
    private val mId: Int
    private var mNativeStack: String = ""
    private var mJavaTrace: JavaStacktrace.Trace? = null
    private var mNativeStackPtr: Long = 0L
    private val mType: TYPE
    private var mMemoryInfo: MemoryInfo? = null
    private val mEglContext: Long
    private val mEglDrawSurface: Long
    private val mEglReadSurface: Long
    private var mActivityInfo: ActivityRecorder.ActivityInfo? = null
    private var mCounter: AtomicInteger? = null

    constructor(clone: OpenGLInfo) {
        mThreadId = clone.mThreadId
        mId = clone.mId
        mEglContext = clone.mEglContext
        mJavaTrace = clone.mJavaTrace
        mNativeStack = clone.mNativeStack
        mNativeStackPtr = clone.mNativeStackPtr
        mType = clone.mType
        mActivityInfo = clone.mActivityInfo
        mMemoryInfo = clone.mMemoryInfo
        mEglDrawSurface = clone.mEglDrawSurface
        mEglReadSurface = clone.mEglReadSurface
    }

    constructor(type: TYPE, id: Int, threadId: String, eglContext: Long) {
        this.mThreadId = threadId
        this.mId = id
        this.mEglContext = eglContext
        this.mType = type
        mEglDrawSurface = 0
        mEglReadSurface = 0
    }

    constructor(
        type: TYPE,
        id: Int,
        threadId: String,
        eglContext: Long,
        eglDrawSurface: Long,
        eglReadSurface: Long,
        javatrace: JavaStacktrace.Trace?,
        nativeStackPtr: Long,
        activityInfo: ActivityRecorder.ActivityInfo?,
        counter: AtomicInteger?,
    ) {
        this.mThreadId = threadId
        this.mJavaTrace = javatrace
        this.mNativeStackPtr = nativeStackPtr
        this.mType = type
        this.mActivityInfo = activityInfo
        this.mCounter = counter
        this.mId = id
        this.mEglContext = eglContext
        this.mEglDrawSurface = eglDrawSurface
        this.mEglReadSurface = eglReadSurface
    }

    fun getMemoryInfo(): MemoryInfo? = mMemoryInfo

    fun getEglContextNativeHandle(): Long = mEglContext

    fun setMemoryInfo(memoryInfo: MemoryInfo?) {
        if (this.mMemoryInfo === memoryInfo) {
            return
        }
        this.mMemoryInfo = memoryInfo
    }

    fun getId(): Int = mId

    fun getThreadId(): String = mThreadId

    fun getType(): TYPE = mType

    fun getJavaStack(): String = mJavaTrace?.content ?: ""

    fun getNativeStack(): String = ResRecordManager.getInstance().getNativeStack(this)

    fun getBriefNativeStack(): String = ResRecordManager.getInstance().getBriefNativeStack(this)

    fun getCounter(): AtomicInteger? = mCounter

    fun getNativeStackPtr(): Long = mNativeStackPtr

    fun getActivityInfo(): ActivityRecorder.ActivityInfo? = mActivityInfo

    fun isEglContextReleased(): Boolean = ResRecordManager.getInstance().isEglContextReleased(this)

    fun releaseJavaStacktrace() {
        val trace = mJavaTrace
        if (trace != null) {
            trace.reduceReference()
            mJavaTrace = null
        }
    }

    fun getEglDrawSurface(): Long = mEglDrawSurface

    fun getEglReadSurface(): Long = mEglReadSurface

    fun isEglSurfaceRelease(): Boolean = ResRecordManager.getInstance().isEglSurfaceReleased(this)

    override fun toString(): String {
        return "OpenGLInfo{" +
            "id=$mId" +
            ", activityName=$mActivityInfo" +
            ", type='$mType'" +
            ", threadId='$mThreadId'" +
            ", eglContextNativeHandle='" + getEglContextNativeHandle() + '\'' +
            ", eglContextReleased='" + isEglContextReleased() + '\'' +
            ", eglSurfaceReleased='" + isEglSurfaceRelease() + '\'' +
            ", javaStack='" + getJavaStack() + '\'' +
            ", nativeStack='" + getNativeStack() + '\'' +
            ", nativeStackPtr=$mNativeStackPtr" +
            ", memoryInfo=$mMemoryInfo" +
            '}'
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OpenGLInfo) return false
        return mId == other.mId &&
            getEglContextNativeHandle() == other.getEglContextNativeHandle() &&
            mType == other.mType
    }

    override fun hashCode(): Int {
        return Objects.hash(mId, getEglContextNativeHandle(), mType)
    }
}

