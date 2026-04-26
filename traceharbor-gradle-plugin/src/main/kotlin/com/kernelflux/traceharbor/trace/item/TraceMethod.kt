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

package com.kernelflux.traceharbor.trace.item

import com.kernelflux.traceharbor.javalib.util.Util
import com.kernelflux.traceharbor.trace.retrace.MappingCollector
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Created by caichongyang on 2017/6/3.
 */
class TraceMethod {
    @JvmField
    var id: Int = 0

    @JvmField
    var accessFlag: Int = 0

    @JvmField
    var className: String? = null

    @JvmField
    var methodName: String? = null

    @JvmField
    var desc: String? = null

    fun getMethodName(): String {
        return if (desc == null || isNativeMethod()) {
            "$className.$methodName"
        } else {
            "$className.$methodName.$desc"
        }
    }

    /**
     * proguard -> original
     */
    fun revert(processor: MappingCollector?) {
        if (processor == null) {
            return
        }
        val methodInfo = processor.originalMethodInfo(className, methodName, desc)
        methodName = methodInfo.getOriginalName()
        desc = methodInfo.getDesc()
        className = processor.originalClassName(className, className.orEmpty())
    }

    /**
     * original -> proguard
     */
    fun proguard(processor: MappingCollector?) {
        if (processor == null) {
            return
        }
        val methodInfo = processor.obfuscatedMethodInfo(className, methodName, desc)
        methodName = methodInfo.getOriginalName()
        desc = methodInfo.getDesc()
        className = processor.proguardClassName(className, className.orEmpty())
    }

    fun getReturn(): String? {
        if (Util.isNullOrNil(desc)) {
            return null
        }
        return Type.getReturnType(desc).toString()
    }

    override fun toString(): String {
        return if (desc == null || isNativeMethod()) {
            "$id,$accessFlag,$className $methodName"
        } else {
            "$id,$accessFlag,$className $methodName $desc"
        }
    }

    fun toIgnoreString(): String {
        return if (desc == null || isNativeMethod()) {
            "$className $methodName"
        } else {
            "$className $methodName $desc"
        }
    }

    fun isNativeMethod(): Boolean {
        return accessFlag and Opcodes.ACC_NATIVE != 0
    }

    override fun equals(other: Any?): Boolean {
        return if (other is TraceMethod) {
            other.getMethodName() == getMethodName()
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }

    companion object {
        private const val TAG = "TraceHarbor.TraceMethod"

        @JvmStatic
        fun create(
            id: Int,
            accessFlag: Int,
            className: String,
            methodName: String,
            desc: String
        ): TraceMethod {
            val traceMethod = TraceMethod()
            traceMethod.id = id
            traceMethod.accessFlag = accessFlag
            traceMethod.className = className.replace("/", ".")
            traceMethod.methodName = methodName
            traceMethod.desc = desc.replace("/", ".")
            return traceMethod
        }
    }
}

