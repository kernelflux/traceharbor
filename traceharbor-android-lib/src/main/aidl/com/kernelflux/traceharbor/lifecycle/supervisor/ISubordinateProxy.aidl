// ISubordinateProxy.aidl
package com.kernelflux.traceharbor.lifecycle.supervisor;

// Declare any non-default types here with import statements
import com.kernelflux.traceharbor.util.MemInfo;

interface ISubordinateProxy {
    void dispatchState(in String scene, in String stateName, in boolean state);
    void dispatchKill(in String scene, in String targetProcess, in int targetPid);
    void dispatchDeath(in String scene, in String targetProcess, in int targetPid, in boolean isLruKill);

    MemInfo getMemInfo();
}