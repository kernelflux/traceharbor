package com.kernelflux.traceharbor.lifecycle.supervisor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.kernelflux.traceharbor.lifecycle.IStateObserver
import com.kernelflux.traceharbor.lifecycle.TraceHarborLifecycleThread
import com.kernelflux.traceharbor.lifecycle.owners.ProcessUILifecycleOwner
import com.kernelflux.traceharbor.util.TraceHarborLog
import com.kernelflux.traceharbor.util.TraceHarborUtil
import com.kernelflux.traceharbor.util.safeApply

/**
 * Created by Yves on 2022/2/10
 */
internal object SubordinatePacemaker : BroadcastReceiver() {

    private const val SUPERVISOR_INSTALLED = "SUPERVISOR_INSTALLED"

    private var packageName: String? = null
    private val permission by lazy { "${packageName!!}.traceharbor.permission.PROCESS_SUPERVISOR" }

    @Volatile
    private var pacemaker: IStateObserver? = null

    private var callback: (() -> Unit)? = null

    fun install(context: Context?, callback: () -> Unit) {
        if (pacemaker != null) {
            TraceHarborLog.e(ProcessSupervisor.tag, "SubordinatePacemaker: already installed")
            return
        }
        if (ProcessSupervisor.isSupervisor) {
            return
        }
        this.callback = callback
        packageName = TraceHarborUtil.getPackageName(context)
        val filter = IntentFilter()
        filter.addAction(SUPERVISOR_INSTALLED)
        context?.registerReceiver(this, filter, permission, null)

        pacemaker = object : IStateObserver {
            override fun on() {
                TraceHarborLifecycleThread.handler.post {
                    TraceHarborLog.i(ProcessSupervisor.tag, "SubordinatePacemaker: callback when foreground")
                    callback.invoke()
                }
            }

            override fun off() {}
        }
        ProcessUILifecycleOwner.startedStateOwner.observeForever(pacemaker!!)
    }

    fun uninstall(context: Context?) {
        if (pacemaker!= null) {
            ProcessUILifecycleOwner.startedStateOwner.removeObserver(pacemaker!!)
            pacemaker = null
            safeApply(ProcessSupervisor.tag) {
                context?.unregisterReceiver(this)
            }
            TraceHarborLog.i(ProcessSupervisor.tag, "SubordinatePacemaker: uninstalled")
        }
    }

    fun notifySupervisorInstalled(context: Context?) {
        packageName = TraceHarborUtil.getPackageName(context)
        Intent(SUPERVISOR_INSTALLED).apply {
            context?.sendBroadcast(this, permission)
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        when(intent?.action) {
            SUPERVISOR_INSTALLED -> {
                TraceHarborLifecycleThread.handler.post {
                    TraceHarborLog.i(ProcessSupervisor.tag, "SubordinatePacemaker: callback when supervisor installed")
                    callback?.invoke()
                }
            }
        }
    }
}