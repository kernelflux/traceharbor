package com.kernelflux.traceharbor.sqlitelint.behaviour.alert

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.kernelflux.traceharbor.sqlitelint.R
import com.kernelflux.traceharbor.sqlitelint.SQLiteLintIssue
import com.kernelflux.traceharbor.sqlitelint.behaviour.BaseBehaviour
import com.kernelflux.traceharbor.sqlitelint.behaviour.persistence.IssueStorage
import com.kernelflux.traceharbor.sqlitelint.util.SLog

class IssueAlertBehaviour(
    private val mContext: Context,
    private val mConcernedDbPath: String
) : BaseBehaviour() {
    private var mLastInsertRowId: Long = 0

    init {
        createShortCut(mContext)
    }

    override fun onPublish(publishedIssues: List<SQLiteLintIssue>?) {
        if (publishedIssues == null || publishedIssues.isEmpty()) {
            return
        }

        val currentInsertRowId = IssueStorage.getLastRowId()
        if (currentInsertRowId == mLastInsertRowId) {
            SLog.v(TAG, "no new issue")
            return
        }
        mLastInsertRowId = currentInsertRowId

        sMainHandler.post {
            val intent = Intent()
            intent.setClass(mContext, CheckResultActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(CheckResultActivity.KEY_DB_LABEL, mConcernedDbPath)
            mContext.startActivity(intent)
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.IssueAlertBehaviour"
        private const val NAME = "SQLiteLint"
        private val sMainHandler = Handler(Looper.getMainLooper())

        private fun createShortCut(context: Context) {
            val cr: ContentResolver = context.contentResolver
            val contentUri = Uri.parse("content://com.android.launcher2.settings/favorites?notify=true")
            val c: Cursor? = cr.query(
                contentUri,
                arrayOf("title", "iconResource"),
                "title=?",
                arrayOf(NAME),
                null
            )
            if (c != null) {
                val count = c.count
                c.close()
                if (count > 0) {
                    return
                }
            }

            val shortcut = Intent("com.android.launcher.action.INSTALL_SHORTCUT")
            shortcut.putExtra(Intent.EXTRA_SHORTCUT_NAME, NAME)
            shortcut.putExtra("duplicate", false)

            val shortcutIntent = Intent(Intent.ACTION_MAIN)
            shortcutIntent.setClassName(context, CheckedDatabaseListActivity::class.java.name)
            shortcut.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)

            val iconRes = Intent.ShortcutIconResource.fromContext(context, R.drawable.sqlite_lint_icon)
            shortcut.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconRes)
            context.sendBroadcast(shortcut)
        }
    }
}
