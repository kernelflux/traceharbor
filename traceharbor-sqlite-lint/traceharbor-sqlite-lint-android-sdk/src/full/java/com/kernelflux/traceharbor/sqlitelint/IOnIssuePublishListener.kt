package com.kernelflux.traceharbor.sqlitelint

interface IOnIssuePublishListener {
    fun onPublish(publishedIssues: List<SQLiteLintIssue>?)
}
