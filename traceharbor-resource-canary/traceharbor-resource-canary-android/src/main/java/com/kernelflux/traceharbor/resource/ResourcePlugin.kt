package com.kernelflux.traceharbor.resource

import android.app.Activity
import android.app.Application
import android.os.Build
import com.kernelflux.traceharbor.lifecycle.EmptyActivityLifecycleCallbacks
import com.kernelflux.traceharbor.plugin.Plugin
import com.kernelflux.traceharbor.plugin.PluginListener
import com.kernelflux.traceharbor.resource.config.ResourceConfig
import com.kernelflux.traceharbor.resource.config.SharePluginInfo
import com.kernelflux.traceharbor.resource.dumper.HprofFileManager
import com.kernelflux.traceharbor.resource.processor.BaseLeakProcessor
import com.kernelflux.traceharbor.resource.watcher.ActivityRefWatcher
import com.kernelflux.traceharbor.util.TraceHarborHandlerThread
import com.kernelflux.traceharbor.util.TraceHarborLog

class ResourcePlugin(
    private val mConfig: ResourceConfig,
) : Plugin() {
    private var mWatcher: ActivityRefWatcher? = null

    fun getWatcher(): ActivityRefWatcher? = mWatcher

    override fun init(app: Application, listener: PluginListener) {
        super.init(app, listener)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            TraceHarborLog.e(
                TAG,
                "API is low Build.VERSION_CODES.ICE_CREAM_SANDWICH(14), ResourcePlugin is not supported",
            )
            unSupportPlugin()
            return
        }
        mWatcher = ActivityRefWatcher(app, this)
    }

    override fun start() {
        super.start()
        if (!isSupported) {
            TraceHarborLog.e(TAG, "ResourcePlugin start, ResourcePlugin is not supported, just return")
            return
        }
        mWatcher?.start()
        TraceHarborHandlerThread.getDefaultHandler().post { HprofFileManager.checkExpiredFile() }
    }

    override fun stop() {
        super.stop()
        if (!isSupported) {
            TraceHarborLog.e(TAG, "ResourcePlugin stop, ResourcePlugin is not supported, just return")
            return
        }
        mWatcher?.stop()
    }

    override fun destroy() {
        super.destroy()
        if (!isSupported) {
            TraceHarborLog.e(TAG, "ResourcePlugin destroy, ResourcePlugin is not supported, just return")
            return
        }
        mWatcher?.destroy()
    }

    override val tag: String
        get() = SharePluginInfo.TAG_PLUGIN

    override fun onForeground(isForeground: Boolean) {
        TraceHarborLog.d(TAG, "onForeground: %s", isForeground)
        if (isPluginStarted() && mWatcher != null) {
            mWatcher?.onForeground(isForeground)
        }
    }

    fun getConfig(): ResourceConfig = mConfig

    fun isAnalyzing(): Boolean = BaseLeakProcessor.isAnalyzing()

    companion object {
        private const val TAG = "TraceHarbor.ResourcePlugin"

        @JvmStatic
        fun activityLeakFixer(application: Application) {
            application.registerActivityLifecycleCallbacks(
                object : EmptyActivityLifecycleCallbacks() {
                    override fun onActivityDestroyed(activity: Activity) {
                        ActivityLeakFixer.fixInputMethodManagerLeak(activity)
                        ActivityLeakFixer.unbindDrawables(activity)
                        ActivityLeakFixer.fixViewLocationHolderLeakApi28(activity)
                    }
                },
            )
        }
    }
}

