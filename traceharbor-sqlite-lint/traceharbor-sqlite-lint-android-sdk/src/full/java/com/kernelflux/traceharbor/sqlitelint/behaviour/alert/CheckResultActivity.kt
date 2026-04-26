package com.kernelflux.traceharbor.sqlitelint.behaviour.alert

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import com.kernelflux.traceharbor.sqlitelint.R
import com.kernelflux.traceharbor.sqlitelint.SQLiteLintIssue
import com.kernelflux.traceharbor.sqlitelint.behaviour.persistence.IssueStorage
import com.kernelflux.traceharbor.sqlitelint.util.SLog
import com.kernelflux.traceharbor.sqlitelint.util.SQLiteLintUtil

class CheckResultActivity : SQLiteLintBaseActivity() {
    private var mDbLabel: String? = null
    private var mCheckResultList: MutableList<SQLiteLintIssue>? = null
    private lateinit var mAdapter: CheckResultListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mDbLabel = intent.getStringExtra(KEY_DB_LABEL)
        initView()
        refreshData()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        refreshData()
    }

    private fun refreshData() {
        val issueList = IssueStorage.getIssueListByDb(mDbLabel)
        if (mCheckResultList == null) {
            mCheckResultList = issueList.toMutableList()
        } else {
            mCheckResultList!!.clear()
            mCheckResultList!!.addAll(issueList)
        }
        SLog.d(TAG, "refreshData size %d", mCheckResultList!!.size)
        mAdapter.notifyDataSetChanged()
    }

    private fun initView() {
        val dbName = SQLiteLintUtil.extractDbName(mDbLabel)
        setTitle(getString(R.string.check_result_title, dbName))

        val listView = findViewById<ListView>(R.id.list)
        mAdapter = CheckResultListAdapter()
        listView.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val issue = parent.getItemAtPosition(position) as SQLiteLintIssue
            val detailIntent = Intent()
            detailIntent.putExtra(IssueDetailActivity.KEY_ISSUE, issue)
            detailIntent.setClass(baseContext, IssueDetailActivity::class.java)
            startActivity(detailIntent)
        }
        listView.adapter = mAdapter
        mAdapter.notifyDataSetChanged()
    }

    override fun getLayoutId(): Int {
        return R.layout.activity_check_result
    }

    inner class CheckResultListAdapter : BaseAdapter() {
        private val mInflater = LayoutInflater.from(this@CheckResultActivity)

        override fun getCount(): Int {
            return mCheckResultList?.size ?: 0
        }

        override fun getItem(position: Int): SQLiteLintIssue {
            return mCheckResultList!![position]
        }

        override fun getItemId(position: Int): Long {
            return 0
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val rootView: View
            val viewHolder: ViewHolder
            if (convertView == null) {
                rootView = mInflater.inflate(R.layout.view_check_result_item, parent, false)
                viewHolder = ViewHolder()
                viewHolder.checkResultTv = rootView.findViewById(R.id.result_tv)
                viewHolder.diagnosisLevelTv = rootView.findViewById(R.id.diagnosis_level_tv)
                viewHolder.timeTv = rootView.findViewById(R.id.time_tv)
                rootView.tag = viewHolder
            } else {
                rootView = convertView
                viewHolder = rootView.tag as ViewHolder
            }

            val issue = getItem(position)
            viewHolder.checkResultTv?.text = String.format("%d、%s", position + 1, issue.desc)
            viewHolder.timeTv?.text = SQLiteLintUtil.formatTime(SQLiteLintUtil.YYYY_MM_DD_HH_mm, issue.createTime)
            viewHolder.diagnosisLevelTv?.text = SQLiteLintIssue.getLevelText(issue.level, baseContext)
            return rootView
        }
    }

    class ViewHolder {
        var checkResultTv: TextView? = null
        var diagnosisLevelTv: TextView? = null
        var timeTv: TextView? = null
    }

    companion object {
        private const val TAG = "MpApp.CheckResultActivity"
        const val KEY_DB_LABEL = "db_label"
    }
}
