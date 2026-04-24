package com.kernelflux.traceharborsample;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

/**
 * Empty service started by the "Start Supervisor" button to mirror the upstream sample's
 * StubService entry. The TraceHarbor process supervisor lifecycle is initialised by
 * {@code TraceHarbor.init()} in {@link DemoApplication}; this service exists only so the
 * button has something to start.
 */
public class SupervisorStubService extends Service {

    @Override
    public void onCreate() {
        super.onCreate();
        IssueRecorder.appendEvent("supervisor", "SupervisorStubService.onCreate()");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
