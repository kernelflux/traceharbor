// ISubordinateProxy.aidl
package io.traceharbor.lifecycle.supervisor;

// Declare any non-default types here with import statements
import io.traceharbor.util.MemInfo;

interface ISubordinateProxy {
    void dispatchState(in String scene, in String stateName, in boolean state);
    void dispatchKill(in String scene, in String targetProcess, in int targetPid);
    void dispatchDeath(in String scene, in String targetProcess, in int targetPid, in boolean isLruKill);

    MemInfo getMemInfo();
}