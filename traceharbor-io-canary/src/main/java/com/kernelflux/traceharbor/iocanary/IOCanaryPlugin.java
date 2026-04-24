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

package com.kernelflux.traceharbor.iocanary;

import android.app.Application;

import com.kernelflux.traceharbor.iocanary.config.IOConfig;
import com.kernelflux.traceharbor.iocanary.config.SharePluginInfo;
import com.kernelflux.traceharbor.iocanary.core.IOCanaryCore;
import com.kernelflux.traceharbor.iocanary.util.IOCanaryUtil;
import com.kernelflux.traceharbor.plugin.Plugin;
import com.kernelflux.traceharbor.plugin.PluginListener;

/**
 * Core logic for hookers, detectors and reporter
 * <p>
 * Logic stream like:
 * hooker -> detector -> reporter
 * <p>
 * @author liyongjie
 *         Created by liyongjie on 2017/6/29.
 */

public class IOCanaryPlugin extends Plugin {
    private static final String TAG = "TraceHarbor.IOCanaryPlugin";

    private final IOConfig     mIOConfig;
    private IOCanaryCore mCore;

//    public IOCanaryPlugin() {
//        mIOConfig = IOConfig.DEFAULT;
//    }

    public IOCanaryPlugin(IOConfig ioConfig) {
        mIOConfig = ioConfig;
    }

    @Override
    public void init(Application app, PluginListener listener) {
        super.init(app, listener);
        IOCanaryUtil.setPackageName(app);
        mCore = new IOCanaryCore(this);
    }

    @Override
    public void start() {
        super.start();
        mCore.start();
    }

    @Override
    public void stop() {
        super.stop();
        mCore.stop();
    }

    public IOConfig getConfig() {
        return mIOConfig;
    }

    @Override
    public void destroy() {
        super.destroy();
    }

    @Override
    public String getTag() {
        return SharePluginInfo.TAG_PLUGIN;
    }
}
