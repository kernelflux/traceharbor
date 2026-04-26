package com.kernelflux.traceharbor.sqlitelint

import android.app.Application
import com.kernelflux.traceharbor.plugin.Plugin
import com.kernelflux.traceharbor.plugin.PluginListener
import com.kernelflux.traceharbor.sqlitelint.config.SQLiteLintConfig

open class SQLiteLintPlugin(config: SQLiteLintConfig?) : Plugin() {
    init {
        if (config != null) {
        }
    }

    override fun init(app: Application, listener: PluginListener) {
        super.init(app, listener)
    }

    override fun start() {
        super.start()
    }

    override fun stop() {
        super.stop()
    }

    override fun destroy() {
        super.destroy()
    }

    override val tag: String
        get() = "SQLiteLint"

    fun notifySqlExecution(concernedDbPath: String, sql: String, timeCost: Int) {
    }

    fun addConcernedDB(concernDb: SQLiteLintConfig.ConcernDb) {
    }
}
