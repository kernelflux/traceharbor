package com.kernelflux.traceharborsample

import android.app.Application
import com.kernelflux.traceharbor.TraceHarbor
import com.kernelflux.traceharbor.plugin.Plugin
import com.kernelflux.traceharbor.plugin.PluginListener
import com.kernelflux.traceharbor.report.Issue

/**
 * @author: kerneflux
 * @date: 2026/4/27
 *
 */
class TraceHarborApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initTraceHarborSDK(this)
    }


    private fun initTraceHarborSDK(application: Application) {
        val builder = TraceHarbor.Builder(application)

        builder.pluginListener(object : PluginListener{
            override fun onInit(plugin: Plugin) {

            }

            override fun onStart(plugin: Plugin) {

            }

            override fun onStop(plugin: Plugin) {

            }

            override fun onDestroy(plugin: Plugin) {

            }

            override fun onReportIssue(issue: Issue) {

            }
        })

    }


}