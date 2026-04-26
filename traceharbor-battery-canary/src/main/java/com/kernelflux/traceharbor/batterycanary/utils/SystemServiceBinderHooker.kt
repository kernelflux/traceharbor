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

import android.os.IBinder
import android.os.IInterface
import androidx.annotation.Nullable
import androidx.annotation.RestrictTo
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.lang.reflect.Field
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * @author liyongjie
 *         Created by liyongjie on 2017/10/30.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
class SystemServiceBinderHooker(
    private val mServiceName: String,
    private val mServiceClass: String,
    private val mHookCallback: HookCallback,
) {
    interface HookCallback {
        fun onServiceMethodInvoke(method: Method, args: Array<Any?>?)

        @Nullable
        @Throws(Throwable::class)
        fun onServiceMethodIntercept(receiver: Any, method: Method, args: Array<Any?>?): Any?
    }

    @Nullable
    private var mOriginServiceBinder: IBinder? = null

    @Nullable
    private var mDelegateServiceBinder: IBinder? = null

    @Suppress("PrivateApi", "UNCHECKED_CAST")
    fun doHook(): Boolean {
        TraceHarborLog.i(TAG, "doHook: serviceName:%s, serviceClsName:%s", mServiceName, mServiceClass)
        return try {
            val binderProxyHandler = BinderProxyHandler(mServiceName, mServiceClass, mHookCallback)
            val delegateBinder = binderProxyHandler.createProxyBinder()

            val serviceManagerCls = Class.forName("android.os.ServiceManager")
            val cacheField: Field = serviceManagerCls.getDeclaredField("sCache")
            cacheField.isAccessible = true
            val cache = cacheField[null] as MutableMap<String, IBinder>
            cache[mServiceName] = delegateBinder

            mDelegateServiceBinder = delegateBinder
            mOriginServiceBinder = binderProxyHandler.getOriginBinder()
            true
        } catch (e: Throwable) {
            TraceHarborLog.e(TAG, "#doHook exp: " + e.localizedMessage)
            false
        }
    }

    @Suppress("PrivateApi", "UNCHECKED_CAST")
    fun doUnHook(): Boolean {
        val originServiceBinder = mOriginServiceBinder
        if (originServiceBinder == null) {
            TraceHarborLog.w(TAG, "#doUnHook mOriginServiceBinder null")
            return false
        }
        val delegateServiceBinder = mDelegateServiceBinder
        if (delegateServiceBinder == null) {
            TraceHarborLog.w(TAG, "#doUnHook mDelegateServiceBinder null")
            return false
        }

        return try {
            val currentBinder = BinderProxyHandler.getCurrentBinder(mServiceName)
            if (delegateServiceBinder != currentBinder) {
                TraceHarborLog.w(TAG, "#doUnHook mDelegateServiceBinder != currentBinder")
                return false
            }

            val serviceManagerCls = Class.forName("android.os.ServiceManager")
            val cacheField: Field = serviceManagerCls.getDeclaredField("sCache")
            cacheField.isAccessible = true
            val cache = cacheField[null] as MutableMap<String, IBinder>
            cache[mServiceName] = originServiceBinder
            true
        } catch (e: Throwable) {
            TraceHarborLog.e(TAG, "#doUnHook exp: " + e.localizedMessage)
            false
        }
    }

    private class BinderProxyHandler(
        serviceName: String,
        serviceClass: String,
        callback: HookCallback,
    ) : InvocationHandler {
        private val mOriginBinder: IBinder = getCurrentBinder(serviceName)
        private val mServiceManagerProxy: Any = createServiceManagerProxy(serviceClass, mOriginBinder, callback)

        override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
            if ("queryLocalInterface" == method.name) {
                return mServiceManagerProxy
            }
            return method.invoke(mOriginBinder, *(args ?: emptyArray()))
        }

        fun getOriginBinder(): IBinder = mOriginBinder

        @Suppress("PrivateApi")
        fun createProxyBinder(): IBinder {
            val serviceManagerCls = Class.forName("android.os.ServiceManager")
            val classLoader = serviceManagerCls.classLoader
                ?: throw IllegalStateException("Can not get ClassLoader of " + serviceManagerCls.name)
            return Proxy.newProxyInstance(
                classLoader,
                arrayOf<Class<*>>(IBinder::class.java),
                this,
            ) as IBinder
        }

        companion object {
            @Suppress("PrivateApi")
            fun getCurrentBinder(serviceName: String): IBinder {
                val serviceManagerCls = Class.forName("android.os.ServiceManager")
                val getService = serviceManagerCls.getDeclaredMethod("getService", String::class.java)
                return getService.invoke(null, serviceName) as IBinder
            }

            @Suppress("PrivateApi")
            private fun createServiceManagerProxy(
                serviceClassName: String,
                originBinder: IBinder,
                callback: HookCallback?,
            ): Any {
                val serviceManagerCls = Class.forName(serviceClassName)
                val serviceManagerStubCls = Class.forName("$serviceClassName\$Stub")
                val classLoader = serviceManagerStubCls.classLoader
                    ?: throw IllegalStateException("get service manager ClassLoader fail!")
                val asInterfaceMethod = serviceManagerStubCls.getDeclaredMethod("asInterface", IBinder::class.java)
                val originManagerService = requireNotNull(asInterfaceMethod.invoke(null, originBinder))
                return Proxy.newProxyInstance(
                    classLoader,
                    arrayOf<Class<*>>(IBinder::class.java, IInterface::class.java, serviceManagerCls),
                ) { _, method, args ->
                    if (callback != null) {
                        callback.onServiceMethodInvoke(method, args)
                        val result = callback.onServiceMethodIntercept(originManagerService, method, args)
                        if (result != null) {
                            return@newProxyInstance result
                        }
                    }
                    method.invoke(originManagerService, *(args ?: emptyArray()))
                }
            }
        }
    }

    private companion object {
        private const val TAG = "TraceHarbor.battery.SystemServiceBinderHooker"
    }
}

