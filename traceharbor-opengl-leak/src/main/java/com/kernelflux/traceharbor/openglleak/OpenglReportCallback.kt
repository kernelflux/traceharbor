package com.kernelflux.traceharbor.openglleak

interface OpenglReportCallback {
    fun onExpProcessSuccess()

    fun onExpProcessFail()

    fun onHookSuccess()

    fun onHookFail()
}

