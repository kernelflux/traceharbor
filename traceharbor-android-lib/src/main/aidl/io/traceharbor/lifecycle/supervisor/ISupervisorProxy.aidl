// ISupervisorProxy.aidl
package io.traceharbor.lifecycle.supervisor;

// Declare any non-default types here with import statements

import io.traceharbor.lifecycle.supervisor.ProcessToken;
import io.traceharbor.lifecycle.supervisor.ISubordinateProxy;

interface ISupervisorProxy {
    void registerSubordinate(in ProcessToken[] tokens, in ISubordinateProxy subordinateProxy);

    void onStateChanged(in ProcessToken token);

    void onSceneChanged(in String scene);

    void onProcessKilled(in ProcessToken token);
    void onProcessRescuedFromKill(in ProcessToken token);
    void onProcessKillCanceled(in ProcessToken token);

    String getRecentScene();
}