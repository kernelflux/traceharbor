package com.kernelflux.traceharbor.sqlitelint.behaviour.report

import com.kernelflux.traceharbor.sqlitelint.SQLiteLintIssue
import com.kernelflux.traceharbor.sqlitelint.behaviour.BaseBehaviour

class IssueReportBehaviour(private val mReportDelegate: IReportDelegate?) : BaseBehaviour() {
    interface IReportDelegate {
        fun report(issue: SQLiteLintIssue?)
    }

    override fun onPublish(publishedIssues: List<SQLiteLintIssue>?) {
        if (mReportDelegate != null && publishedIssues != null) {
            for (issue in publishedIssues) {
                mReportDelegate.report(issue)
            }
        }
    }
}
