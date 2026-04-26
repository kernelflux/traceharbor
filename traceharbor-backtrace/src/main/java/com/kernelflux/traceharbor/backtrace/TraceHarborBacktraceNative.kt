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

package com.kernelflux.traceharbor.backtrace

import androidx.annotation.Keep

@Keep
class TraceHarborBacktraceNative private constructor() {
    companion object {
        /**
         * Currently useless.
         */
        @Keep
        @JvmStatic
        external fun setPackageName(packageName: String?)

        /**
         * @param savingPath Where quicken unwind table will be saved.
         */
        @Keep
        @JvmStatic
        external fun setSavingPath(savingPath: String?)

        /**
         * Notify backtrace native library to acknowledge that we had warmed up.
         */
        @Keep
        @JvmStatic
        external fun setWarmedUp(hasWarmUp: Boolean)

        /**
         * mode = 0: Fp-based unwind
         * mode = 1: Quicken-based unwind
         * mode = 2: Dwarf-based unwind
         */
        @Keep
        @JvmStatic
        external fun setBacktraceMode(mode: Int)

        /**
         * @param enable set quicken always enabled.
         */
        @Keep
        @JvmStatic
        external fun setQuickenAlwaysOn(enable: Boolean)

        /**
         * Consume all so/oat files that waiting to generate quicken unwind table.
         *
         * @return Array of consumed file paths, end with elf start offset.
         */
        @Keep
        @JvmStatic
        external fun consumeRequestedQut(): Array<String>?

        /**
         * Warm-up specific so file path.
         */
        @Keep
        @JvmStatic
        external fun warmUp(soPath: String?, elfStartOffset: Int, onlySaveFile: Boolean): Boolean

        /**
         * Notify warmed-up elf file to native library.
         */
        @Keep
        @JvmStatic
        external fun notifyWarmedUp(soPath: String?, elfStartOffset: Int)

        /**
         * Test loading qut.
         */
        @Keep
        @JvmStatic
        external fun testLoadQut(soPath: String?, elfStartOffset: Int): Boolean

        /**
         * Some statistic.
         */
        @Keep
        @JvmStatic
        external fun statistic(soPath: String?): IntArray?

        /**
         * Generate quicken table immediately while stepping stack.
         */
        @Keep
        @JvmStatic
        external fun immediateGeneration(immediate: Boolean)

        /**
         * Enable logger if compilation options defined 'EnableLog'
         */
        @Keep
        @JvmStatic
        external fun enableLogger(enable: Boolean)

        /**
         * A callback from backtrace native code that will schedule an task
         * to consume QUT generate requests.
         */
        @Keep
        @JvmStatic
        fun requestQutGenerate() {
//            TraceHarborBacktrace.instance().requestQutGenerate();
        }
    }
}

