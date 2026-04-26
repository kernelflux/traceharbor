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

package com.kernelflux.traceharbor.hook.memory

import android.text.TextUtils
import androidx.annotation.Keep
import androidx.annotation.NonNull
import com.kernelflux.traceharbor.hook.AbsHook
import com.kernelflux.traceharbor.hook.HookManager
import com.kernelflux.traceharbor.util.TraceHarborLog

/**
 * Created by Yves on 2019-08-08
 */
object MemoryHook : AbsHook() {
    private const val TAG = "TraceHarbor.MemoryHook"

    private val hookSoSet: MutableSet<String> = HashSet()
    private val ignoreSoSet: MutableSet<String> = HashSet()

    private var minTraceSize = 0
    private var maxTraceSize = 0
    private var stacktraceLogThreshold = 10 * 1024 * 1024
    private var enableStacktrace = false
    private var enableMmap = false
    private var memGuardInstalled = false
    private var hookInstalled = false

    fun addHookSo(regex: String?): MemoryHook {
        if (TextUtils.isEmpty(regex)) {
            TraceHarborLog.e(TAG, "thread regex is empty!!!")
        } else {
            hookSoSet.add(regex!!)
        }
        return this
    }

    fun addHookSo(vararg regexArr: String?): MemoryHook {
        for (regex in regexArr) {
            addHookSo(regex)
        }
        return this
    }

    fun addIgnoreSo(regex: String?): MemoryHook {
        if (TextUtils.isEmpty(regex)) {
            return this
        }
        ignoreSoSet.add(regex!!)
        return this
    }

    fun addIgnoreSo(vararg regexArr: String?): MemoryHook {
        for (regex in regexArr) {
            addIgnoreSo(regex)
        }
        return this
    }

    fun enableStacktrace(enable: Boolean): MemoryHook {
        enableStacktrace = enable
        return this
    }

    /**
     * >= 0, 0 表示不限制
     */
    fun tracingAllocSizeRange(min: Int, max: Int): MemoryHook {
        minTraceSize = min
        maxTraceSize = max
        return this
    }

    fun enableMmapHook(enable: Boolean): MemoryHook {
        enableMmap = enable
        return this
    }

    fun stacktraceLogThreshold(threshold: Int): MemoryHook {
        stacktraceLogThreshold = threshold
        return this
    }

    fun notifyMemGuardInstalled() {
        memGuardInstalled = true
    }

    /**
     * notice: it is an exclusive interface
     */
    @Throws(HookManager.HookFailedException::class)
    fun hook() {
        HookManager.clearHooks()
            .addHook(this)
            .commitHooks()
    }

    @NonNull
    override fun getNativeLibraryName(): String {
        return "traceharbor-memoryhook"
    }

    override fun onConfigure(): Boolean {
        if (memGuardInstalled) {
            TraceHarborLog.w(TAG, "MemGuard has been installed, skip MemoryHook install logic.")
            return false
        }

        if (minTraceSize < 0 || (maxTraceSize != 0 && maxTraceSize < minTraceSize)) {
            throw IllegalArgumentException(
                "sizes should not be negative and maxSize should be 0 or greater than minSize: " +
                    "min = $minTraceSize, max = $maxTraceSize"
            )
        }

        TraceHarborLog.d(TAG, "enable mmap? $enableMmap")
        enableMmapHookNative(enableMmap)
        setTracingAllocSizeRangeNative(minTraceSize, maxTraceSize)
        setStacktraceLogThresholdNative(stacktraceLogThreshold)
        enableStacktraceNative(enableStacktrace)

        return true
    }

    override fun onHook(enableDebug: Boolean): Boolean {
        if (!hookInstalled) {
            installHooksNative(hookSoSet.toTypedArray(), ignoreSoSet.toTypedArray(), enableDebug)
            hookInstalled = true
        }
        return true
    }

    fun dump(logPath: String?, jsonPath: String?) {
        if (getStatus() == Status.COMMIT_SUCCESS) {
            dumpNative(logPath, jsonPath)
        }
    }

    @Keep
    private external fun dumpNative(logPath: String?, jsonPath: String?)

    @Keep
    private external fun setTracingAllocSizeRangeNative(minSize: Int, maxSize: Int)

    @Keep
    private external fun enableStacktraceNative(enable: Boolean)

    @Keep
    private external fun enableMmapHookNative(enable: Boolean)

    @Keep
    private external fun setStacktraceLogThresholdNative(threshold: Int)

    @Keep
    private external fun installHooksNative(
        hookSoPatterns: Array<String>,
        ignoreSoPatterns: Array<String>,
        enableDebug: Boolean
    )
}

