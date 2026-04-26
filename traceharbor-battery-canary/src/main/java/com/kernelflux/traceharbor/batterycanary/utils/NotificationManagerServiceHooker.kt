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

import android.app.Notification
import android.content.Context
import androidx.annotation.AnyThread
import androidx.annotation.Nullable
import androidx.annotation.RestrictTo
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.lang.reflect.Method
import java.util.ArrayList

@RestrictTo(RestrictTo.Scope.LIBRARY)
object NotificationManagerServiceHooker {
    interface IListener {
        @AnyThread
        fun onCreateNotificationChannel(@Nullable notificationChannel: Any?)

        @AnyThread
        fun onCreateNotification(id: Int, @Nullable notification: Notification?)
    }

    private val sListeners: MutableList<IListener> = ArrayList()
    private var sTryHook = false
    private val sHookCallback = object : SystemServiceBinderHooker.HookCallback {
        override fun onServiceMethodInvoke(method: Method, args: Array<Any?>?) {
            if ("createNotificationChannels" == method.name) {
                var notificationChannel: Any? = null
                if (args != null) {
                    for (arg in args) {
                        if (arg == null) {
                            continue
                        }
                        if ("android.content.pm.ParceledListSlice" == arg.javaClass.name) {
                            try {
                                val getListMethod = arg.javaClass.getDeclaredMethod("getList")
                                val list = getListMethod.invoke(arg)
                                if (list is Iterable<*>) {
                                    for (item in list) {
                                        if (item != null && "android.app.NotificationChannel" == item.javaClass.name) {
                                            notificationChannel = item
                                            break
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                TraceHarborLog.w(TAG, "try parse args fail: " + e.message)
                            }
                        }
                    }
                }
                dispatchCreateNotificationChannel(notificationChannel)
            } else if ("enqueueNotificationWithTag" == method.name) {
                var notifyId = -1
                var notification: Notification? = null
                if (args != null) {
                    for (item in args) {
                        if (item is Int) {
                            if (notifyId == -1) {
                                notifyId = item
                            }
                            continue
                        }
                        if (item is Notification) {
                            notification = item
                        }
                    }
                }
                dispatchCreateNotification(notifyId, notification)
            }
        }

        @Nullable
        override fun onServiceMethodIntercept(receiver: Any, method: Method, args: Array<Any?>?): Any? = null
    }
    private val sHookHelper =
        SystemServiceBinderHooker(Context.NOTIFICATION_SERVICE, "android.app.INotificationManager", sHookCallback)

    @JvmStatic
    @Synchronized
    fun addListener(listener: IListener?) {
        if (listener == null) {
            return
        }

        if (sListeners.contains(listener)) {
            return
        }

        sListeners.add(listener)
        checkHook()
    }

    @JvmStatic
    @Synchronized
    fun removeListener(listener: IListener?) {
        if (listener == null) {
            return
        }

        sListeners.remove(listener)
        checkUnHook()
    }

    @JvmStatic
    @Synchronized
    fun release() {
        sListeners.clear()
        checkUnHook()
    }

    private fun checkHook() {
        if (sTryHook) {
            return
        }

        if (sListeners.isEmpty()) {
            return
        }

        val hookRet = sHookHelper.doHook()
        TraceHarborLog.i(TAG, "checkHook hookRet:%b", hookRet)
        sTryHook = true
    }

    private fun checkUnHook() {
        if (!sTryHook) {
            return
        }

        if (sListeners.isNotEmpty()) {
            return
        }

        val unHookRet = sHookHelper.doUnHook()
        TraceHarborLog.i(TAG, "checkUnHook unHookRet:%b", unHookRet)
        sTryHook = false
    }

    private fun dispatchCreateNotificationChannel(@Nullable channel: Any?) {
        for (item in sListeners) {
            item.onCreateNotificationChannel(channel)
        }
    }

    private fun dispatchCreateNotification(id: Int, @Nullable notification: Notification?) {
        for (item in sListeners) {
            item.onCreateNotification(id, notification)
        }
    }

    private const val TAG = "TraceHarbor.battery.NotificationHooker"
}

