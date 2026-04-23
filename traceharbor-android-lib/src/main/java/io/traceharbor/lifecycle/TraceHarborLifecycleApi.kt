package io.traceharbor.lifecycle

import androidx.lifecycle.LifecycleOwner

@Deprecated("")
abstract class ITraceHarborLifecycleCallback {
    internal var stateObserver: IStateObserver? = null
    abstract fun onForeground()
    abstract fun onBackground()
}

abstract class ITraceHarborForegroundCallback {
    internal var stateObserver: IStateObserver? = null
    abstract fun onEnterForeground()

    /**
     * NOTICE: do NOT always mean background!!!, depending on the observed owner
     */
    abstract fun onExitForeground()
}

abstract class ITraceHarborBackgroundCallback {
    internal var stateObserver: IStateObserver? = null
    abstract fun onEnterBackground()

    /**
     * NOTICE: do NOT always mean foreground!!!, depending on the observed owner
     */
    abstract fun onExitBackground()
}

interface IBackgroundStatefulOwner : IStatefulOwner {
    fun isBackground() = active()

    fun addLifecycleCallback(callback: ITraceHarborBackgroundCallback) =
        observeForever(object : IStateObserver {
            override fun on() = callback.onEnterBackground()
            override fun off() = callback.onExitBackground()
        }.also { callback.stateObserver = it })

    fun addLifecycleCallback(lifecycleOwner: LifecycleOwner, callback: ITraceHarborBackgroundCallback) =
        observeWithLifecycle(lifecycleOwner, object : IStateObserver {
            override fun on() = callback.onEnterBackground()
            override fun off() = callback.onExitBackground()
        }.also { callback.stateObserver = it })

    fun removeLifecycleCallback(callback: ITraceHarborBackgroundCallback) {
        callback.stateObserver?.let { removeObserver(it) }
    }

    @Deprecated("")
    fun addLifecycleCallback(callback: ITraceHarborLifecycleCallback) =
        observeForever(object : IStateObserver {
            override fun on() = callback.onBackground()
            override fun off() = callback.onForeground()
        }.also { callback.stateObserver = it })

    @Deprecated("")
    fun addLifecycleCallback(lifecycleOwner: LifecycleOwner, callback: ITraceHarborLifecycleCallback) =
        observeWithLifecycle(lifecycleOwner, object : IStateObserver {
            override fun on() = callback.onBackground()
            override fun off() = callback.onForeground()
        }.also { callback.stateObserver = it })

    @Deprecated("")
    fun removeLifecycleCallback(callback: ITraceHarborLifecycleCallback) {
        callback.stateObserver?.let { removeObserver(it) }
    }
}

interface IForegroundStatefulOwner : IStatefulOwner {
    fun isForeground() = active()

    fun addLifecycleCallback(callback: ITraceHarborForegroundCallback) =
        observeForever(object : IStateObserver {
            override fun on() = callback.onEnterForeground()
            override fun off() = callback.onExitForeground()
        }.also { callback.stateObserver = it })

    fun addLifecycleCallback(lifecycleOwner: LifecycleOwner, callback: ITraceHarborForegroundCallback) =
        observeWithLifecycle(lifecycleOwner, object : IStateObserver {
            override fun on() = callback.onEnterForeground()
            override fun off() = callback.onExitForeground()
        }.also { callback.stateObserver = it })

    fun removeLifecycleCallback(callback: ITraceHarborForegroundCallback) {
        callback.stateObserver?.let { removeObserver(it) }
    }

    @Deprecated("")
    fun addLifecycleCallback(callback: ITraceHarborLifecycleCallback) =
        observeForever(object : IStateObserver {
            override fun on() = callback.onForeground()
            override fun off() = callback.onBackground()
        }.also { callback.stateObserver = it })

    @Deprecated("")
    fun addLifecycleCallback(lifecycleOwner: LifecycleOwner, callback: ITraceHarborLifecycleCallback) =
        observeWithLifecycle(lifecycleOwner, object : IStateObserver {
            override fun on() = callback.onForeground()
            override fun off() = callback.onBackground()
        }.also { callback.stateObserver = it })

    @Deprecated("")
    fun removeLifecycleCallback(callback: ITraceHarborLifecycleCallback) {
        callback.stateObserver?.let { removeObserver(it) }
    }
}