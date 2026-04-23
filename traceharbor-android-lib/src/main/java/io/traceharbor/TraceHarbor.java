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

package io.traceharbor;

import android.app.Application;

import io.traceharbor.lifecycle.TraceHarborLifecycleConfig;
import io.traceharbor.lifecycle.TraceHarborLifecycleOwnerInitializer;
import io.traceharbor.lifecycle.supervisor.ProcessSupervisor;
import io.traceharbor.plugin.DefaultPluginListener;
import io.traceharbor.plugin.Plugin;
import io.traceharbor.plugin.PluginListener;
import io.traceharbor.util.TraceHarborLog;

import java.util.HashSet;

/**
 * Created by zhangshaowen on 17/5/17.
 */

public class TraceHarbor {
    private static final String TAG = "TraceHarbor.TraceHarbor";

    private static volatile TraceHarbor sInstance;

    private final HashSet<Plugin> plugins;
    private final Application     application;

    private TraceHarbor(Application app, PluginListener listener, HashSet<Plugin> plugins, TraceHarborLifecycleConfig config) {
        this.application = app;
        this.plugins = plugins;
        TraceHarborLifecycleOwnerInitializer.init(app, config);
        ProcessSupervisor.INSTANCE.init(app, config.getSupervisorConfig());
        for (Plugin plugin : plugins) {
            plugin.init(application, listener);
        }
    }

    public static void setLogIml(TraceHarborLog.TraceHarborLogImp imp) {
        TraceHarborLog.setTraceHarborLogImp(imp);
    }

    public static boolean isInstalled() {
        return sInstance != null;
    }

    public static TraceHarbor init(TraceHarbor matrix) {
        if (matrix == null) {
            throw new RuntimeException("TraceHarbor init, TraceHarbor should not be null.");
        }
        synchronized (TraceHarbor.class) {
            if (sInstance == null) {
                sInstance = matrix;
            } else {
                TraceHarborLog.e(TAG, "TraceHarbor instance is already set. this invoking will be ignored");
            }
        }
        return sInstance;
    }

    public static TraceHarbor with() {
        if (sInstance == null) {
            throw new RuntimeException("you must init TraceHarbor sdk first");
        }
        return sInstance;
    }

    public void startAllPlugins() {
        for (Plugin plugin : plugins) {
            plugin.start();
        }
    }

    public void stopAllPlugins() {
        for (Plugin plugin : plugins) {
            plugin.stop();
        }
    }

    public void destroyAllPlugins() {
        for (Plugin plugin : plugins) {
            plugin.destroy();
        }
    }

    public Application getApplication() {
        return application;
    }

    public HashSet<Plugin> getPlugins() {
        return plugins;
    }

    public Plugin getPluginByTag(String tag) {
        for (Plugin plugin : plugins) {
            if (plugin.getTag().equals(tag)) {
                return plugin;
            }
        }
        return null;
    }

    public <T extends Plugin> T getPluginByClass(Class<T> pluginClass) {
        String className = pluginClass.getName();
        for (Plugin plugin : plugins) {
            if (plugin.getClass().getName().equals(className)) {
                return (T) plugin;
            }
        }
        return null;
    }

    public static class Builder {
        private final Application application;

        private PluginListener   pluginListener;

        private TraceHarborLifecycleConfig mLifecycleConfig = new TraceHarborLifecycleConfig(); // default config

//        private SupervisorConfig supervisorConfig;
//        private boolean          enableFgServiceMonitor;
//        private boolean          enableOverlayWindowMonitor;

        private final HashSet<Plugin> plugins = new HashSet<>();

        public Builder(Application app) {
            if (app == null) {
                throw new RuntimeException("matrix init, application is null");
            }
            this.application = app;
        }

        public Builder plugin(Plugin plugin) {
            String tag = plugin.getTag();
            for (Plugin exist : plugins) {
                if (tag.equals(exist.getTag())) {
                    throw new RuntimeException(String.format("plugin with tag %s is already exist", tag));
                }
            }
            plugins.add(plugin);
            return this;
        }

        public Builder pluginListener(PluginListener pluginListener) {
            this.pluginListener = pluginListener;
            return this;
        }

        public Builder matrixLifecycleConfig(TraceHarborLifecycleConfig config) {
            this.mLifecycleConfig = config;
            return this;
        }

        public TraceHarbor build() {
            if (pluginListener == null) {
                pluginListener = new DefaultPluginListener(application);
            }
            return new TraceHarbor(application, pluginListener, plugins, mLifecycleConfig);
        }

    }
}
