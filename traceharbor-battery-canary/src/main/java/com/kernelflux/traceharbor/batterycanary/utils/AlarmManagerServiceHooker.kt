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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.annotation.RestrictTo
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.lang.reflect.Method

/**
 * @author liyongjie
 * Created by liyongjie on 2017/10/30.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
object AlarmManagerServiceHooker {
    private const val TAG = "TraceHarbor.battery.AlarmHooker"

    interface IListener {
        fun onAlarmSet(
            type: Int,
            triggerAtMillis: Long,
            windowMillis: Long,
            intervalMillis: Long,
            flags: Int,
            operation: PendingIntent?,
            onAlarmListener: AlarmManager.OnAlarmListener?,
        )

        fun onAlarmRemove(
            operation: PendingIntent?,
            onAlarmListener: AlarmManager.OnAlarmListener?,
        )
    }

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
        Context.ALARM_SERVICE,
        "android.app.IAlarmManager",
        sHookCallback,
    )

    private val sListeners: MutableList<IListener> = ArrayList()

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
        if (method.name == "set" ||
            // jb-release ics-mr0-release
            method.name == "setRepeating" ||
            method.name == "setInexactRepeating"
        ) {
            dispatchSet(args)
        } else if (method.name == "remove") {
            dispatchCancel(args)
        }
    }

    private fun dispatchSet(args: Array<Any?>?) {
        val setArgs = SetArgsCompatible.createSetArgs(args)
        if (setArgs == null) {
            TraceHarborLog.w(TAG, "dispatchSet setArgs null")
            return
        }

        synchronized(AlarmManagerServiceHooker::class.java) {
            for (i in sListeners.indices) {
                sListeners[i].onAlarmSet(
                    setArgs.type,
                    setArgs.triggerAtMillis,
                    setArgs.windowMillis,
                    setArgs.intervalMillis,
                    setArgs.flags,
                    setArgs.operation,
                    setArgs.onAlarmListener,
                )
            }
        }
    }

    private fun dispatchCancel(args: Array<Any?>?) {
        val cancelArgs = CancelArgsCompatible.createCancelArgs(args)
        if (cancelArgs == null) {
            TraceHarborLog.w(TAG, "dispatchCancel cancelArgs null")
            return
        }

        synchronized(AlarmManagerServiceHooker::class.java) {
            for (i in sListeners.indices) {
                sListeners[i].onAlarmRemove(cancelArgs.operation, cancelArgs.onAlarmListener)
            }
        }
    }

    private class SetArgs {
        var type: Int = 0
        var triggerAtMillis: Long = 0
        var windowMillis: Long = 0
        var intervalMillis: Long = 0
        var flags: Int = 0
        var operation: PendingIntent? = null
        var onAlarmListener: AlarmManager.OnAlarmListener? = null
    }

    private object SetArgsCompatible {
        fun createSetArgs(argsArr: Array<Any?>?): SetArgs? {
            if (argsArr == null) {
                TraceHarborLog.w(TAG, "createSetArgs args null")
                return null
            }

            // Log infos related
            TraceHarborLog.i(
                TAG,
                "createSetArgs apiLevel:%d, codeName:%s, versionRelease:%s",
                Build.VERSION.SDK_INT,
                Build.VERSION.CODENAME,
                Build.VERSION.SDK_INT,
            )

            return createSetArgsAccordingToArgsLength(argsArr)
        }

        private fun createSetArgsAccordingToArgsLength(argsArr: Array<Any?>): SetArgs? {
            val length = argsArr.size
            TraceHarborLog.i(TAG, "createSetArgsAccordingToArgsLength: length:%s", length)
            return when (length) {
                3 -> createSetArgs3(argsArr) // jb-release ics-mr0-release set
                4 -> createSetArgs4(argsArr) // jb-release ics-mr0-release setRepeating setInexactRepeating
                6, // kitkat-release
                7, // lollipop-release
                -> createSetArgs7or6(argsArr)
                8 -> createSetArgs8(argsArr) // marshmallow-release
                11 -> createSetArgs11(argsArr) // nougat-release and oreo-release
                else -> createSetArgs11(argsArr)
            }
        }

        private fun createSetArgs11(argsArr: Array<Any?>): SetArgs? {
            if (argsArr.size != 11) {
                TraceHarborLog.w(TAG, "createSetArgs args length invalid : %d", argsArr.size)
                return null
            }

            val setArgs = SetArgs()
            if (argsArr[1] !is Int) {
                TraceHarborLog.w(TAG, "createSetArgs args idx 1 not Integer, %s", argsArr[1])
                return null
            } else {
                setArgs.type = argsArr[1] as Int
            }

            if (argsArr[2] !is Long) {
                TraceHarborLog.w(TAG, "createSetArgs args idx 2 not Long, %s", argsArr[2])
                return null
            } else {
                setArgs.triggerAtMillis = argsArr[2] as Long
            }

            if (argsArr[3] !is Long) {
                TraceHarborLog.w(TAG, "createSetArgs args idx 3 not Long, %s", argsArr[3])
                return null
            } else {
                setArgs.windowMillis = argsArr[3] as Long
            }

            if (argsArr[4] !is Long) {
                TraceHarborLog.w(TAG, "createSetArgs args idx 4 not Long, %s", argsArr[4])
                return null
            } else {
                setArgs.intervalMillis = argsArr[4] as Long
            }

            if (argsArr[5] !is Int) {
                TraceHarborLog.w(TAG, "createSetArgs args idx 5 not Integer, %s", argsArr[5])
                return null
            } else {
                setArgs.flags = argsArr[5] as Int
            }

            if (argsArr[6] != null && argsArr[6] !is PendingIntent) {
                TraceHarborLog.w(TAG, "createSetArgs args idx 6 not PendingIntent, %s", argsArr[6])
                return null
            } else {
                setArgs.operation = argsArr[6] as PendingIntent?
            }

            return setArgs
        }

        private fun createSetArgs8(argsArr: Array<Any?>): SetArgs? {
            if (argsArr.size != 8) {
                TraceHarborLog.w(TAG, "createSetArgs args length invalid : %d", argsArr.size)
                return null
            }

            val setArgs = SetArgs()
            if (argsArr[0] !is Int) {
                TraceHarborLog.w(TAG, "createSetArgs args idx 0 not Integer, %s", argsArr[0])
                return null
            } else {
                setArgs.type = argsArr[0] as Int
            }

            if (argsArr[1] !is Long) {
                TraceHarborLog.w(TAG, "createSetArgs args idx 1 not Long, %s", argsArr[1])
                return null
            } else {
                setArgs.triggerAtMillis = argsArr[1] as Long
            }

            if (argsArr[2] !is Long) {
                TraceHarborLog.w(TAG, "createSetArgs args idx 2 not Long, %s", argsArr[2])
                return null
            } else {
                setArgs.windowMillis = argsArr[2] as Long
            }

            if (argsArr[3] !is Long) {
                TraceHarborLog.w(TAG, "createSetArgs args idx 3 not Long, %s", argsArr[3])
                return null
            } else {
                setArgs.intervalMillis = argsArr[3] as Long
            }

            if (argsArr[4] !is Int) {
                TraceHarborLog.w(TAG, "createSetArgs args idx 4 not Integer, %s", argsArr[4])
                return null
            } else {
                setArgs.flags = argsArr[4] as Int
            }

            if (argsArr[5] != null && argsArr[5] !is PendingIntent) {
                TraceHarborLog.w(TAG, "createSetArgs args idx 5 not PendingIntent, %s", argsArr[5])
                return null
            } else {
                setArgs.operation = argsArr[5] as PendingIntent?
            }
            return setArgs
        }

        private fun createSetArgs7or6(argsArr: Array<Any?>): SetArgs? {
            if (argsArr.size != 7 && argsArr.size != 6) {
                TraceHarborLog.w(TAG, "createSetArgs args length invalid : %d", argsArr.size)
                return null
            }

            val setArgs = SetArgs()
            if (argsArr[0] !is Int) {
                TraceHarborLog.w(TAG, "createSetArgs args idx 0 not Integer, %s", argsArr[0])
                return null
            } else {
                setArgs.type = argsArr[0] as Int
            }

            if (argsArr[1] !is Long) {
                TraceHarborLog.w(TAG, "createSetArgs args idx 1 not Long, %s", argsArr[1])
                return null
            } else {
                setArgs.triggerAtMillis = argsArr[1] as Long
            }

            if (argsArr[2] !is Long) {
                TraceHarborLog.w(TAG, "createSetArgs args idx 2 not Long, %s", argsArr[2])
                return null
            } else {
                setArgs.windowMillis = argsArr[2] as Long
            }

            if (argsArr[3] !is Long) {
                TraceHarborLog.w(TAG, "createSetArgs args idx 3 not Long, %s", argsArr[3])
                return null
            } else {
                setArgs.intervalMillis = argsArr[3] as Long
            }

            if (argsArr[4] != null && argsArr[4] !is PendingIntent) {
                TraceHarborLog.w(TAG, "createSetArgs args idx 4 not PendingIntent, %s", argsArr[4])
                return null
            } else {
                setArgs.operation = argsArr[4] as PendingIntent?
            }
            return setArgs
        }

        private fun createSetArgs4(argsArr: Array<Any?>): SetArgs? {
            if (argsArr.size != 4) {
                TraceHarborLog.w(TAG, "createSetArgs args length invalid : %d", argsArr.size)
                return null
            }

            val setArgs = SetArgs()
            if (argsArr[0] !is Int) {
                TraceHarborLog.w(TAG, "createSetArgs args idx 0 not Integer, %s", argsArr[0])
                return null
            } else {
                setArgs.type = argsArr[0] as Int
            }

            if (argsArr[1] !is Long) {
                TraceHarborLog.w(TAG, "createSetArgs args idx 1 not Long, %s", argsArr[1])
                return null
            } else {
                setArgs.triggerAtMillis = argsArr[1] as Long
            }

            if (argsArr[2] !is Long) {
                TraceHarborLog.w(TAG, "createSetArgs args idx 2 not Long, %s", argsArr[2])
                return null
            } else {
                setArgs.intervalMillis = argsArr[2] as Long
            }

            if (argsArr[3] != null && argsArr[3] !is PendingIntent) {
                TraceHarborLog.w(TAG, "createSetArgs args idx 3 not PendingIntent, %s", argsArr[3])
                return null
            } else {
                setArgs.operation = argsArr[3] as PendingIntent?
            }
            return setArgs
        }

        private fun createSetArgs3(argsArr: Array<Any?>): SetArgs? {
            if (argsArr.size != 3) {
                TraceHarborLog.w(TAG, "createSetArgs args length invalid : %d", argsArr.size)
                return null
            }

            val setArgs = SetArgs()
            if (argsArr[0] !is Int) {
                TraceHarborLog.w(TAG, "createSetArgs args idx 0 not Integer, %s", argsArr[0])
                return null
            } else {
                setArgs.type = argsArr[0] as Int
            }

            if (argsArr[1] !is Long) {
                TraceHarborLog.w(TAG, "createSetArgs args idx 1 not Long, %s", argsArr[1])
                return null
            } else {
                setArgs.triggerAtMillis = argsArr[1] as Long
            }

            if (argsArr[2] != null && argsArr[2] !is PendingIntent) {
                TraceHarborLog.w(TAG, "createSetArgs args idx 2 not PendingIntent, %s", argsArr[2])
                return null
            } else {
                setArgs.operation = argsArr[2] as PendingIntent?
            }
            return setArgs
        }
    }

    private class CancelArgs {
        var operation: PendingIntent? = null
        var onAlarmListener: AlarmManager.OnAlarmListener? = null
    }

    private object CancelArgsCompatible {
        fun createCancelArgs(argsArr: Array<Any?>?): CancelArgs? {
            if (argsArr == null) {
                TraceHarborLog.w(TAG, "createCancelArgs args null")
                return null
            }

            // Log infos related
            TraceHarborLog.i(
                TAG,
                "createCancelArgs apiLevel:%d, codeName:%s, versionRelease:%s",
                Build.VERSION.SDK_INT,
                Build.VERSION.CODENAME,
                Build.VERSION.SDK_INT,
            )

            return createCancelArgsAccordingToArgsLength(argsArr)
        }

        private fun createCancelArgsAccordingToArgsLength(argsArr: Array<Any?>): CancelArgs? {
            val length = argsArr.size
            TraceHarborLog.i(TAG, "createCancelArgsAccordingToArgsLength: length:%s", length)
            return when (length) {
                1 -> createCancelArgs1(argsArr) // i to m
                2 -> createCancelArgs2(argsArr) // oreo-release nougat-release
                else -> createCancelArgs2(argsArr)
            }
        }

        private fun createCancelArgs2(argsArr: Array<Any?>): CancelArgs? {
            if (argsArr.size != 2) {
                TraceHarborLog.w(TAG, "createCancelArgs2 args length invalid : %d", argsArr.size)
                return null
            }

            val cancelArgs = CancelArgs()
            if (argsArr[0] != null && argsArr[0] !is PendingIntent) {
                TraceHarborLog.w(TAG, "createCancelArgs2 args idx 0 not PendingIntent, %s", argsArr[0])
                return null
            } else {
                cancelArgs.operation = argsArr[0] as PendingIntent?
            }

            return cancelArgs
        }

        private fun createCancelArgs1(argsArr: Array<Any?>): CancelArgs? {
            if (argsArr.size != 1) {
                TraceHarborLog.w(TAG, "createCancelArgs1 args length invalid : %d", argsArr.size)
                return null
            }

            val cancelArgs = CancelArgs()
            if (argsArr[0] != null && argsArr[0] !is PendingIntent) {
                TraceHarborLog.w(TAG, "createCancelArgs1 args idx 0 not PendingIntent, %s", argsArr[0])
                return null
            } else {
                cancelArgs.operation = argsArr[0] as PendingIntent?
            }
            return cancelArgs
        }
    }
}
