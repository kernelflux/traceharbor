package com.kernelflux.traceharbor.openglleak.statistics;

import com.kernelflux.traceharbor.openglleak.statistics.resource.OpenGLInfo;

public class BindCenter {

    private static final String TAG = "traceharbor.BindCenter";

    private static final BindCenter mInstance = new BindCenter();

    public static BindCenter getInstance() {
        return mInstance;
    }

    private BindCenter() {
    }

    public void glBindResource(OpenGLInfo.TYPE type, int target, long eglContextId, OpenGLInfo info) {
        BindMap.getInstance().putBindInfo(type, target, eglContextId, info);
    }


    public OpenGLInfo findCurrentResourceIdByTarget(OpenGLInfo.TYPE type, long eglContextId, int target) {
        return BindMap.getInstance().getBindInfo(type, eglContextId, target);
    }


}
