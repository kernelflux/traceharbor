/*
 * Tencent is pleased to support the open source community by making wechat-matrix available.
 * Copyright (C) 2018 THL A29 Limited, a Tencent company. All rights reserved.
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

package com.kernelflux.traceharbor.trace.retrace

/**
 * Created by caichongyang on 2017/6/3.
 */
class MethodInfo(
    private val originalClassName: String,
    originalType: String,
    originalName: String,
    originalArguments: String
) {
    private var originalTypeValue: String = originalType
    private var originalNameValue: String = originalName
    private var originalArgumentsValue: String = originalArguments
    private var descValue: String? = null

    constructor(methodInfo: MethodInfo) : this(
        methodInfo.originalClassName,
        methodInfo.getOriginalType(),
        methodInfo.getOriginalName(),
        methodInfo.getOriginalArguments()
    ) {
        descValue = methodInfo.getDesc()
    }

    fun matches(originalType: String?, originalArguments: String?): Boolean {
        return (originalType == null || originalType == this.originalTypeValue) &&
            (originalArguments == null || originalArguments == this.originalArgumentsValue)
    }

    fun getOriginalClassName(): String {
        return originalClassName
    }

    fun getOriginalType(): String {
        return originalTypeValue
    }

    fun getOriginalName(): String {
        return originalNameValue
    }

    fun getOriginalArguments(): String {
        return originalArgumentsValue
    }

    fun getDesc(): String? {
        return descValue
    }

    fun setDesc(desc: String?) {
        this.descValue = desc
    }

    fun setOriginalName(originalName: String) {
        this.originalNameValue = originalName
    }

    fun setOriginalArguments(originalArguments: String) {
        this.originalArgumentsValue = originalArguments
    }

    fun setOriginalType(originalType: String) {
        this.originalTypeValue = originalType
    }

    companion object {
        @JvmStatic
        fun deFault(): MethodInfo {
            return MethodInfo("", "", "", "")
        }
    }
}

