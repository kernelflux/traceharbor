package com.kernelflux.traceharbor.iocanary

import android.app.Application
import com.kernelflux.traceharbor.iocanary.config.IOConfig
import com.kernelflux.traceharbor.iocanary.config.SharePluginInfo
import com.kernelflux.traceharbor.iocanary.core.IOCanaryCore
import com.kernelflux.traceharbor.iocanary.util.IOCanaryUtil
import com.kernelflux.traceharbor.plugin.Plugin
import com.kernelflux.traceharbor.plugin.PluginListener

class IOCanaryPlugin(val config: IOConfig) : Plugin() {
    private lateinit var mCore: IOCanaryCore

    override fun init(application: Application, pluginListener: PluginListener) {
        super.init(application, pluginListener)
        IOCanaryUtil.setPackageName(application)
        mCore = IOCanaryCore(this)
    }

    override fun start() {
        super.start()
        mCore.start()
    }

    override fun stop() {
        super.stop()
        mCore.stop()
    }

    override fun destroy() {
        super.destroy()
    }

    override val tag: String
        get() = SharePluginInfo.TAG_PLUGIN
}
