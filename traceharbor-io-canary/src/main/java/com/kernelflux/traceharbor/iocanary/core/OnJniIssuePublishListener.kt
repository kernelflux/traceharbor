package com.kernelflux.traceharbor.iocanary.core

fun interface OnJniIssuePublishListener {
    fun onIssuePublish(issues: List<IOIssue>?)
}
