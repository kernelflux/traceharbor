/*
 * Tencent is pleased to support the open source community by making wechat-matrix available.
 * Copyright (C) 2018 THL A29 Limited, a Tencent company. All rights reserved.
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.traceharbor.trace;

import android.app.Application;
import android.os.Build;
import android.os.Looper;

import io.traceharbor.plugin.Plugin;
import io.traceharbor.plugin.PluginListener;
import io.traceharbor.trace.config.SharePluginInfo;
import io.traceharbor.trace.config.TraceConfig;
import io.traceharbor.trace.core.AppMethodBeat;
import io.traceharbor.trace.core.UIThreadMonitor;
import io.traceharbor.trace.tracer.EvilMethodTracer;
import io.traceharbor.trace.tracer.FrameTracer;
import io.traceharbor.trace.tracer.IdleHandlerLagTracer;
import io.traceharbor.trace.tracer.LooperAnrTracer;
import io.traceharbor.trace.tracer.SignalAnrTracer;
import io.traceharbor.trace.tracer.StartupTracer;
import io.traceharbor.trace.tracer.TouchEventLagTracer;
import io.traceharbor.util.TraceHarborHandlerThread;
import io.traceharbor.util.TraceHarborLog;

/**
 * Created by caichongyang on 2017/5/20.
 */
public class TracePlugin extends Plugin {
    private static final String TAG = "TraceHarbor.TracePlugin";

    private final TraceConfig traceConfig;
    private EvilMethodTracer evilMethodTracer;
    private StartupTracer startupTracer;
    private FrameTracer frameTracer;
    private LooperAnrTracer looperAnrTracer;
    private SignalAnrTracer signalAnrTracer;
    private IdleHandlerLagTracer idleHandlerLagTracer;
    private TouchEventLagTracer touchEventLagTracer;
    private final int sdkInt = Build.VERSION.SDK_INT;

    public TracePlugin(TraceConfig config) {
        this.traceConfig = config;
    }

    @Override
    public void init(Application app, PluginListener listener) {
        super.init(app, listener);
        TraceHarborLog.i(TAG, "trace plugin init, trace config: %s", traceConfig.toString());
        if (sdkInt < Build.VERSION_CODES.JELLY_BEAN) {
            TraceHarborLog.e(TAG, "[FrameBeat] API is low Build.VERSION_CODES.JELLY_BEAN(16), TracePlugin is not supported");
            unSupportPlugin();
            return;
        }

        looperAnrTracer = new LooperAnrTracer(traceConfig);

        frameTracer = new FrameTracer(traceConfig);

        evilMethodTracer = new EvilMethodTracer(traceConfig);

        startupTracer = new StartupTracer(traceConfig);
    }

