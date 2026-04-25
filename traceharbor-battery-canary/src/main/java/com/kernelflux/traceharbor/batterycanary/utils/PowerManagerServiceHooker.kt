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
import android.os.Build
import android.os.IBinder
import android.os.WorkSource
import androidx.annotation.RestrictTo
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.lang.reflect.Method

/**
 * This hook is to collect infos about how wake lock is used by the developers.
 *
 * @author liyongjie
 * Created by liyongjie on 2017/7/24.
 * @see IListener
 *
 * usage example:
 * final PowerManagerServiceHooker.IListener listener = new PowerManagerServiceHooker.IListener() {
 * @Override public void onAcquireWakeLock(IBinder token, int flags, String tag, String packageName, WorkSource workSource, String historyTag) {
 * Log.i(TAG, "onAcquireWakeLock token:" + token.toString() + ", tag:" + tag);
 * }
 * @Override public void onReleaseWakeLock(IBinder token, int flags) {
 * Log.i(TAG, "onReleaseWakeLock token:" + token.toString());
 * }
 * };
 * PowerManagerServiceHooker.addListener(listener);
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
object PowerManagerServiceHooker {
    private const val TAG = "TraceHarbor.battery.PowerHooker"

    interface IListener {
        fun onAcquireWakeLock(
            token: IBinder,
            flags: Int,
            tag: String?,
            packageName: String?,
            workSource: WorkSource?,
            historyTag: String?,
        )

        fun onReleaseWakeLock(token: IBinder, flags: Int)
    }

    private val sListeners: MutableList<IListener> = ArrayList()
    private var sTryHook = false
    private val sHookCallback: SystemServiceBinderHooker.HookCallback =
        object : SystemServiceBinderHooker.HookCallback {
            override fun onServiceMethodInvoke(method: Method, args: Array<Any?>?) {
                TraceHarborLog.v(TAG, "onServiceMethodInvoke: method name %s", method.name)
                dispatchListeners(method, args)
            }

            override fun onServiceMethodIntercept(
                receiver: Any,
                method: Method,
                args: Array<Any?>?,
            ): Any? {
                return null
            }
        }
    private val sHookHelper = SystemServiceBinderHooker(
        Context.POWER_SERVICE,
        "android.os.IPowerManager",
        sHookCallback,
    )

    /**
     * If there is a listener, then hook
     *
     * @param listener
     * @see removeListener
     * @see checkHook
     */
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

    /**
     * if there is no listeners, then unHook
     *
     * @param listener
     * @see checkUnHook
     */
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

    private fun dispatchListeners(method: Method, args: Array<Any?>?) {
        if (method.name == "acquireWakeLock") {
            dispatchAcquireWakeLock(args)
        } else if (method.name == "releaseWakeLock") {
            dispatchReleaseWakeLock(args)
        }
    }

    /**
     * @see checkAcquireWakeLockArgs
     * @param argsArr
     */
    private fun dispatchAcquireWakeLock(argsArr: Array<Any?>?) {
        val args = AcquireWakeLockArgsCompatible.createAcquireWakeLockArgs(argsArr)
        if (args == null) {
            TraceHarborLog.w(TAG, "dispatchAcquireWakeLock AcquireWakeLockArgs null")
            return
        }

        synchronized(PowerManagerServiceHooker::class.java) {
            for (i in sListeners.indices) {
                sListeners[i].onAcquireWakeLock(
                    args.token,
                    args.flags,
                    args.tag,
                    args.packageName,
                    args.ws,
                    args.historyTag,
                )
            }
        }
    }

    private fun dispatchReleaseWakeLock(argsArr: Array<Any?>?) {
        val args = ReleaseWakeLockArgsCompatible.createReleaseWakeLockArgs(argsArr)
        if (args == null) {
            TraceHarborLog.w(TAG, "dispatchReleaseWakeLock AcquireWakeLockArgs null")
            return
        }

        synchronized(PowerManagerServiceHooker::class.java) {
            for (i in sListeners.indices) {
                sListeners[i].onReleaseWakeLock(args.token, args.flags)
            }
        }
    }

    private class AcquireWakeLockArgs {
        lateinit var token: IBinder
        var flags: Int = 0
        var tag: String? = null
        var packageName: String? = null
        var ws: WorkSource? = null
        var historyTag: String? = null
    }

    private object AcquireWakeLockArgsCompatible {
        fun createAcquireWakeLockArgs(argsArr: Array<Any?>?): AcquireWakeLockArgs? {
            if (argsArr == null) {
                TraceHarborLog.w(TAG, "createAcquireWakeLockArgs args null")
                return null
            }

            // Log infos related
            TraceHarborLog.i(
                TAG,
                "createAcquireWakeLockArgs apiLevel:%d, codeName:%s, versionRelease:%s",
                Build.VERSION.SDK_INT,
                Build.VERSION.CODENAME,
                Build.VERSION.SDK_INT,
            )
            return createAcquireWakeLockArgsAccordingToArgsLength(argsArr)
        }

        private fun createAcquireWakeLockArgsAccordingToArgsLength(
            argsArr: Array<Any?>,
        ): AcquireWakeLockArgs? {
            val length = argsArr.size
            TraceHarborLog.i(TAG, "createAcquireWakeLockArgsAccordingToArgsLength: length:%s", length)
            return when (length) {
                4 -> createAcquireWakeLockArgs4(argsArr) // android I ~ J
                5, // android K
                6, // android L ~ O
                -> createAcquireWakeLockArgs6or5(argsArr)
                else -> createAcquireWakeLockArgs6or5(argsArr)
            }
        }

        private fun createAcquireWakeLockArgs6or5(argsArr: Array<Any?>): AcquireWakeLockArgs? {
            if (argsArr.size != 6 && argsArr.size != 5) {
                TraceHarborLog.w(TAG, "createAcquireWakeLockArgs6 args length invalid : %d", argsArr.size)
                return null
            }

            val args = AcquireWakeLockArgs()

            if (argsArr[0] !is IBinder) {
                TraceHarborLog.w(TAG, "createAcquireWakeLockArgs6 args idx 0 not IBinder, %s", argsArr[0])
                return null
            } else {
                args.token = argsArr[0] as IBinder
            }

            if (argsArr[1] !is Int) {
                TraceHarborLog.w(TAG, "createAcquireWakeLockArgs6 args idx 1 not Integer, %s", argsArr[1])
                return null
            } else {
                args.flags = argsArr[1] as Int
            }

            if (argsArr[2] != null && argsArr[2] !is String) {
                TraceHarborLog.w(TAG, "createAcquireWakeLockArgs6 args idx 2 not String, %s", argsArr[2])
                return null
            } else {
                args.tag = argsArr[2] as String?
            }

            if (argsArr[3] != null && argsArr[3] !is String) {
                TraceHarborLog.w(TAG, "createAcquireWakeLockArgs6 args idx 3 not String, %s", argsArr[3])
                return null
            } else {
                args.packageName = argsArr[3] as String?
            }

            if (argsArr[4] != null && argsArr[4] !is WorkSource) {
                TraceHarborLog.w(TAG, "createAcquireWakeLockArgs6 args idx 4 not WorkSource, %s", argsArr[4])
                return null
            } else {
                args.ws = argsArr[4] as WorkSource?
            }

            if (argsArr.size == 5) {
                return args
            }

            if (argsArr[5] != null && argsArr[5] !is String) {
                TraceHarborLog.w(TAG, "createAcquireWakeLockArgs6 args idx 5 not String, %s", argsArr[5])
                return null
            } else {
                args.historyTag = argsArr[5] as String?
            }

            return args
        }

        private fun createAcquireWakeLockArgs4(argsArr: Array<Any?>): AcquireWakeLockArgs? {
            if (argsArr.size != 4) {
                TraceHarborLog.w(TAG, "createAcquireWakeLockArgs4 args length invalid : %d", argsArr.size)
                return null
            }

            val args = AcquireWakeLockArgs()
            if (argsArr[2] != null && argsArr[2] !is String) {
                TraceHarborLog.w(TAG, "createAcquireWakeLockArgs6 args idx 2 not String, %s", argsArr[2])
                return null
            } else {
                args.tag = argsArr[2] as String?
            }

            if (argsArr[3] != null && argsArr[3] !is WorkSource) {
                TraceHarborLog.w(TAG, "createAcquireWakeLockArgs6 args idx 3 not WorkSource, %s", argsArr[3])
                return null
            } else {
                args.ws = argsArr[3] as WorkSource?
            }

            // API 15 ~ 16
            if (argsArr[0] is Int) {
                args.flags = argsArr[0] as Int
                if (argsArr[1] !is IBinder) {
                    TraceHarborLog.w(TAG, "createAcquireWakeLockArgs6 args idx 1 not IBinder, %s", argsArr[1])
                    return null
                } else {
                    args.token = argsArr[1] as IBinder
                }
                // API 17 ~ 18
            } else if (argsArr[0] is IBinder) {
                args.token = argsArr[0] as IBinder
                if (argsArr[1] !is Int) {
                    TraceHarborLog.w(TAG, "createAcquireWakeLockArgs4 args idx 1 not Integer, %s", argsArr[1])
                    return null
                } else {
                    args.flags = argsArr[1] as Int
                }
            } else {
                TraceHarborLog.w(TAG, "createAcquireWakeLockArgs4 args idx 0 not IBinder an Integer, %s", argsArr[0])
                return null
            }

            return args
        }
    }

    private class ReleaseWakeLockArgs {
        lateinit var token: IBinder
        var flags: Int = 0
    }

    private object ReleaseWakeLockArgsCompatible {
        fun createReleaseWakeLockArgs(argsArr: Array<Any?>?): ReleaseWakeLockArgs? {
            if (argsArr == null) {
                TraceHarborLog.w(TAG, "createReleaseWakeLockArgs args null")
                return null
            }

            // Log infos related
            TraceHarborLog.i(
                TAG,
                "createReleaseWakeLockArgs apiLevel:%d, codeName:%s, versionRelease:%s",
                Build.VERSION.SDK_INT,
                Build.VERSION.CODENAME,
                Build.VERSION.SDK_INT,
            )
            return createReleaseWakeLockArgsAccordingToArgsLength(argsArr)
        }

        private fun createReleaseWakeLockArgsAccordingToArgsLength(
            argsArr: Array<Any?>,
        ): ReleaseWakeLockArgs? {
            val length = argsArr.size
            TraceHarborLog.i(TAG, "createReleaseWakeLockArgsAccordingToArgsLength: length:%s", length)
            return when (length) {
                2 -> createReleaseWakeLockArgs2(argsArr)
                else -> createReleaseWakeLockArgs2(argsArr)
            }
        }

        private fun createReleaseWakeLockArgs2(argsArr: Array<Any?>): ReleaseWakeLockArgs? {
            if (argsArr.size != 2) {
                TraceHarborLog.w(TAG, "createReleaseWakeLockArgs2 args length invalid : %d", argsArr.size)
                return null
            }

            val args = ReleaseWakeLockArgs()

            if (argsArr[0] !is IBinder) {
                TraceHarborLog.w(TAG, "createReleaseWakeLockArgs2 args idx 0 not IBinder, %s", argsArr[0])
                return null
            } else {
                args.token = argsArr[0] as IBinder
            }

            if (argsArr[1] !is Int) {
                TraceHarborLog.w(TAG, "createReleaseWakeLockArgs2 args idx 1 not Integer, %s", argsArr[1])
                return null
            } else {
                args.flags = argsArr[1] as Int
            }
            return args
        }
    }
}
