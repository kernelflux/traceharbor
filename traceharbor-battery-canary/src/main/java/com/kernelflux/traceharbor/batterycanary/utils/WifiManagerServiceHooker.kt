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
object WifiManagerServiceHooker {
    interface IListener {
        @AnyThread
        fun onStartScan()

        @AnyThread
        fun onGetScanResults()
    }

    private val sListeners: MutableList<IListener> = ArrayList()
    private var sTryHook = false
    private val sHookCallback = object : SystemServiceBinderHooker.HookCallback {
        override fun onServiceMethodInvoke(method: Method, args: Array<Any?>?) {
            if ("startScan" == method.name) {
                dispatchStartScan()
            } else if ("getScanResults" == method.name) {
                dispatchGetScanResults()
            }
        }

        @Nullable
        override fun onServiceMethodIntercept(receiver: Any, method: Method, args: Array<Any?>?): Any? = null
    }
    private val sHookHelper = SystemServiceBinderHooker(Context.WIFI_SERVICE, "android.net.wifi.IWifiManager", sHookCallback)

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

    private fun dispatchStartScan() {
        for (item in sListeners) {
            item.onStartScan()
        }
    }

    private fun dispatchGetScanResults() {
        for (item in sListeners) {
            item.onGetScanResults()
        }
    }

    private const val TAG = "TraceHarbor.battery.WifiHooker"
}

