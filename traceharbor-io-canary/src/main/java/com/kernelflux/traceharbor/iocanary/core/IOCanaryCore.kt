package com.kernelflux.traceharbor.iocanary.core

import com.kernelflux.traceharbor.iocanary.IOCanaryPlugin
import com.kernelflux.traceharbor.iocanary.config.IOConfig
import com.kernelflux.traceharbor.iocanary.detect.CloseGuardHooker
import com.kernelflux.traceharbor.iocanary.util.IOCanaryUtil
import com.kernelflux.traceharbor.report.Issue
import com.kernelflux.traceharbor.report.IssuePublisher

class IOCanaryCore(private val mIoCanaryPlugin: IOCanaryPlugin) :
    OnJniIssuePublishListener,
    IssuePublisher.OnIssueDetectListener {

    private val mIOConfig: IOConfig = mIoCanaryPlugin.config

    private var mIsStart = false
    private var mCloseGuardHooker: CloseGuardHooker? = null

    fun start() {
        initDetectorsAndHookers(mIOConfig)
        synchronized(this) {
            mIsStart = true
        }
    }

    @Synchronized
    fun isStart(): Boolean = mIsStart

    fun stop() {
        synchronized(this) {
            mIsStart = false
        }

        mCloseGuardHooker?.unHook()
        IOCanaryJniBridge.uninstall()
    }

    override fun onDetectIssue(issue: Issue) {
        mIoCanaryPlugin.onDetectIssue(issue)
    }

    private fun initDetectorsAndHookers(ioConfig: IOConfig) {
        if (ioConfig.isDetectFileIOInMainThread() ||
            ioConfig.isDetectFileIOBufferTooSmall() ||
            ioConfig.isDetectFileIORepeatReadSameFile()
        ) {
            IOCanaryJniBridge.install(ioConfig, this)
        }

        if (ioConfig.isDetectIOClosableLeak()) {
            mCloseGuardHooker = CloseGuardHooker(this)
            mCloseGuardHooker?.hook()
        }
    }

    override fun onIssuePublish(issues: List<IOIssue>?) {
        if (issues == null) {
            return
        }

        for (issue in issues) {
            IOCanaryUtil.convertIOIssueToReportIssue(issue)?.let { reportIssue ->
                mIoCanaryPlugin.onDetectIssue(reportIssue)
            }
        }
    }
}
