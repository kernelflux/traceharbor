package com.kernelflux.traceharbor

import android.app.Activity
import android.app.Application
import com.kernelflux.traceharbor.lifecycle.owners.ProcessUILifecycleOwner
import com.kernelflux.traceharbor.listeners.IAppForeground
import com.kernelflux.traceharbor.util.TraceHarborUtil

/**
 * use [ProcessUILifecycleOwner] instead.
 *
 * Kept as a Kotlin enum so existing Java callers like
 * `AppActiveTraceHarborDelegate.INSTANCE.method()` keep compiling. A plain
 * Kotlin `object` would expose a Kotlin-style INSTANCE field too, but the
 * single-value-enum form matches Java's enum-as-singleton idiom one-to-one.
 */
@Deprecated("use ProcessUILifecycleOwner instead")
enum class AppActiveTraceHarborDelegate {
    INSTANCE;

    @Suppress("UNUSED_PARAMETER")
    fun init(application: Application) {
    }

    fun getCurrentFragmentName(): String? = ProcessUILifecycleOwner.currentFragmentName

    /**
     * must set after [Activity.onStart].
     *
     * @param fragmentName fragment identifier set by the host
     */
    fun setCurrentFragmentName(fragmentName: String?) {
        ProcessUILifecycleOwner.currentFragmentName = fragmentName
    }

    fun getVisibleScene(): String? = ProcessUILifecycleOwner.visibleScene

    @Deprecated("use ProcessUILifecycleOwner instead")
    fun isAppForeground(): Boolean = ProcessUILifecycleOwner.isProcessForeground

    /**
     * use [ProcessUILifecycleOwner] instead.
     */
    @Deprecated("use ProcessUILifecycleOwner instead")
    fun addListener(listener: IAppForeground) {
        ProcessUILifecycleOwner.addListener(listener)
    }

    /**
     * use [ProcessUILifecycleOwner] instead.
     */
    @Deprecated("use ProcessUILifecycleOwner instead")
    fun removeListener(listener: IAppForeground) {
        ProcessUILifecycleOwner.removeListener(listener)
    }

    companion object {
        private const val TAG = "TraceHarbor.AppActiveDelegate"

        @JvmStatic
        @Deprecated("use TraceHarborUtil.getTopActivityName() directly")
        fun getTopActivityName(): String? = TraceHarborUtil.getTopActivityName()
    }
}
