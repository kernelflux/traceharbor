package com.kernelflux.traceharbor;

import android.app.Activity;
import android.app.Application;

import com.kernelflux.traceharbor.listeners.IAppForeground;
import com.kernelflux.traceharbor.lifecycle.owners.ProcessUILifecycleOwner;
import com.kernelflux.traceharbor.util.TraceHarborUtil;

/**
 * use {@link ProcessUILifecycleOwner} instead
 */
@Deprecated
public enum AppActiveTraceHarborDelegate {

    INSTANCE;

    private static final String TAG = "TraceHarbor.AppActiveDelegate";

    public void init(Application application) {
    }

    public String getCurrentFragmentName() {
        return ProcessUILifecycleOwner.INSTANCE.getCurrentFragmentName();
    }

    /**
     * must set after {@link Activity#onStart()}
     *
     * @param fragmentName
     */
    public void setCurrentFragmentName(String fragmentName) {
        ProcessUILifecycleOwner.INSTANCE.setCurrentFragmentName(fragmentName);
    }

    public String getVisibleScene() {
        return ProcessUILifecycleOwner.INSTANCE.getVisibleScene();
    }

    @Deprecated
    public boolean isAppForeground() {
        return ProcessUILifecycleOwner.INSTANCE.isProcessForeground();
    }

    /**
     * use {@link ProcessUILifecycleOwner} instead:
     * @param listener
     */
    @Deprecated
    public void addListener(IAppForeground listener) {
        ProcessUILifecycleOwner.INSTANCE.addListener(listener);
    }

    /**
     * use {@link ProcessUILifecycleOwner} instead:
     * @param listener
     */
    @Deprecated
    public void removeListener(IAppForeground listener) {
        ProcessUILifecycleOwner.INSTANCE.removeListener(listener);
    }

    @Deprecated
    public static String getTopActivityName() {
        return TraceHarborUtil.getTopActivityName();
    }
}
