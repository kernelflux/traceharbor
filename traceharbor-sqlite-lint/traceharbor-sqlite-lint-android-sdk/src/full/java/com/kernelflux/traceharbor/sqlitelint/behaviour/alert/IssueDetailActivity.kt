package com.kernelflux.traceharbor.sqlitelint.behaviour.alert

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.kernelflux.traceharbor.sqlitelint.R
import com.kernelflux.traceharbor.sqlitelint.SQLiteLintIssue
import com.kernelflux.traceharbor.sqlitelint.util.SLog
import com.kernelflux.traceharbor.sqlitelint.util.SQLiteLintUtil

class IssueDetailActivity : SQLiteLintBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val issue = intent.getParcelableExtra<SQLiteLintIssue>(KEY_ISSUE)
        initView(issue)
    }

    private fun initView(issue: SQLiteLintIssue?) {
        if (issue == null) {
            return
        }

        setTitle(getString(R.string.diagnosis_detail_title))
        val timeTv = findViewById<TextView>(R.id.time_tv)
        val diagnosisLevelTv = findViewById<TextView>(R.id.diagnosis_level_tv)
        timeTv.text = SQLiteLintUtil.formatTime(SQLiteLintUtil.YYYY_MM_DD_HH_mm, issue.createTime)
        diagnosisLevelTv.text = SQLiteLintIssue.getLevelText(issue.level, baseContext)

        if (!SQLiteLintUtil.isNullOrNil(issue.desc)) {
            val descLayout = findViewById<LinearLayout>(R.id.desc_layout)
            val descTv = findViewById<TextView>(R.id.desc_tv)
            descTv.text = issue.desc
            descLayout.visibility = View.VISIBLE
            descLayout.setOnClickListener {
                SLog.v(TAG, issue.desc!!.replace("%", "###"))
            }
        }

        if (!SQLiteLintUtil.isNullOrNil(issue.detail)) {
            val detailLayout = findViewById<LinearLayout>(R.id.detail_layout)
            val detailTv = findViewById<TextView>(R.id.detail_tv)
            detailTv.text = issue.detail
            detailLayout.visibility = View.VISIBLE
            detailTv.setOnClickListener {
                SLog.v(TAG, issue.detail!!.replace("%", "###"))
            }
        }

        if (!SQLiteLintUtil.isNullOrNil(issue.advice)) {
            val adviceLayout = findViewById<LinearLayout>(R.id.advice_layout)
            val adviceTv = findViewById<TextView>(R.id.advice_tv)
            adviceTv.text = issue.advice
            adviceLayout.visibility = View.VISIBLE
        }

        if (!SQLiteLintUtil.isNullOrNil(issue.extInfo)) {
            val adviceLayout = findViewById<LinearLayout>(R.id.ext_info_layout)
            val extInfoTv = findViewById<TextView>(R.id.ext_info_tv)
            extInfoTv.text = getString(R.string.diagnosis_ext_info_title, issue.extInfo)
            adviceLayout.visibility = View.VISIBLE
        }
    }

    override fun getLayoutId(): Int {
        return R.layout.activity_diagnosis_detail
    }

    companion object {
        private const val TAG = "MicroMsg.IssueDetailActivity"
        const val KEY_ISSUE = "issue"
    }
}
