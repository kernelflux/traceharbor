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

package com.kernelflux.traceharbor.util

/**
 * Created by zhangshaowen on 17/5/17.
 *
 * Static logging facade. Java callers reach the static methods via
 * `TraceHarborLog.i(...)`, `TraceHarborLog.printErrStackTrace(...)`,
 * `TraceHarborLog.setTraceHarborLogImp(...)`, etc. — preserved verbatim
 * by hosting them in the companion object with `@JvmStatic`.
 */
class TraceHarborLog private constructor() {

    interface TraceHarborLogImp {
        fun v(tag: String, msg: String, vararg obj: Any?)
        fun i(tag: String, msg: String, vararg obj: Any?)
        fun w(tag: String, msg: String, vararg obj: Any?)
        fun d(tag: String, msg: String, vararg obj: Any?)
        fun e(tag: String, msg: String, vararg obj: Any?)
        fun printErrStackTrace(tag: String, tr: Throwable, format: String?, vararg obj: Any?)
    }

    companion object {
        private val debugLog: TraceHarborLogImp = object : TraceHarborLogImp {
            override fun v(tag: String, format: String, vararg params: Any?) {
                val log = if (params.isEmpty()) format else String.format(format, *params)
                android.util.Log.v(tag, log)
            }

            override fun i(tag: String, format: String, vararg params: Any?) {
                val log = if (params.isEmpty()) format else String.format(format, *params)
                android.util.Log.i(tag, log)
            }

            override fun d(tag: String, format: String, vararg params: Any?) {
                val log = if (params.isEmpty()) format else String.format(format, *params)
                android.util.Log.d(tag, log)
            }

            override fun w(tag: String, format: String, vararg params: Any?) {
                val log = if (params.isEmpty()) format else String.format(format, *params)
                android.util.Log.w(tag, log)
            }

            override fun e(tag: String, format: String, vararg params: Any?) {
                val log = if (params.isEmpty()) format else String.format(format, *params)
                android.util.Log.e(tag, log)
            }

            override fun printErrStackTrace(
                tag: String,
                tr: Throwable,
                format: String?,
                vararg params: Any?,
            ) {
                var log: String =
                    if (params.isEmpty() || format == null) (format ?: "")
                    else String.format(format, *params)
                log += "  " + android.util.Log.getStackTraceString(tr)
                android.util.Log.e(tag, log)
            }
        }

        @JvmStatic
        private var matrixLogImp: TraceHarborLogImp = debugLog

        @JvmStatic
        fun setTraceHarborLogImp(imp: TraceHarborLogImp) {
            matrixLogImp = imp
        }

        @JvmStatic
        fun getImpl(): TraceHarborLogImp = matrixLogImp

        @JvmStatic
        fun v(tag: String, msg: String, vararg obj: Any?) {
            matrixLogImp.v(tag, msg, *obj)
        }

        @JvmStatic
        fun e(tag: String, msg: String, vararg obj: Any?) {
            matrixLogImp.e(tag, msg, *obj)
        }

        @JvmStatic
        fun w(tag: String, msg: String, vararg obj: Any?) {
            matrixLogImp.w(tag, msg, *obj)
        }

        @JvmStatic
        fun i(tag: String, msg: String, vararg obj: Any?) {
            matrixLogImp.i(tag, msg, *obj)
        }

        @JvmStatic
        fun d(tag: String, msg: String, vararg obj: Any?) {
            matrixLogImp.d(tag, msg, *obj)
        }

        @JvmStatic
        fun printErrStackTrace(tag: String, tr: Throwable, format: String?, vararg obj: Any?) {
            matrixLogImp.printErrStackTrace(tag, tr, format, *obj)
        }
    }
}
