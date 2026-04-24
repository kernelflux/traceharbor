package com.kernelflux.traceharbor.fd

import androidx.annotation.Keep
import com.kernelflux.traceharbor.util.TraceHarborLog

/**
 * Created by Yves on 2019-07-22
 *
 * Static facade — Java callers use `FDDumpBridge.getFdPathName(...)`,
 * `FDDumpBridge.getFDLimit()` etc., so everything stays in the
 * companion object with `@JvmStatic` to preserve bytecode.
 *
 * The two native methods keep their canonical
 * `Java_com_kernelflux_traceharbor_fd_FDDumpBridge_<name>` JNI symbols
 * because JNI binding is by `package + class + method` name and we
 * preserve all three.
 */
class FDDumpBridge private constructor() {

    companion object {
        private const val TAG = "FDDumpBridge"

        @JvmStatic
        private var initialized: Boolean = false

        init {
            try {
                System.loadLibrary("traceharbor-fd")
                initialized = true
            } catch (e: Throwable) {
                TraceHarborLog.printErrStackTrace(TAG, e, "")
                initialized = false
            }
        }

        @JvmStatic
        fun getFdPathName(path: String): String {
            if (!initialized) {
                return path
            }
            return getFdPathNameNative(path)
        }

        @JvmStatic
        @Keep
        external fun getFdPathNameNative(path: String): String

        @JvmStatic
        @Keep
        external fun getFDLimit(): Int
    }
}
