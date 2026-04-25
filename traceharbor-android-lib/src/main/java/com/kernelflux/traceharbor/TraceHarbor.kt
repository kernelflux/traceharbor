package com.kernelflux.traceharbor

import android.app.Application
import com.kernelflux.traceharbor.lifecycle.TraceHarborLifecycleConfig
import com.kernelflux.traceharbor.lifecycle.TraceHarborLifecycleOwnerInitializer
import com.kernelflux.traceharbor.lifecycle.supervisor.ProcessSupervisor
import com.kernelflux.traceharbor.plugin.DefaultPluginListener
import com.kernelflux.traceharbor.plugin.Plugin
import com.kernelflux.traceharbor.plugin.PluginListener
import com.kernelflux.traceharbor.util.TraceHarborLog

class TraceHarbor private constructor(
    val application: Application,
    listener: PluginListener,
    val plugins: HashSet<Plugin>,
    config: TraceHarborLifecycleConfig,
) {

    init {
        TraceHarborLifecycleOwnerInitializer.init(application, config)
        ProcessSupervisor.init(application, config.supervisorConfig)
        for (plugin in plugins) {
            plugin.init(application, listener)
        }
    }

    fun startAllPlugins() {
        for (plugin in plugins) {
            plugin.start()
        }
    }

    fun stopAllPlugins() {
        for (plugin in plugins) {
            plugin.stop()
        }
    }

    fun destroyAllPlugins() {
        for (plugin in plugins) {
            plugin.destroy()
        }
    }

    fun getPluginByTag(tag: String): Plugin? {
        for (plugin in plugins) {
            if (plugin.tag == tag) {
                return plugin
            }
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Plugin> getPluginByClass(pluginClass: Class<T>): T? {
        val className = pluginClass.name
        for (plugin in plugins) {
            if (plugin.javaClass.name == className) {
                return plugin as T
            }
        }
        return null
    }

    class Builder(private val application: Application?) {
        private var pluginListener: PluginListener? = null

        private var mLifecycleConfig = TraceHarborLifecycleConfig()

        private val plugins = HashSet<Plugin>()

        init {
            if (application == null) {
                throw RuntimeException("matrix init, application is null")
            }
        }

        fun plugin(plugin: Plugin): Builder {
            val tag = plugin.tag
            for (exist in plugins) {
                if (tag == exist.tag) {
                    throw RuntimeException(String.format("plugin with tag %s is already exist", tag))
                }
            }
            plugins.add(plugin)
            return this
        }

        fun pluginListener(pluginListener: PluginListener?): Builder {
            this.pluginListener = pluginListener
            return this
        }

        fun matrixLifecycleConfig(config: TraceHarborLifecycleConfig): Builder {
            this.mLifecycleConfig = config
            return this
        }

        fun build(): TraceHarbor {
            val app = application ?: throw RuntimeException("matrix init, application is null")
            val listener = pluginListener ?: DefaultPluginListener(app)
            return TraceHarbor(app, listener, plugins, mLifecycleConfig)
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.TraceHarbor"

        @Volatile
        private var sInstance: TraceHarbor? = null

        @JvmStatic
        fun setLogIml(imp: TraceHarborLog.TraceHarborLogImp) {
            TraceHarborLog.setTraceHarborLogImp(imp)
        }

        @JvmStatic
        fun isInstalled(): Boolean = sInstance != null

        @JvmStatic
        fun init(matrix: TraceHarbor?): TraceHarbor {
            if (matrix == null) {
                throw RuntimeException("TraceHarbor init, TraceHarbor should not be null.")
            }
            synchronized(TraceHarbor::class.java) {
                if (sInstance == null) {
                    sInstance = matrix
                } else {
                    TraceHarborLog.e(TAG, "TraceHarbor instance is already set. this invoking will be ignored")
                }
            }
            return sInstance!!
        }

        @JvmStatic
        fun with(): TraceHarbor {
            return sInstance ?: throw RuntimeException("you must init TraceHarbor sdk first")
        }
    }
}
