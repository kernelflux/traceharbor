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

package com.kernelflux.traceharbor.hook.pthread

import android.text.TextUtils
import androidx.annotation.Keep
import com.kernelflux.traceharbor.hook.AbsHook
import com.kernelflux.traceharbor.hook.HookManager
import com.kernelflux.traceharbor.util.TraceHarborLog

/**
 * Created by Yves on 2020-03-11
 */
object PthreadHook : AbsHook() {
    private const val TAG = "TraceHarbor.Pthread"

    private val hookThreadName: MutableSet<String> = HashSet()
    private var enableQuicken = false
    private var enableLog = false
    private var configured = false
    private var threadTraceEnabled = false
    private var threadStackShrinkConfig: ThreadStackShrinkConfig? = null
    private var hookInstalled = false
    private var enableTracePthreadRelease = false

    class ThreadStackShrinkConfig {
        private var enabled = false
        val ignoreCreatorSoPatterns: MutableSet<String> = HashSet(5)

        fun setEnabled(value: Boolean): ThreadStackShrinkConfig {
            enabled = value
            return this
        }

        fun isEnabled(): Boolean = enabled

        fun setIgnoreCreatorSoPatterns(vararg patterns: String?): ThreadStackShrinkConfig {
            if (patterns.isEmpty()) {
                ignoreCreatorSoPatterns.clear()
            } else {
                for (pattern in patterns) {
                    if (!pattern.isNullOrEmpty()) {
                        ignoreCreatorSoPatterns.add(pattern)
                    }
                }
            }
            return this
        }

        fun addIgnoreCreatorSoPatterns(pattern: String): ThreadStackShrinkConfig {
            ignoreCreatorSoPatterns.add(pattern)
            return this
        }
    }

    fun addHookThread(regex: String?): PthreadHook {
        if (TextUtils.isEmpty(regex)) {
            TraceHarborLog.e(TAG, "thread regex is empty!!!")
        } else {
            hookThreadName.add(regex!!)
        }
        return this
    }

    fun addHookThread(vararg regexArr: String?): PthreadHook {
        for (regex in regexArr) {
            addHookThread(regex)
        }
        return this
    }

    fun setThreadTraceEnabled(enabled: Boolean): PthreadHook {
        threadTraceEnabled = enabled
        return this
    }

    /**
     * trace pthread_detach or pthread_join
     */
    fun enableTracePthreadRelease(enabled: Boolean): PthreadHook {
        enableTracePthreadRelease = enabled
        return this
    }

    fun setThreadStackShrinkConfig(config: ThreadStackShrinkConfig?): PthreadHook {
        threadStackShrinkConfig = config
        return this
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

    fun dump(path: String?) {
        if (TextUtils.isEmpty(path)) {
            throw IllegalArgumentException("path NOT valid: $path")
        }
        if (getStatus() == Status.COMMIT_SUCCESS) {
            dumpNative(path!!)
        }
    }

    fun enableQuicken(enable: Boolean) {
        enableQuicken = enable
        if (configured) {
            enableQuickenNative(enableQuicken)
        }
    }

    fun enableLogger(enable: Boolean) {
        enableLog = enable
        if (configured) {
            enableLoggerNative(enableLog)
        }
    }

    override fun getNativeLibraryName(): String {
        return "traceharbor-pthreadhook"
    }

    override fun onConfigure(): Boolean {
        addHookThreadNameNative(hookThreadName.toTypedArray())
        enableQuickenNative(enableQuicken)
        enableLoggerNative(enableLog)
        enableTracePthreadReleaseNative(enableTracePthreadRelease)

        val config = threadStackShrinkConfig
        if (config != null) {
            if (setThreadStackShrinkIgnoredCreatorSoPatternsNative(config.ignoreCreatorSoPatterns.toTypedArray())) {
                setThreadStackShrinkEnabledNative(config.isEnabled())
            } else {
                TraceHarborLog.e(
                    TAG,
                    "setThreadStackShrinkIgnoredCreatorSoPatternsNative return false, do not enable ThreadStackShrinker."
                )
                setThreadStackShrinkEnabledNative(false)
            }
        } else {
            setThreadStackShrinkIgnoredCreatorSoPatternsNative(null)
            setThreadStackShrinkEnabledNative(false)
        }
        setThreadTraceEnabledNative(threadTraceEnabled)
        configured = true
        return true
    }

    override fun onHook(enableDebug: Boolean): Boolean {
        if (threadTraceEnabled || (threadStackShrinkConfig?.isEnabled() == true)) {
            if (!hookInstalled) {
                installHooksNative(enableDebug)
                hookInstalled = true
            }
        }
        return true
    }

    @Keep
    private external fun addHookThreadNameNative(threadNames: Array<String>)

    @Keep
    private external fun setThreadTraceEnabledNative(enabled: Boolean)

    @Keep
    private external fun setThreadStackShrinkEnabledNative(enabled: Boolean)

    @Keep
    private external fun setThreadStackShrinkIgnoredCreatorSoPatternsNative(patterns: Array<String>?): Boolean

    @Keep
    private external fun enableLoggerNative(enable: Boolean)

    @Keep
    private external fun enableQuickenNative(enable: Boolean)

    @Keep
    private external fun dumpNative(path: String)

    @Keep
    private external fun installHooksNative(enableDebug: Boolean)

    @Keep
    private external fun enableTracePthreadReleaseNative(enable: Boolean)
}

