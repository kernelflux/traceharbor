package com.kernelflux.traceharbor.sqlitelint

import android.content.Context
import com.kernelflux.traceharbor.sqlitelint.behaviour.BaseBehaviour
import com.kernelflux.traceharbor.sqlitelint.util.SLog
import java.util.concurrent.ConcurrentHashMap

enum class SQLiteLintAndroidCoreManager {
    INSTANCE;

    private val mCoresMap = ConcurrentHashMap<String, SQLiteLintAndroidCore>()

    fun install(context: Context, installEnv: SQLiteLint.InstallEnv, options: SQLiteLint.Options) {
        val concernedDbPath = installEnv.getConcernedDbPath()
        if (mCoresMap.containsKey(concernedDbPath)) {
            SLog.w(TAG, "install twice!! ignore")
            return
        }
        val core = SQLiteLintAndroidCore(context, installEnv, options)
        mCoresMap[concernedDbPath] = core
    }

    fun addBehavior(behaviour: BaseBehaviour, dbPath: String) {
        val core = get(dbPath) ?: return
        core.addBehavior(behaviour)
    }

    fun removeBehavior(behaviour: BaseBehaviour, dbPath: String) {
        val core = get(dbPath) ?: return
        core.removeBehavior(behaviour)
    }

    fun get(dbPath: String): SQLiteLintAndroidCore? {
        return mCoresMap[dbPath]
    }

    fun remove(dbPath: String) {
        mCoresMap.remove(dbPath)
    }

    companion object {
        private const val TAG = "SQLiteLint.SQLiteLintAndroidCoreManager"
    }
}
