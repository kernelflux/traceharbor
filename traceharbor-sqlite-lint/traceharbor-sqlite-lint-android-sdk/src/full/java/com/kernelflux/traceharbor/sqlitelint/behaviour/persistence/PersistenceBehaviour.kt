package com.kernelflux.traceharbor.sqlitelint.behaviour.persistence

import com.kernelflux.traceharbor.sqlitelint.SQLiteLintIssue
import com.kernelflux.traceharbor.sqlitelint.behaviour.BaseBehaviour

class PersistenceBehaviour : BaseBehaviour() {
    override fun onPublish(publishedIssues: List<SQLiteLintIssue>?) {
        if (publishedIssues == null) {
            return
        }
        IssueStorage.saveIssues(publishedIssues)
    }
}
