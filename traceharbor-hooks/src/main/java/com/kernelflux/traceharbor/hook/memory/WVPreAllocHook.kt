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

import android.os.Build
import androidx.annotation.NonNull
import com.kernelflux.traceharbor.hook.AbsHook

object WVPreAllocHook : AbsHook() {
    @NonNull
    override fun getNativeLibraryName(): String {
        return "traceharbor-memoryhook"
    }

    override fun onConfigure(): Boolean {
        // Ignored.
        return true
    }

    override fun onHook(enableDebug: Boolean): Boolean {
        return installHooksNative(Build.VERSION.SDK_INT, javaClass.classLoader, enableDebug)
    }

    private external fun installHooksNative(sdkVer: Int, classLoader: ClassLoader?, enableDebug: Boolean): Boolean
}

