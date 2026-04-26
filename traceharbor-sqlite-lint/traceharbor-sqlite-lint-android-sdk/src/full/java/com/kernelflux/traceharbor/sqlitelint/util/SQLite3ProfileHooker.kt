package com.kernelflux.traceharbor.sqlitelint.util

import java.lang.reflect.Field

class SQLite3ProfileHooker private constructor() {
    companion object {
        private const val TAG = "SQLiteLint.SQLite3ProfileHooker"

        @Volatile
        private var sIsTryHook: Boolean = false

        @JvmStatic
        fun hook() {
            SLog.i(TAG, "hook sIsTryHook: %b", sIsTryHook)
            nativeStartProfile()
            if (!sIsTryHook) {
                val hookRet = doHook()
                SLog.i(TAG, "hook hookRet: %b", hookRet)
                sIsTryHook = true
            }
        }

        @JvmStatic
        fun unHook() {
            if (sIsTryHook) {
                val unHookRet = doUnHook()
                SLog.i(TAG, "unHook unHookRet: %b", unHookRet)
                sIsTryHook = false
            }
        }

        private fun doHook(): Boolean {
            if (!hookOpenSQLite3Profile()) {
                SLog.i(TAG, "doHook hookOpenSQLite3Profile failed")
                return false
            }
            return nativeDoHook()
        }

        private fun doUnHook(): Boolean {
            unHookOpenSQLite3Profile()
            nativeStopProfile()
            return true
        }

        private external fun nativeDoHook(): Boolean
        private external fun nativeStartProfile()
        private external fun nativeStopProfile()

        private fun hookOpenSQLite3Profile(): Boolean {
            return try {
                val clsSQLiteDebug = Class.forName("android.database.sqlite.SQLiteDebug")
                val fieldDST: Field = clsSQLiteDebug.getDeclaredField("DEBUG_SQL_TIME")
                fieldDST.isAccessible = true
                fieldDST.setBoolean(clsSQLiteDebug, true)
                fieldDST.isAccessible = false
                true
            } catch (e: ClassNotFoundException) {
                SLog.e(TAG, "prepareHookBeforeOpenDatabase: e=%s", e.localizedMessage)
                false
            } catch (e: IllegalAccessException) {
                SLog.e(TAG, "prepareHookBeforeOpenDatabase: e=%s", e.localizedMessage)
                false
            } catch (e: NoSuchFieldException) {
                SLog.e(TAG, "prepareHookBeforeOpenDatabase: e=%s", e.localizedMessage)
                false
            }
        }

        private fun unHookOpenSQLite3Profile(): Boolean {
            return try {
                val clsSQLiteDebug = Class.forName("android.database.sqlite.SQLiteDebug")
                val fieldDST: Field = clsSQLiteDebug.getDeclaredField("DEBUG_SQL_TIME")
                fieldDST.isAccessible = true
                fieldDST.setBoolean(clsSQLiteDebug, false)
                fieldDST.isAccessible = false
                true
            } catch (e: ClassNotFoundException) {
                SLog.e(TAG, "unHookOpenSQLite3Profile: e=%s", e.localizedMessage)
                false
            } catch (e: IllegalAccessException) {
                SLog.e(TAG, "unHookOpenSQLite3Profile: e=%s", e.localizedMessage)
                false
            } catch (e: NoSuchFieldException) {
                SLog.e(TAG, "unHookOpenSQLite3Profile: e=%s", e.localizedMessage)
                false
            }
        }
    }
}
