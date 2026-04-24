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

/**
 * Created by Yves on 2020-03-18
 */
abstract class AbsHook {
    private var mStatus: Status = Status.UNCOMMIT

    enum class Status {
        UNCOMMIT,
        COMMIT_SUCCESS,
        COMMIT_FAIL_ON_LOAD_LIB,
        COMMIT_FAIL_ON_CONFIGURE,
        COMMIT_FAIL_ON_HOOK,
    }

    /**
     * Originally package-private (`void setStatus(Status status)`).
     * `internal` is the closest Kotlin equivalent and is sufficient because
     * the only caller (HookManager) lives in the same module.
     */
    internal fun setStatus(status: Status) {
        mStatus = status
    }

    fun getStatus(): Status = mStatus

    protected abstract fun getNativeLibraryName(): String

    protected abstract fun onConfigure(): Boolean

    protected abstract fun onHook(enableDebug: Boolean): Boolean

    /**
     * Internal bridges so [HookManager] (which lives in the same module
     * but a different class) can drive the lifecycle methods. Java
     * subclasses still `@Override` the `protected` declarations above
     * unchanged — these wrappers just expose the Kotlin-protected
     * methods at module scope without polluting the public API.
     *
     * Original Java relied on package-private (`onConfigure` and
     * `onHook` were declared `protected` but in the same package as
     * HookManager); Kotlin's `protected` is strictly class-scoped, so
     * we route through an `internal` wrapper instead.
     */
    internal fun getNativeLibraryNameInternal(): String = getNativeLibraryName()

    internal fun onConfigureInternal(): Boolean = onConfigure()

    internal fun onHookInternal(enableDebug: Boolean): Boolean = onHook(enableDebug)
}
