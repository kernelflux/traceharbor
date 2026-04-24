package com.kernelflux.traceharbor.openglleak.statistics;

import com.kernelflux.traceharbor.openglleak.statistics.resource.OpenGLInfo;
import com.kernelflux.traceharbor.openglleak.statistics.resource.ResRecorder;

import java.util.List;

public class CustomizeLeakMonitor {

    private final ResRecorder mResRecorder;

    public CustomizeLeakMonitor() {
        mResRecorder = new ResRecorder();
    }

    public void checkStart() {
        mResRecorder.clear();
        mResRecorder.start();
    }

    public List<OpenGLInfo> checkEnd() {
        mResRecorder.end();
        return mResRecorder.getCurList();
    }

}
