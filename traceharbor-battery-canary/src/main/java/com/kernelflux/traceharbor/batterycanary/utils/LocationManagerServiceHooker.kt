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

import android.content.Context
import androidx.annotation.AnyThread
import androidx.annotation.Nullable
import androidx.annotation.RestrictTo
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.lang.reflect.Method
import java.util.ArrayList

@RestrictTo(RestrictTo.Scope.LIBRARY)
object LocationManagerServiceHooker {
    interface IListener {
        @AnyThread
        fun onRequestLocationUpdates(minTimeMillis: Long, minDistance: Float)
    }

    private val sListeners: MutableList<IListener> = ArrayList()
    private var sTryHook = false
    private val sHookCallback = object : SystemServiceBinderHooker.HookCallback {
        override fun onServiceMethodInvoke(method: Method, args: Array<Any?>?) {
            if ("requestLocationUpdates" == method.name) {
                var minTime = -1L
                var minDistance = -1f
                if (args != null) {
                    for (item in args) {
                        if (item != null && "android.location.LocationRequest" == item.javaClass.name) {
                            try {
                                val mFastestInterval = item.javaClass.getDeclaredMethod("getFastestInterval")
                                mFastestInterval.isAccessible = true
                                minTime = mFastestInterval.invoke(item) as Long
                                val mSmallestDisplacement = item.javaClass.getDeclaredMethod("getSmallestDisplacement")
                                mSmallestDisplacement.isAccessible = true
                                minDistance = mSmallestDisplacement.invoke(item) as Float
                            } catch (throwable: Throwable) {
                                TraceHarborLog.printErrStackTrace(TAG, throwable, "")
                            }
                        }
                    }
                }
                dispatchRequestLocationUpdates(minTime, minDistance)
            }
        }

        @Nullable
        override fun onServiceMethodIntercept(receiver: Any, method: Method, args: Array<Any?>?): Any? = null
    }
    private val sHookHelper = SystemServiceBinderHooker(Context.LOCATION_SERVICE, "android.location.ILocationManager", sHookCallback)

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

    private fun dispatchRequestLocationUpdates(minTimeMillis: Long, minDistance: Float) {
        for (item in sListeners) {
            item.onRequestLocationUpdates(minTimeMillis, minDistance)
        }
    }

    private const val TAG = "TraceHarbor.battery.LocationHooker"
}

