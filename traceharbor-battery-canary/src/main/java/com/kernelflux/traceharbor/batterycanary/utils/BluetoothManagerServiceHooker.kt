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

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.bluetooth.le.ScanSettings
import android.os.Build
import android.os.IBinder
import android.os.IInterface
import androidx.annotation.AnyThread
import androidx.annotation.BinderThread
import androidx.annotation.RestrictTo
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

@RestrictTo(RestrictTo.Scope.LIBRARY)
object BluetoothManagerServiceHooker {
    private const val TAG = "TraceHarbor.battery.BluetoothHooker"

    interface IListener {
        @AnyThread
        fun onStartDiscovery()

        @AnyThread
        fun onRegisterScanner()

        /**
         * Callback from H handler by AMS
         */
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @BinderThread
        fun onStartScan(scanId: Int, scanSettings: ScanSettings?)

        /**
         * Callback from H handler by AMS
         */
        @AnyThread
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        fun onStartScanForIntent(scanSettings: ScanSettings?)
    }

    private val sListeners: MutableList<IListener> = ArrayList()
    private var sTryHook = false
    private val sHookCallback: SystemServiceBinderHooker.HookCallback =
        object : SystemServiceBinderHooker.HookCallback {
            override fun onServiceMethodInvoke(method: Method, args: Array<Any?>?) {
            }

            override fun onServiceMethodIntercept(
                receiver: Any,
                method: Method,
                args: Array<Any?>?,
            ): Any? {
                if (method.name == "registerAdapter") {
                    val bluetooth = method.invoke(receiver, *(args ?: emptyArray()))
                    val proxy = bluetooth?.let { proxyBluetooth(it) }
                    return proxy ?: bluetooth
                } else if (method.name == "getBluetoothGatt") {
                    val bluetoothGatt = method.invoke(receiver, *(args ?: emptyArray()))
                    val proxy = bluetoothGatt?.let { proxyBluetoothGatt(it) }
                    return proxy ?: bluetoothGatt
                }
                return null
            }
        }
    private val sHookHelper = SystemServiceBinderHooker(
        "bluetooth_manager",
        "android.bluetooth.IBluetoothManager",
        sHookCallback,
    )

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
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

    private fun proxyBluetooth(delegate: Any): Any? {
        try {
            @SuppressLint("PrivateApi")
            val clazz = Class.forName("android.bluetooth.IBluetooth")
            val interfaces = arrayOf(IBinder::class.java, IInterface::class.java, clazz)
            val loader = delegate.javaClass.classLoader
            val handler = InvocationHandler { _, method, args ->
                if (method.name == "startDiscovery") {
                    dispatchStartDiscovery()
                }

                try {
                    safeInvocationReturn(delegate, method, args)
                } catch (e: Throwable) {
                    TraceHarborLog.printErrStackTrace(TAG, e, "invokeBluetooth fail")
                    null
                }
            }
            return Proxy.newProxyInstance(loader, interfaces, handler)
        } catch (e: Throwable) {
            TraceHarborLog.printErrStackTrace(TAG, e, "proxyBluetooth fail")
        }
        return null
    }

    private fun proxyBluetoothGatt(delegate: Any): Any? {
        try {
            @SuppressLint("PrivateApi")
            val clazz = Class.forName("android.bluetooth.IBluetoothGatt")
            val interfaces = arrayOf(IBinder::class.java, IInterface::class.java, clazz)
            val loader = delegate.javaClass.classLoader
            val handler = InvocationHandler { _, method, args ->
                if (method.name == "registerScanner") {
                    dispatchRegisterScanner()
                } else if (method.name == "startScan") {
                    var scanId = -1
                    var scanSettings: ScanSettings? = null
                    if (!args.isNullOrEmpty()) {
                        if (args[0] is Int) {
                            scanId = args[0] as Int
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            for (item in args) {
                                if (item is ScanSettings) {
                                    scanSettings = item
                                }
                            }
                        }
                    }
                    dispatchStartScan(scanId, scanSettings)
                } else if (method.name == "startScanForIntent") {
                    var scanSettings: ScanSettings? = null
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && args != null) {
                        for (item in args) {
                            if (item is ScanSettings) {
                                scanSettings = item
                            }
                        }
                    }
                    dispatchStartScanForIntent(scanSettings)
                }

                try {
                    safeInvocationReturn(delegate, method, args)
                } catch (e: Throwable) {
                    TraceHarborLog.printErrStackTrace(TAG, e, "invokeBluetoothGatt fail")
                    null
                }
            }
            return Proxy.newProxyInstance(loader, interfaces, handler)
        } catch (e: Throwable) {
            TraceHarborLog.printErrStackTrace(TAG, e, "proxyBluetoothGatt fail")
        }
        return null
    }

    private fun dispatchStartDiscovery() {
        for (item in sListeners) {
            item.onStartDiscovery()
        }
    }

    private fun dispatchRegisterScanner() {
        for (item in sListeners) {
            item.onRegisterScanner()
        }
    }

    private fun dispatchStartScan(scanId: Int, scanSettings: ScanSettings?) {
        for (item in sListeners) {
            item.onStartScan(scanId, scanSettings)
        }
    }

    private fun dispatchStartScanForIntent(scanSettings: ScanSettings?) {
        for (item in sListeners) {
            item.onStartScanForIntent(scanSettings)
        }
    }

    private fun safeInvocationReturn(receiver: Any, method: Method, args: Array<Any?>?): Any? {
        val invokeResult = try {
            method.invoke(receiver, *(args ?: emptyArray()))
        } catch (e: Throwable) {
            TraceHarborLog.printErrStackTrace(TAG, e, "reflect invocation fail")
            null
        }

        if (invokeResult != null) {
            return invokeResult
        }

        val returnType = method.returnType
        if (!returnType.isPrimitive) {
            return null
        }

        // return primitive default value
        // refer: "https://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html"
        if (returnType == java.lang.Byte.TYPE ||
            returnType == java.lang.Short.TYPE ||
            returnType == Integer.TYPE
        ) {
            return 0
        }
        if (returnType == java.lang.Long.TYPE) {
            return 0L
        }
        if (returnType == java.lang.Float.TYPE) {
            return 0.0f
        }
        if (returnType == java.lang.Double.TYPE) {
            return 0.0
        }
        if (returnType == Character.TYPE) {
            return '\u0000'
        }
        if (returnType == java.lang.Boolean.TYPE) {
            return false
        }

        return null
    }
}
