package com.kernelflux.traceharbor.sqlitelint

interface IOnSqlExecutionCallback {
    fun onSqlExecuted(dbPath: String, sql: String, timeCost: Long)
}
