package com.kernelflux.traceharbor.sqlitelint.behaviour.alert

import android.content.Context
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
import com.kernelflux.traceharbor.sqlitelint.behaviour.persistence.IssueStorage
import com.kernelflux.traceharbor.sqlitelint.behaviour.persistence.SQLiteLintDbHelper
import com.kernelflux.traceharbor.sqlitelint.util.SLog
import com.kernelflux.traceharbor.sqlitelint.util.SQLiteLintUtil

class CheckedDatabaseListActivity : SQLiteLintBaseActivity() {
    private lateinit var mListView: ListView
    private lateinit var mListAdapter: CheckedDatabaseListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SQLiteLintDbHelper.INSTANCE.initialize(this)
        initView()
    }

    override fun onResume() {
        super.onResume()
        refreshView()
    }

    override fun getLayoutId(): Int {
        return R.layout.activity_checked_database_list
    }

    private fun initView() {
        setTitle(getString(R.string.checked_database_list_title))
        mListView = findViewById(R.id.list)
        mListAdapter = CheckedDatabaseListAdapter(this)
        mListView.adapter = mListAdapter
        mListView.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val dbPath = parent.getItemAtPosition(position) as String
            if (SQLiteLintUtil.isNullOrNil(dbPath)) {
                return@OnItemClickListener
            }
            val intent = Intent()
            intent.setClass(this@CheckedDatabaseListActivity, CheckResultActivity::class.java)
            intent.putExtra(CheckResultActivity.KEY_DB_LABEL, dbPath)
            startActivity(intent)
        }
    }

    private fun refreshView() {
        val defectiveDbList = IssueStorage.getDbPathList()
        SLog.i(TAG, "refreshView defectiveDbList is %d", defectiveDbList.size)
        mListAdapter.setData(defectiveDbList)
    }

    private class CheckedDatabaseListAdapter(context: Context) : BaseAdapter() {
        private val mInflater = LayoutInflater.from(context)
        private var mDefectiveDbList: List<String>? = null

        fun setData(defectiveDbList: List<String>) {
            mDefectiveDbList = defectiveDbList
            notifyDataSetChanged()
        }

        override fun getCount(): Int {
            return mDefectiveDbList?.size ?: 0
        }

        override fun getItem(position: Int): String {
            return mDefectiveDbList!![position]
        }

        override fun getItemId(position: Int): Long {
            return 0
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val rootView: View
            val viewHolder: ViewHolder
            if (convertView == null) {
                rootView = mInflater.inflate(R.layout.view_checked_database_item, parent, false)
                viewHolder = ViewHolder()
                viewHolder.dbPathTv = rootView.findViewById(R.id.db_path)
                rootView.tag = viewHolder
            } else {
                rootView = convertView
                viewHolder = rootView.tag as ViewHolder
            }
            val dbPath = getItem(position)
            viewHolder.dbPathTv?.text = dbPath
            return rootView
        }
    }

    class ViewHolder {
        var dbPathTv: TextView? = null
    }

    companion object {
        private const val TAG = "SQLiteLint.CheckedDatabaseListActivity"
    }
}
