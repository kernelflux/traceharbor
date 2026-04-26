package com.kernelflux.traceharbor.batterycanary.utils

import androidx.annotation.NonNull
import java.lang.ref.WeakReference

/**
 * @author Kaede
 * @since 2022/1/4
 */
abstract class ThreadSafeReference<T> {
    @JvmField
    var mThreadLocalRef: ThreadLocal<WeakReference<T>>? = null

    @NonNull
    abstract fun onCreate(): T

    @NonNull
    fun safeGet(): T {
        val threadLocalRef = mThreadLocalRef
        if (threadLocalRef != null) {
            val target = threadLocalRef.get()?.get()
            if (target != null) {
                return target
            }
        }
        val target = onCreate()
        val ref = WeakReference(target)
        mThreadLocalRef = ThreadLocal<WeakReference<T>>().also { it.set(ref) }
        return target
    }
}