    @Override
    public void start() {
        super.start();
        if (!isSupported()) {
            TraceHarborLog.w(TAG, "[start] Plugin is unSupported!");
            return;
        }
        TraceHarborLog.w(TAG, "start!");
        Runnable runnable = new Runnable() {
            @Override
            public void run() {

                if (sdkInt < Build.VERSION_CODES.N && willUiThreadMonitorRunning(traceConfig)) {
                    if (!UIThreadMonitor.getMonitor().isInit()) {
                        try {
                            UIThreadMonitor.getMonitor().init(traceConfig);
                        } catch (java.lang.RuntimeException e) {
                            TraceHarborLog.e(TAG, "[start] RuntimeException:%s", e);
                            return;
                        }
                    }
                }

                if (traceConfig.isAppMethodBeatEnable()) {
                    AppMethodBeat.getInstance().onStart();
                } else {
                    AppMethodBeat.getInstance().forceStop();
                }

                UIThreadMonitor.getMonitor().onStart();

                if (traceConfig.isAnrTraceEnable()) {
                    looperAnrTracer.onStartTrace();
                }

                if (traceConfig.isIdleHandlerTraceEnable()) {
                    idleHandlerLagTracer = new IdleHandlerLagTracer(traceConfig);
                    idleHandlerLagTracer.onStartTrace();
                }

                if (traceConfig.isTouchEventTraceEnable()) {
                    touchEventLagTracer = new TouchEventLagTracer(traceConfig);
                    touchEventLagTracer.onStartTrace();
                }

                if (traceConfig.isSignalAnrTraceEnable()) {
                    if (!SignalAnrTracer.hasInstance) {
                        signalAnrTracer = new SignalAnrTracer(traceConfig);
                        signalAnrTracer.onStartTrace();
                    }
                }

                if (traceConfig.isFPSEnable()) {
                    frameTracer.onStartTrace();
                }

                if (traceConfig.isEvilMethodTraceEnable()) {
                    evilMethodTracer.onStartTrace();
                }

                if (traceConfig.isStartupEnable()) {
                    startupTracer.onStartTrace();
                }


            }
        };

        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            runnable.run();
        } else {
            TraceHarborLog.w(TAG, "start TracePlugin in Thread[%s] but not in mainThread!", Thread.currentThread().getId());
            TraceHarborHandlerThread.getDefaultMainHandler().post(runnable);
        }
    }

    @Override
    public void stop() {
        super.stop();
        if (!isSupported()) {
            TraceHarborLog.w(TAG, "[stop] Plugin is unSupported!");
            return;
        }
        TraceHarborLog.w(TAG, "stop!");
        Runnable runnable = new Runnable() {
            @Override
            public void run() {

                AppMethodBeat.getInstance().onStop();

                UIThreadMonitor.getMonitor().onStop();

                looperAnrTracer.onCloseTrace();

                frameTracer.onCloseTrace();

                evilMethodTracer.onCloseTrace();

                startupTracer.onCloseTrace();

                if (signalAnrTracer != null) {
                    signalAnrTracer.onCloseTrace();
                }

                if (idleHandlerLagTracer != null) {
                    idleHandlerLagTracer.onCloseTrace();
                }
            }
        };

        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            runnable.run();
        } else {
            TraceHarborLog.w(TAG, "stop TracePlugin in Thread[%s] but not in mainThread!", Thread.currentThread().getId());
            TraceHarborHandlerThread.getDefaultMainHandler().post(runnable);
        }

    }

    @Override
    public void onForeground(boolean isForeground) {
        super.onForeground(isForeground);
        if (!isSupported()) {
            return;
        }

        if (frameTracer != null) {
            frameTracer.onForeground(isForeground);
        }

        if (looperAnrTracer != null) {
            looperAnrTracer.onForeground(isForeground);
        }

        if (evilMethodTracer != null) {
            evilMethodTracer.onForeground(isForeground);
        }

        if (startupTracer != null) {
            startupTracer.onForeground(isForeground);
        }

    }

    private boolean willUiThreadMonitorRunning(TraceConfig traceConfig) {
        return traceConfig.isEvilMethodTraceEnable() || traceConfig.isAnrTraceEnable() || traceConfig.isFPSEnable();
    }

    @Override
    public void destroy() {
        super.destroy();
    }

    @Override
    public String getTag() {
        return SharePluginInfo.TAG_PLUGIN;
    }

    public FrameTracer getFrameTracer() {
        return frameTracer;
    }

    public AppMethodBeat getAppMethodBeat() {
        return AppMethodBeat.getInstance();
    }

    public LooperAnrTracer getLooperAnrTracer() {
        return looperAnrTracer;
    }

    public EvilMethodTracer getEvilMethodTracer() {
        return evilMethodTracer;
    }

    public StartupTracer getStartupTracer() {
        return startupTracer;
    }

    public UIThreadMonitor getUIThreadMonitor() {
        if (UIThreadMonitor.getMonitor().isInit()) {
            return UIThreadMonitor.getMonitor();
        } else {
            return null;
        }
    }

    public TraceConfig getTraceConfig() {
        return traceConfig;
    }
}
