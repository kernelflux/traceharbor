/*
 * Tencent is pleased to support the open source community by making wechat-matrix available.
 * Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kernelflux.traceharbor.hook

import android.os.Build
import android.text.TextUtils
import androidx.annotation.Keep
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader

/**
 * Created by Yves on 2020-03-17
 *
 * Singleton — Java callers reach it as `HookManager.INSTANCE`.
 * `object HookManager` auto-generates a `public static final HookManager INSTANCE`
 * field, matching the original Java `public static final HookManager INSTANCE = new HookManager()`
 * exactly.
 *
 * Native bridges (`doPreHookInitializeNative`, `doFinalInitializeNative`)
 * are kept as `external fun` declarations on the singleton, which JNI
 * resolves against `Java_com_kernelflux_traceharbor_hook_HookManager_*`
 * — same package + class + method as before.
 */
object HookManager {
    private const val TAG = "TraceHarbor.HookManager"

    @Volatile
    private var mHasNativeInitialized: Boolean = false

    private val mInitializeGuard: ByteArray = byteArrayOf()
    private val mPendingHooks: MutableSet<AbsHook> = HashSet()

    @Volatile
    private var mEnableDebug: Boolean = BuildConfig.DEBUG

    private var mNativeLibLoader: NativeLibraryLoader? = null

    fun interface NativeLibraryLoader {
        fun loadLibrary(libName: String)
    }

    @Throws(HookFailedException::class)
    fun commitHooks() {
        synchronized(mInitializeGuard) {
            synchronized(mPendingHooks) {
                if (mPendingHooks.isEmpty()) {
                    return
                }
            }
            if (!mHasNativeInitialized) {
                try {
                    val loader = mNativeLibLoader
                    if (loader != null) {
                        loader.loadLibrary("traceharbor-hookcommon")
                    } else {
                        System.loadLibrary("traceharbor-hookcommon")
                    }
                } catch (e: Throwable) {
                    TraceHarborLog.printErrStackTrace(TAG, e, "")
                    return
                }

                if (!doPreHookInitializeNative(mEnableDebug)) {
                    throw HookFailedException("Fail to do hook common pre-hook initialize.")
                }

                commitHooksLocked()

                doFinalInitializeNative(mEnableDebug)
                mHasNativeInitialized = true
            } else {
                commitHooksLocked()
            }
        }
    }

    private var enableLibCxxSharedCheck: Boolean = false

    fun enableLibCxxSharedCheck(enable: Boolean): HookManager {
        enableLibCxxSharedCheck = enable
        return this
    }

    private fun checkLibCxxSharedLoaded(): Boolean {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.LOLLIPOP &&
            Build.VERSION.SDK_INT != Build.VERSION_CODES.LOLLIPOP_MR1) {
            return true
        }
        try {
            BufferedReader(InputStreamReader(FileInputStream("/proc/self/maps"))).use { br ->
                var line: String? = br.readLine()
                while (line != null) {
                    if (line.endsWith("libc++_shared.so")) {
                        return true
                    }
                    line = br.readLine()
                }
            }
        } catch (e: IOException) {
            TraceHarborLog.printErrStackTrace(TAG, e, "")
        }
        return false
    }

    @Throws(RuntimeException::class)
    private fun ensureLibCxxSharedLoadedForLollipop() {
        if (!enableLibCxxSharedCheck) {
            return
        }
        enableLibCxxSharedCheck = false // mark loaded
        if (checkLibCxxSharedLoaded()) {
            return
        }
        val loader = mNativeLibLoader
        if (loader != null) {
            loader.loadLibrary("c++_shared")
        } else {
            System.loadLibrary("c++_shared")
        }
    }

    @Throws(HookFailedException::class)
    private fun commitHooksLocked() {
        synchronized(mPendingHooks) {
            for (hook in mPendingHooks) {
                val nativeLibName = hook.getNativeLibraryNameInternal()
                if (TextUtils.isEmpty(nativeLibName)) {
                    continue
                }
                try {
                    ensureLibCxxSharedLoadedForLollipop()
                    val loader = mNativeLibLoader
                    if (loader != null) {
                        loader.loadLibrary(nativeLibName)
                    } else {
                        System.loadLibrary(nativeLibName)
                    }
                } catch (e: Throwable) {
                    TraceHarborLog.printErrStackTrace(TAG, e, "")
                    TraceHarborLog.e(
                        TAG, "Fail to load native library for %s, skip next steps.",
                        hook.javaClass.name,
                    )
                    hook.setStatus(AbsHook.Status.COMMIT_FAIL_ON_LOAD_LIB)
                }
            }
            for (hook in mPendingHooks) {
                if (hook.getStatus() != AbsHook.Status.UNCOMMIT) {
                    TraceHarborLog.e(
                        TAG, "%s has failed steps before, skip calling onConfigure on it.",
                        hook.javaClass.name,
                    )
                    continue
                }
                if (!hook.onConfigureInternal()) {
                    TraceHarborLog.e(TAG, "Fail to configure %s, skip next steps", hook.javaClass.name)
                    hook.setStatus(AbsHook.Status.COMMIT_FAIL_ON_CONFIGURE)
                }
            }
            for (hook in mPendingHooks) {
                if (hook.getStatus() != AbsHook.Status.UNCOMMIT) {
                    TraceHarborLog.e(
                        TAG, "%s has failed steps before, skip calling onHook on it.",
                        hook.javaClass.name,
                    )
                    continue
                }
                if (hook.onHookInternal(mEnableDebug)) {
                    TraceHarborLog.i(TAG, "%s is committed successfully.", hook.javaClass.name)
                    hook.setStatus(AbsHook.Status.COMMIT_SUCCESS)
                } else {
                    TraceHarborLog.e(TAG, "Fail to do hook in %s.", hook.javaClass.name)
                    hook.setStatus(AbsHook.Status.COMMIT_FAIL_ON_HOOK)
                }
            }
            mPendingHooks.clear()
        }
    }

    fun setEnableDebug(enabled: Boolean): HookManager {
        mEnableDebug = enabled
        return this
    }

    fun setNativeLibraryLoader(loader: NativeLibraryLoader?): HookManager {
        mNativeLibLoader = loader
        return this
    }

    fun addHook(hook: AbsHook?): HookManager {
        if (hook != null && hook.getStatus() != AbsHook.Status.COMMIT_SUCCESS) {
            synchronized(mPendingHooks) {
                mPendingHooks.add(hook)
            }
        }
        return this
    }

    fun clearHooks(): HookManager {
        synchronized(mPendingHooks) {
            mPendingHooks.clear()
            return this
        }
    }

    @JvmStatic
    @Keep
    fun getStack(): String {
        return try {
            stackTraceToString(Thread.currentThread().stackTrace)
        } catch (e: Throwable) {
            "ERROR: " + stackTraceToString(e.stackTrace)
        }
    }

    @JvmStatic
    private fun stackTraceToString(arr: Array<StackTraceElement>?): String {
        if (arr == null) {
            return ""
        }
        val sb = StringBuilder()
        for (stackTraceElement in arr) {
            val className = stackTraceElement.className
            // remove unused stacks
            if (className.contains("java.lang.Thread")) {
                continue
            }
            sb.append(stackTraceElement).append(';')
        }
        return sb.toString()
    }

    private external fun doPreHookInitializeNative(debug: Boolean): Boolean

    private external fun doFinalInitializeNative(debug: Boolean)

    class HookFailedException(message: String) : Exception(message)
}
