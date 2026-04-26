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

import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import androidx.annotation.Nullable
import com.kernelflux.traceharbor.util.TraceHarborHandlerThread
import com.kernelflux.traceharbor.util.TraceHarborLog
import com.kernelflux.traceharbor.xlog.XLogNative
import java.util.concurrent.atomic.AtomicInteger

class WarmUpService : Service() {
    interface RemoteInvoker {
        fun call(cmd: Int, args: Bundle?): Bundle?
    }

    interface RemoteConnection {
        fun isConnected(): Boolean
        fun connect(context: Context, args: Bundle): Boolean
        fun disconnect(context: Context)
    }

    class RemoteInvokerImpl : RemoteInvoker, RemoteConnection {
        @Volatile
        private var resp: Messenger? = null

        @Volatile
        private var req: Messenger? = null

        private var result: Bundle? = null
        private var handlerThread: HandlerThread? = null
        private var bound = false

        private val boundLock = Object()
        private val resultLock = Object()
        private val handlerLock = Object()

        private val connection: ServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                req = Messenger(service)
                synchronized(boundLock) {
                    bound = true
                    boundLock.notifyAll()
                }
                TraceHarborLog.i(INVOKER_TAG, "This remote invoker(%s) connected.", this)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                req = null
                synchronized(boundLock) {
                    bound = false
                    boundLock.notifyAll()
                }
                TraceHarborLog.i(INVOKER_TAG, "This remote invoker(%s) disconnected.", this)
                synchronized(resultLock) {
                    result = null
                    resultLock.notifyAll()
                }
            }
        }

        private fun checkThread() {
            if (Looper.getMainLooper() == Looper.myLooper()) {
                throw RuntimeException("Should not call this from main thread!")
            }
        }

        override fun isConnected(): Boolean = bound

        override fun connect(context: Context, args: Bundle): Boolean {
            checkThread()
            if (bound) {
                return true
            }

            TraceHarborLog.i(INVOKER_TAG, "Start connecting to remote. (%s)", hashCode())

            synchronized(handlerLock) {
                handlerThread?.quitSafely()
                handlerThread =
                    HandlerThread("warm-up-remote-invoker-${hashCode()}").also { it.start() }
                resp = Messenger(object : Handler(handlerThread!!.looper) {
                    override fun handleMessage(msg: Message) {
                        val bundle = msg.obj as? Bundle ?: return
                        synchronized(resultLock) {
                            result = bundle
                            resultLock.notifyAll()
                        }
                    }
                })
            }

            val intent = Intent().apply {
                component = ComponentName(context, WarmUpService::class.java)
                putExtra(BIND_ARGS_ENABLE_LOGGER, args.getBoolean(BIND_ARGS_ENABLE_LOGGER, false))
                putExtra(BIND_ARGS_PATH_OF_XLOG_SO, args.getString(BIND_ARGS_PATH_OF_XLOG_SO, null))
            }
            context.bindService(intent, connection, BIND_AUTO_CREATE)

            try {
                synchronized(boundLock) {
                    if (!bound) {
                        boundLock.wait(60_000)
                    }
                }
            } catch (e: InterruptedException) {
                TraceHarborLog.printErrStackTrace(INVOKER_TAG, e, "")
            }

            if (!bound) {
                disconnect(context)
            }
            return bound
        }

        override fun disconnect(context: Context) {
            try {
                context.unbindService(connection)
            } catch (e: Throwable) {
                TraceHarborLog.printErrStackTrace(INVOKER_TAG, e, "")
            }

            TraceHarborLog.i(INVOKER_TAG, "Start disconnecting to remote. (%s)", hashCode())

            synchronized(handlerLock) {
                handlerThread?.quitSafely()
                handlerThread = null
            }

            synchronized(resultLock) {
                result = null
                resultLock.notifyAll()
            }
        }

        override fun call(cmd: Int, args: Bundle?): Bundle? {
            try {
                val reqMessenger = req ?: return null
                val responseBinder = resp?.binder ?: return null
                val wrap = Bundle().apply {
                    putBundle(INVOKE_ARGS, args)
                    putBinder(INVOKE_RESP, responseBinder)
                }
                reqMessenger.send(Message.obtain(null, cmd, wrap))

                synchronized(resultLock) {
                    result = null
                    resultLock.wait(300 * 1000L)
                    return result
                }
            } catch (e: RemoteException) {
                TraceHarborLog.printErrStackTrace(INVOKER_TAG, e, "")
            } catch (e: InterruptedException) {
                TraceHarborLog.printErrStackTrace(INVOKER_TAG, e, "")
            }
            return null
        }

        private companion object {
            private const val INVOKER_TAG = "TraceHarbor.WarmUpInvoker"
        }
    }

    @SuppressLint("HandlerLeak")
    private val messenger = Messenger(
        object : Handler(TraceHarborHandlerThread.getDefaultHandlerThread().looper) {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                val wrap = msg.obj as? Bundle ?: return
                val args = wrap.getBundle(INVOKE_ARGS)
                val binder = wrap.getBinder(INVOKE_RESP) ?: return

                val result = call(msg.what, args)
                val remote = Messenger(binder)
                try {
                    remote.send(Message.obtain(null, msg.what, result))
                } catch (e: RemoteException) {
                    TraceHarborLog.printErrStackTrace(TAG, e, "")
                }
            }
        }
    )

    private val warmUpDelegate = WarmUpDelegate()

    private fun isNullOrNil(string: String?): Boolean = string.isNullOrEmpty()

    @Synchronized
    private fun call(cmd: Int, args: Bundle?): Bundle {
        removeScheduledSuicide()
        try {
            val result = Bundle().apply {
                putInt(RESULT_OF_WARM_UP, INVALID_ARGUMENT)
            }
            if (args == null) {
                TraceHarborLog.i(TAG, "Args is null.")
                return result
            }

            val savingPath = args.getString(ARGS_WARM_UP_SAVING_PATH, null)
            TraceHarborLog.i(TAG, "Invoke from client with savingPath: %s.", savingPath)
            if (isNullOrNil(savingPath)) {
                TraceHarborLog.i(TAG, "Saving path is empty.")
                return result
            }
            warmUpDelegate.setSavingPath(savingPath)

            if (cmd == CMD_WARM_UP_SINGLE_ELF_FILE) {
                val pathOfSo = args.getString(ARGS_WARM_UP_PATH_OF_ELF, null)
                if (isNullOrNil(pathOfSo)) {
                    TraceHarborLog.i(TAG, "Warm-up so path is empty.")
                    return result
                }
                val offset = args.getInt(ARGS_WARM_UP_ELF_START_OFFSET, 0)
                TraceHarborLog.i(TAG, "Warm up so path %s offset %s.", pathOfSo, offset)

                val ret = if (!WarmUpUtility.UnfinishedManagement.checkAndMark(
                        this,
                        pathOfSo!!,
                        offset
                    )
                ) {
                    WARM_UP_FAILED_TOO_MANY_TIMES
                } else {
                    var success = WarmUpDelegate.internalWarmUpSoPath(pathOfSo, offset, true)
                    if (!TraceHarborBacktraceNative.testLoadQut(pathOfSo, offset)) {
                        TraceHarborLog.w(
                            TAG,
                            "Warm up elf %s:%s success, but test load qut failed!",
                            pathOfSo,
                            offset
                        )
                        success = false
                    }
                    WarmUpUtility.UnfinishedManagement.result(this, pathOfSo, offset, success)
                    if (success) OK else WARM_UP_FAILED
                }
                result.putInt(RESULT_OF_WARM_UP, ret)
            } else {
                TraceHarborLog.w(TAG, "Unknown cmd: %s", cmd)
            }
            return result
        } finally {
            scheduleSuicide(false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!hasInitiated) {
            init()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (!hasLoaded) {
            loadLibrary(intent)
        }
        return messenger.binder
    }

    private fun removeScheduledSuicide() {
        TraceHarborLog.i(TAG, "Remove scheduled suicide")
        synchronized(recyclerLock) {
            recyclerHandler?.removeMessages(MSG_SUICIDE)
            workingCall.incrementAndGet()
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.WarmUpService"
        private const val INVOKE_ARGS = "invoke-args"
        private const val INVOKE_RESP = "invoke-resp"

        const val BIND_ARGS_PATH_OF_XLOG_SO = "path-of-xlog-so"
        const val BIND_ARGS_ENABLE_LOGGER = "enable-logger"

        const val ARGS_WARM_UP_SAVING_PATH = "saving-path"
        const val ARGS_WARM_UP_PATH_OF_ELF = "path-of-elf"
        const val ARGS_WARM_UP_ELF_START_OFFSET = "elf-start-offset"
        const val RESULT_OF_WARM_UP = "warm-up-result"

        const val CMD_WARM_UP_SINGLE_ELF_FILE = 100

        const val OK = 0
        const val INVALID_ARGUMENT = -1
        const val WARM_UP_FAILED = -2
        const val WARM_UP_FAILED_TOO_MANY_TIMES = -3

        @Volatile
        private var hasInitiated = false

        @Volatile
        private var hasLoaded = false

        private var recycler: HandlerThread? = null
        private var recyclerHandler: Handler? = null
        private val workingCall = AtomicInteger(0)
        private val recyclerLock = ByteArray(0)
        private const val MSG_SUICIDE = 1
        private const val INTERVAL_OF_CHECK = 60 * 1000L

        private class RecyclerCallback : Handler.Callback {
            override fun handleMessage(msg: Message): Boolean {
                if (msg.what == MSG_SUICIDE) {
                    TraceHarborLog.i(TAG, "Suicide.")
                    android.os.Process.killProcess(android.os.Process.myPid())
                    System.exit(0)
                }
                return false
            }
        }

        @Synchronized
        private fun loadLibrary(intent: Intent?) {
            if (hasLoaded) {
                return
            }

            TraceHarborLog.i(TAG, "Init called.")
            TraceHarborBacktrace.loadLibrary()

            val enableLogger = intent?.getBooleanExtra(BIND_ARGS_ENABLE_LOGGER, false) ?: false
            val pathOfXLogSo = intent?.getStringExtra(BIND_ARGS_PATH_OF_XLOG_SO)

            TraceHarborLog.i(TAG, "Enable logger: %s", enableLogger)
            TraceHarborLog.i(TAG, "Path of XLog: %s", pathOfXLogSo)

            XLogNative.setXLogger(pathOfXLogSo)
            TraceHarborBacktrace.enableLogger(enableLogger)

            hasLoaded = true
        }

        @Synchronized
        private fun init() {
            if (hasInitiated) {
                return
            }

            synchronized(recyclerLock) {
                if (recycler == null) {
                    recycler = HandlerThread("backtrace-recycler").also { it.start() }
                    recyclerHandler = Handler(recycler!!.looper, RecyclerCallback())
                }
            }

            scheduleSuicide(true)
            hasInitiated = true
        }

        private fun scheduleSuicide(onCreate: Boolean) {
            TraceHarborLog.i(TAG, "Schedule suicide")
            synchronized(recyclerLock) {
                val handler = recyclerHandler ?: return
                if (onCreate) {
                    handler.sendEmptyMessageDelayed(MSG_SUICIDE, INTERVAL_OF_CHECK)
                } else {
                    if (workingCall.decrementAndGet() == 0) {
                        handler.sendEmptyMessageDelayed(MSG_SUICIDE, INTERVAL_OF_CHECK)
                    }
                }
                return@synchronized
            }
        }
    }
}

