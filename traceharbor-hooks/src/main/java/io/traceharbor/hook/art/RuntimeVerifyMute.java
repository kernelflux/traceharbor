package io.traceharbor.hook.art;

import androidx.annotation.Nullable;

import io.traceharbor.hook.HookManager.NativeLibraryLoader;
import io.traceharbor.util.TraceHarborLog;

/**
 * Created by tomystang on 2022/11/16.
 */
public final class RuntimeVerifyMute {
    private static final String TAG = "TraceHarbor.RuntimeVerifyMute";

    public static final RuntimeVerifyMute INSTANCE = new RuntimeVerifyMute();

    private NativeLibraryLoader mNativeLibLoader = null;
    private boolean mNativeLibLoaded = false;

    public RuntimeVerifyMute setNativeLibraryLoader(@Nullable NativeLibraryLoader loader) {
        mNativeLibLoader = loader;
        return this;
    }

    private boolean ensureNativeLibLoaded() {
        synchronized (this) {
            if (mNativeLibLoaded) {
                return true;
            }
            try {
                if (mNativeLibLoader != null) {
                    mNativeLibLoader.loadLibrary("traceharbor-hookcommon");
                    mNativeLibLoader.loadLibrary("traceharbor-artmisc");
                } else {
                    System.loadLibrary("traceharbor-hookcommon");
                    System.loadLibrary("traceharbor-artmisc");
                }
                mNativeLibLoaded = true;
            } catch (Throwable thr) {
                TraceHarborLog.printErrStackTrace(TAG, thr, "Fail to load native library.");
                mNativeLibLoaded = false;
            }
            return mNativeLibLoaded;
        }
    }

    public boolean install() {
        if (!ensureNativeLibLoaded()) {
            return false;
        }
        return nativeInstall();
    }

    private static native boolean nativeInstall();

    private RuntimeVerifyMute() { }
}
