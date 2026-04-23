package io.traceharbor.lifecycle

import android.annotation.SuppressLint
import android.app.Application
import androidx.annotation.NonNull
import io.traceharbor.lifecycle.TraceHarborLifecycleOwnerInitializer.Companion.init
import io.traceharbor.lifecycle.owners.ForegroundServiceLifecycleOwner
import io.traceharbor.lifecycle.owners.OverlayWindowLifecycleOwner
import io.traceharbor.lifecycle.owners.ProcessUILifecycleOwner
import io.traceharbor.lifecycle.supervisor.SupervisorConfig
import io.traceharbor.util.TraceHarborLog
import io.traceharbor.util.safeLet

/**
 * All feature that would change the origin TraceHarbor behavior is disabled by default.
 */
data class TraceHarborLifecycleConfig(
    val supervisorConfig: SupervisorConfig = SupervisorConfig(),
    /**
     * Injects Service#mActivityManager if true
     */
    val enableFgServiceMonitor: Boolean = false,
    /**
     * Injects WindowManagerGlobal#mRoots if true
     */
    val enableOverlayWindowMonitor: Boolean = false,

    val lifecycleThreadConfig: LifecycleThreadConfig = LifecycleThreadConfig(),

    val enableLifecycleLogger: Boolean = false
)

/**
 * You should init [io.traceharbor.TraceHarbor] or call [init] manually before creating any Activity
 * Created by Yves on 2021/9/14
 */
class TraceHarborLifecycleOwnerInitializer {
    companion object {
        private const val TAG = "TraceHarbor.ProcessLifecycleOwnerInit"

        @Volatile
        private var inited = false

        @JvmStatic
        fun init(
            @NonNull app: Application,
            config: TraceHarborLifecycleConfig
        ) {
            if (inited) {
                return
            }
            inited = true
            if (hasCreatedActivities()) {
                ("TraceHarbor Warning: TraceHarbor might be inited after launching first Activity, " +
                        "which would disable some features like ProcessLifecycleOwner, " +
                        "pls consider calling MultiProcessLifecycleInitializer#init manually " +
                        "or initializing matrix at Application#onCreate").let {
                    TraceHarborLog.e(TAG, it)
                }
                return
            }
            TraceHarborLifecycleThread.init(config.lifecycleThreadConfig)
            ProcessUILifecycleOwner.init(app)
            ForegroundServiceLifecycleOwner.init(app, config.enableFgServiceMonitor)
            OverlayWindowLifecycleOwner.init(config.enableOverlayWindowMonitor)
            TraceHarborLifecycleLogger.init(app, config.enableLifecycleLogger)
        }

        @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
        @JvmStatic
        private fun hasCreatedActivities() = safeLet(tag = TAG, defVal = false) {
            val clazzActivityThread = Class.forName("android.app.ActivityThread")
            val objectActivityThread =
                clazzActivityThread.getMethod("currentActivityThread").invoke(null)
            val fieldMActivities = clazzActivityThread.getDeclaredField("mActivities")
            fieldMActivities.isAccessible = true
            val mActivities = fieldMActivities.get(objectActivityThread) as Map<*, *>?
            return mActivities != null && mActivities.isNotEmpty()
        }
    }
}