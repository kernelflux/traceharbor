package com.kernelflux.traceharbor.hook.art

import com.kernelflux.traceharbor.hook.HookManager.NativeLibraryLoader
import com.kernelflux.traceharbor.util.TraceHarborLog

/**
 * Created by tomystang on 2022/11/16.
 */
object RuntimeVerifyMute {
    private const val TAG = "TraceHarbor.RuntimeVerifyMute"

    private var nativeLibLoader: NativeLibraryLoader? = null
    private var nativeLibLoaded = false

    fun setNativeLibraryLoader(loader: NativeLibraryLoader?): RuntimeVerifyMute {
        nativeLibLoader = loader
        return this
    }

    private fun ensureNativeLibLoaded(): Boolean {
        synchronized(this) {
            if (nativeLibLoaded) {
                return true
            }
            try {
                if (nativeLibLoader != null) {
                    nativeLibLoader?.apply {
                        loadLibrary("traceharbor-hookcommon")
                        loadLibrary("traceharbor-artmisc")
                    }
                } else {
                    System.loadLibrary("traceharbor-hookcommon")
                    System.loadLibrary("traceharbor-artmisc")
                }
                nativeLibLoaded = true
            } catch (thr: Throwable) {
                TraceHarborLog.printErrStackTrace(TAG, thr, "Fail to load native library.")
                nativeLibLoaded = false
            }
            return nativeLibLoaded
        }
    }

    fun install(): Boolean {
        if (!ensureNativeLibLoaded()) {
            return false
        }
        return nativeInstall()
    }

    private external fun nativeInstall(): Boolean
}

