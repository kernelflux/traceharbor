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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.PowerManager
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.util.HashSet

class WarmUpScheduler internal constructor(
    private val delegate: WarmUpDelegate,
    private val context: Context,
    private val timing: TraceHarborBacktrace.WarmUpTiming,
    delay: Long
) : Handler.Callback {
    private var idleReceiver: IdleReceiver? = null
    private val handler: Handler = Handler(Looper.getMainLooper(), this)
    private val warmUpDelay: Long = delay.coerceAtLeast(DELAY_SHORTLY)

    enum class TaskType {
        WarmUp,
        CleanUp,
        RequestConsuming,
        DiskUsage,
    }

    fun scheduleTask(type: TaskType) {
        handler.post { scheduleTaskImpl(type) }
    }

    fun scheduleTaskImpl(type: TaskType) {
        when (timing) {
            TraceHarborBacktrace.WarmUpTiming.PostStartup -> arrangeTaskDirectly(type)
            TraceHarborBacktrace.WarmUpTiming.WhileCharging,
            TraceHarborBacktrace.WarmUpTiming.WhileScreenOff -> arrangeTaskToIdleReceiver(context, type)
        }
    }

    fun taskFinished(type: TaskType) {
        when (timing) {
            TraceHarborBacktrace.WarmUpTiming.WhileCharging,
            TraceHarborBacktrace.WarmUpTiming.WhileScreenOff -> finishTaskToIdleReceiver(context, type)
            else -> Unit
        }
    }

    private fun arrangeTaskDirectly(type: TaskType) {
        when (type) {
            TaskType.WarmUp -> {
                TraceHarborLog.i(TAG, "Schedule warm-up in %ss", warmUpDelay / 1000)
                handler.sendMessageDelayed(
                    Message.obtain(handler, MSG_WARM_UP, CancellationSignal()),
                    warmUpDelay
                )
            }
            TaskType.CleanUp -> {
                TraceHarborLog.i(TAG, "Schedule clean-up in %ss", warmUpDelay / 1000)
                handler.sendMessageDelayed(
                    Message.obtain(handler, MSG_CLEAN_UP, CancellationSignal()),
                    warmUpDelay
                )
            }
            TaskType.RequestConsuming -> {
                TraceHarborLog.i(TAG, "Schedule request consuming in %ss", warmUpDelay / 1000)
                handler.sendMessageDelayed(
                    Message.obtain(handler, MSG_CONSUME_REQ_QUT, CancellationSignal()),
                    warmUpDelay
                )
            }
            TaskType.DiskUsage -> Unit
        }
    }

    @Synchronized
    private fun arrangeTaskToIdleReceiver(context: Context, type: TaskType) {
        if (idleReceiver == null) {
            idleReceiver = IdleReceiver(context, handler, timing, warmUpDelay).also { receiver ->
                receiver.arrange(type)

                TraceHarborLog.i(TAG, "Register idle receiver.")

                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_POWER_CONNECTED)
                    addAction(Intent.ACTION_POWER_DISCONNECTED)
                }
                context.registerReceiver(receiver, filter)
                receiver.refreshIdleStatus(context)
            }
        } else {
            idleReceiver?.arrange(type)
        }
    }

    @Synchronized
    private fun finishTaskToIdleReceiver(context: Context, type: TaskType) {
        val receiver = idleReceiver ?: return
        if (receiver.finish(type) == 0) {
            TraceHarborLog.i(TAG, "Unregister idle receiver.")
            context.unregisterReceiver(receiver)
            idleReceiver = null
        }
    }

    override fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            MSG_WARM_UP -> delegate.warmingUp(msg.obj as CancellationSignal)
            MSG_CONSUME_REQ_QUT -> delegate.consumingRequestedQut(msg.obj as CancellationSignal)
            MSG_CLEAN_UP -> delegate.cleaningUp(msg.obj as CancellationSignal)
            MSG_COMPUTE_DISK_USAGE -> delegate.computeDiskUsage(msg.obj as CancellationSignal)
        }
        return false
    }

    private class IdleReceiver(
        private val context: Context,
        private val idleHandler: Handler,
        private val timing: TraceHarborBacktrace.WarmUpTiming,
        private val warmUpDelay: Long
    ) : BroadcastReceiver() {
        private var cancellationSignal: CancellationSignal? = null
        private val tasks: MutableSet<TaskType> = HashSet()

        @Synchronized
        fun arrange(type: TaskType) {
            if (tasks.contains(type)) {
                return
            }
            tasks.add(type)
        }

        @Synchronized
        fun finish(type: TaskType): Int {
            tasks.remove(type)
            return tasks.size
        }

        @Synchronized
        private fun triggerIdle(isInteractive: Boolean, isCharging: Boolean) {
            TraceHarborLog.i(TAG, "Idle status changed: interactive = %s, charging = %s", isInteractive, isCharging)

            val isIdle = (!isInteractive) &&
                (timing == TraceHarborBacktrace.WarmUpTiming.WhileScreenOff || !isCharging)

            if (isIdle && cancellationSignal == null) {
                cancellationSignal = CancellationSignal()
                val signal = cancellationSignal!!
                val it = tasks.iterator()
                while (it.hasNext()) {
                    when (it.next()) {
                        TaskType.WarmUp -> {
                            if (!WarmUpUtility.hasWarmedUp(context)) {
                                idleHandler.sendMessageDelayed(
                                    Message.obtain(idleHandler, MSG_WARM_UP, signal),
                                    warmUpDelay
                                )
                                TraceHarborLog.i(TAG, "System idle, trigger warm up in %s seconds.", warmUpDelay / 1000)
                            } else {
                                it.remove()
                            }
                        }
                        TaskType.RequestConsuming -> {
                            idleHandler.sendMessageDelayed(
                                Message.obtain(idleHandler, MSG_CONSUME_REQ_QUT, signal),
                                warmUpDelay
                            )
                            TraceHarborLog.i(
                                TAG,
                                "System idle, trigger consume requested qut in %s seconds.",
                                warmUpDelay / 1000
                            )
                        }
                        TaskType.CleanUp -> {
                            if (WarmUpUtility.needCleanUp(context)) {
                                idleHandler.sendMessageDelayed(
                                    Message.obtain(idleHandler, MSG_CLEAN_UP, signal),
                                    DELAY_CLEAN_UP
                                )
                            } else {
                                it.remove()
                            }
                            TraceHarborLog.i(TAG, "System idle, trigger clean up in %s seconds.", DELAY_CLEAN_UP / 1000)
                        }
                        TaskType.DiskUsage -> {
                            if (WarmUpUtility.shouldComputeDiskUsage(context)) {
                                idleHandler.sendMessageDelayed(
                                    Message.obtain(idleHandler, MSG_COMPUTE_DISK_USAGE, signal),
                                    DELAY_SHORTLY
                                )
                            } else {
                                it.remove()
                            }
                            TraceHarborLog.i(TAG, "System idle, trigger disk usage in %s seconds.", DELAY_SHORTLY / 1000)
                        }
                    }
                }
            } else if (!isIdle && cancellationSignal != null) {
                idleHandler.removeMessages(MSG_WARM_UP)
                idleHandler.removeMessages(MSG_CONSUME_REQ_QUT)
                idleHandler.removeMessages(MSG_CLEAN_UP)
                idleHandler.removeMessages(MSG_COMPUTE_DISK_USAGE)
                cancellationSignal?.cancel()
                cancellationSignal = null
                TraceHarborLog.i(TAG, "Exit idle state, task cancelled.")
            }
        }

        @Synchronized
        fun refreshIdleStatus(context: Context) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isInteractive = pm.isInteractive
            var isCharging = false

            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (intent != null) {
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING) ||
                    (status == BatteryManager.BATTERY_STATUS_FULL)
            }
            triggerIdle(isInteractive, isCharging)
        }

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return

            var isInteractive = false
            var isCharging = false
            synchronized(this) {
                when (action) {
                    Intent.ACTION_SCREEN_ON -> isInteractive = true
                    Intent.ACTION_SCREEN_OFF -> isInteractive = false
                    Intent.ACTION_POWER_CONNECTED -> isCharging = true
                    Intent.ACTION_POWER_DISCONNECTED -> isCharging = false
                }
                triggerIdle(isInteractive, isCharging)
            }
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.WarmUpScheduler"
        private const val MSG_WARM_UP = 1
        private const val MSG_CONSUME_REQ_QUT = 2
        private const val MSG_CLEAN_UP = 3
        private const val MSG_COMPUTE_DISK_USAGE = 4

        @JvmField
        val DELAY_SHORTLY: Long = 3 * 1000L

        @JvmField
        val DELAY_CLEAN_UP: Long = DELAY_SHORTLY

        @JvmField
        val DELAY_WARM_UP: Long = DELAY_SHORTLY

        @JvmField
        val DELAY_CONSUME_REQ_QUT: Long = DELAY_SHORTLY
    }
}

