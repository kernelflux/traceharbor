package com.kernelflux.traceharbor.trace.listeners

import android.app.Activity

fun interface IAppMethodBeatListener {
    fun onActivityFocused(activity: Activity)
}
