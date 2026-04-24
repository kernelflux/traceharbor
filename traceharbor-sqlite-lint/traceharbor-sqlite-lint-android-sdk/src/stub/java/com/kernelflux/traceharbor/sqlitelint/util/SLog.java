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

package com.kernelflux.traceharbor.sqlitelint.util;

import android.util.Log;
import com.kernelflux.traceharbor.util.TraceHarborLog;

/**
 * Created by liyongjie on 16/9/26.
 */

public class SLog {
    private volatile static SLog mInstance = null;

    public static SLog getInstance() {
        if (mInstance == null) {
            synchronized (SLog.class) {
                if (mInstance == null) {
                    mInstance = new SLog();
                }
            }
        }
        return mInstance;
    }

    public static native void nativeSetLogger(int logLevel);

    public void printLog(int priority, String tag, String msg) {
        switch (priority) {
            case Log.VERBOSE:
                TraceHarborLog.v(tag, msg);
                return;
            case Log.DEBUG:
                TraceHarborLog.d(tag, msg);
                return;
            case Log.INFO:
                TraceHarborLog.i(tag, msg);
                return;
            case Log.WARN:
                TraceHarborLog.w(tag, msg);
                return;
            case Log.ERROR:
            case Log.ASSERT:
                TraceHarborLog.e(tag, msg);
                return;
            default:
                TraceHarborLog.i(tag, msg);
                return;
        }
    }

    public static void e(final String tag, final String format, final Object... args) {
        getInstance().printLog(Log.ERROR, tag, String.format(format, args));
    }

    public static void w(final String tag, final String format, final Object... args) {
        getInstance().printLog(Log.WARN, tag, String.format(format, args));
    }

    public static void i(final String tag, final String format, final Object... args) {
        getInstance().printLog(Log.INFO, tag, String.format(format, args));
    }

    public static void d(final String tag, final String format, final Object... args) {
        getInstance().printLog(Log.DEBUG, tag, String.format(format, args));
    }

    public static void v(final String tag, final String format, final Object... args) {
        getInstance().printLog(Log.VERBOSE, tag, String.format(format, args));
    }

}

