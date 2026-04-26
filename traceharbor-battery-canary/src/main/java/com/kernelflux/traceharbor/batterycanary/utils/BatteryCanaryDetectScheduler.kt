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

package com.kernelflux.traceharbor.batterycanary.utils

import android.os.Handler
import androidx.annotation.RestrictTo
import com.kernelflux.traceharbor.util.TraceHarborHandlerThread

/**
 *  Schedule the detect task(runnable) in a single thread and in FIFO.
 *
 * @author liyongjie
 *         Created by liyongjie on 2017/8/14.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
class BatteryCanaryDetectScheduler {
    private var mDetectHandler: Handler? = null
    private var started = false

    /**
     * Add to the end. Run in the called thread
     *
     * @param detectTask
     */
    fun addDetectTask(detectTask: Runnable) {
        mDetectHandler?.post(detectTask)
    }

    fun addDetectTask(detectTask: Runnable, delayInMillis: Long) {
        mDetectHandler?.postDelayed(detectTask, delayInMillis)
    }

    fun start() {
        if (started) {
            return
        }
        val detectThread = TraceHarborHandlerThread.getDefaultHandlerThread()
        mDetectHandler = Handler(detectThread.looper)
        started = true
    }

    fun quit() {
        if (started) {
            mDetectHandler?.removeCallbacksAndMessages(null)
            started = false
        }
    }
}

