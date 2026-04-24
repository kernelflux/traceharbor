package com.kernelflux.traceharbor.trace.listeners

import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(api = Build.VERSION_CODES.N)
interface IDropFrameListener : IFrameListener
