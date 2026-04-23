package io.traceharbor.fd;


import androidx.annotation.Keep;

import io.traceharbor.util.TraceHarborLog;

/**
 * Created by Yves on 2019-07-22
 */
public class FDDumpBridge {

    private static final String TAG = "FDDumpBridge";

    private static boolean initialized;

    static {
        try {
            System.loadLibrary("traceharbor-fd");
            initialized = true;
        } catch (Throwable e) {
            TraceHarborLog.printErrStackTrace(TAG, e, "");
            initialized = false;
        }
    }

    public static String getFdPathName(String path) {
        if (!initialized) {
            return path;
        }
        return getFdPathNameNative(path);
    }

    @Keep
    public static native String getFdPathNameNative(String path);

    @Keep
    public static native int getFDLimit();
}
