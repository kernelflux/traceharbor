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

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Printer
import com.kernelflux.traceharbor.lifecycle.owners.ProcessUILifecycleOwner
import com.kernelflux.traceharbor.listeners.IAppForeground
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by zhangshaowen on 17/7/5.
 *
 * Static facade — Java callers reach methods via
 * `TraceHarborHandlerThread.getDefaultHandler()` etc., so everything
 * stays in the companion object with `@JvmStatic`.
 */
class TraceHarborHandlerThread private constructor() {

    companion object {
        private const val TAG = "TraceHarbor.HandlerThread"

        const val MATRIX_THREAD_NAME: String = "default_traceharbor_thread"

        /**
         * unite defaultHandlerThread for lightweight work,
         * if you have heavy work checking, you can create a new thread.
         */
        @Volatile
        private var defaultHandlerThread: HandlerThread? = null

        @Volatile
        private var defaultHandler: Handler? = null

        @Volatile
        private var defaultMainHandler: Handler = Handler(Looper.getMainLooper())

        private val handlerThreads: HashSet<HandlerThread> = HashSet()

        @JvmField
        var isDebug: Boolean = false

        @JvmStatic
        fun getDefaultMainHandler(): Handler = defaultMainHandler

        @JvmStatic
        fun getDefaultHandlerThread(): HandlerThread {
            synchronized(TraceHarborHandlerThread::class.java) {
                if (defaultHandlerThread == null) {
                    val ht = HandlerThread(MATRIX_THREAD_NAME)
                    ht.start()
                    defaultHandlerThread = ht
                    defaultHandler = Handler(ht.looper)
                    ht.looper.setMessageLogging(if (isDebug) LooperPrinter() else null)
                    TraceHarborLog.w(
                        TAG,
                        "create default handler thread, we should use these thread normal, isDebug:%s",
                        isDebug,
                    )
                }
                return defaultHandlerThread!!
            }
        }

        @JvmStatic
        fun getDefaultHandler(): Handler {
            if (defaultHandler == null) {
                getDefaultHandlerThread()
            }
            return defaultHandler!!
        }

        @JvmStatic
        @Synchronized
        fun getNewHandlerThread(name: String, priority: Int): HandlerThread {
            val it = handlerThreads.iterator()
            while (it.hasNext()) {
                val element = it.next()
                if (!element.isAlive) {
                    it.remove()
                    TraceHarborLog.w(
                        TAG,
                        "warning: remove dead handler thread with name %s",
                        name,
                    )
                }
            }
            val handlerThread = HandlerThread(name)
            handlerThread.priority = priority
            handlerThread.start()
            handlerThreads.add(handlerThread)
            TraceHarborLog.w(
                TAG,
                "warning: create new handler thread with name %s, alive thread size:%d",
                name,
                handlerThreads.size,
            )
            return handlerThread
        }
    }

    private class LooperPrinter : Printer, IAppForeground {

        private val hashMap: ConcurrentHashMap<String, Info> = ConcurrentHashMap()

        @Volatile
        private var isForeground: Boolean

        init {
            ProcessUILifecycleOwner.addListener(this)
            this.isForeground = ProcessUILifecycleOwner.isProcessForeground
        }

        override fun println(x: String) {
            if (isForeground) {
                return
            }
            if (x[0] == '>') {
                val start = x.indexOf("} ")
                val end = x.indexOf("@", start)
                if (start < 0 || end < 0) {
                    return
                }
                val content = x.substring(start, end)
                var info = hashMap[content]
                if (info == null) {
                    info = Info()
                    info.key = content
                    hashMap[content] = info
                }
                ++info.count
            }
        }

        override fun onForeground(isForeground: Boolean) {
            this.isForeground = isForeground
            if (isForeground) {
                val start = System.currentTimeMillis()
                val list = LinkedList<Info>()
                for (info in hashMap.values) {
                    if (info.count > 1) {
                        list.add(info)
                    }
                }
                list.sortWith(Comparator { o1, o2 -> o2.count - o1.count })
                hashMap.clear()
                if (list.isNotEmpty()) {
                    TraceHarborLog.i(
                        TAG,
                        "matrix default thread has exec in background! %s cost:%s",
                        list,
                        System.currentTimeMillis() - start,
                    )
                }
            } else {
                hashMap.clear()
            }
        }

        class Info {
            var key: String? = null
            var count: Int = 0

            override fun toString(): String = "$key:$count"
        }
    }
}
