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

package com.kernelflux.traceharbor.trace;

/**
 * Created by caichongyang on 2017/6/20.
 */
public class TraceBuildConstants {

    public final static String MATRIX_TRACE_CLASS = "com/kernelflux/traceharbor/trace/core/AppMethodBeat";
    public final static String MATRIX_TRACE_ON_WINDOW_FOCUS_METHOD = "onWindowFocusChanged";
    public final static String MATRIX_TRACE_ATTACH_BASE_CONTEXT = "attachBaseContext";
    public final static String MATRIX_TRACE_ATTACH_BASE_CONTEXT_ARGS = "(Landroid/content/Context;)V";
    public final static String MATRIX_TRACE_APPLICATION_ON_CREATE = "onCreate";
    public final static String MATRIX_TRACE_APPLICATION_ON_CREATE_ARGS = "()V";
    public final static String MATRIX_TRACE_ACTIVITY_CLASS = "android/app/Activity";
    public final static String MATRIX_TRACE_V7_ACTIVITY_CLASS = "android/support/v7/app/AppCompatActivity";
    public final static String MATRIX_TRACE_V4_ACTIVITY_CLASS = "android/support/v4/app/FragmentActivity";
    public final static String MATRIX_TRACE_ANDROIDX_ACTIVITY_CLASS = "androidx/appcompat/app/AppCompatActivity";
    public final static String MATRIX_TRACE_APPLICATION_CLASS = "android/app/Application";
    public final static String MATRIX_TRACE_METHOD_BEAT_CLASS = "com/kernelflux/traceharbor/trace/core/AppMethodBeat";
    public final static String MATRIX_TRACE_ON_WINDOW_FOCUS_METHOD_ARGS = "(Z)V";
    public static final String[] UN_TRACE_CLASS = {"R.class", "R$", "Manifest", "BuildConfig"};

    // Block list applied during method collection / tracing.
    //
    //   - android/* — platform classes, not in our DEX, must not be tracked.
    //   - com/kernelflux/traceharbor/* — the TraceHarbor SDK itself; instrumenting our own runtime
    //     would skew its own bookkeeping and risks re-entrancy from AppMethodBeat.
    //
    // Convention: TraceHarbor reserves the entire `com.kernelflux.traceharbor.*` namespace for the
    // SDK. Consumers MUST publish their app/library code under their own root package
    // (e.g. com.acme.app), not under com.kernelflux.traceharbor.*; otherwise the default block
    // below will silently disable instrumentation for them. The bundled sample lives
    // under `com.kernelflux.traceharborsample` for exactly this reason.
    public final static String DEFAULT_BLOCK_TRACE =
                    "[package]\n"
                    + "-keeppackage android/\n"
                    + "-keeppackage com/kernelflux/traceharbor/\n";

    private static final int METHOD_ID_MAX = 0xFFFFF;
    public static final int METHOD_ID_DISPATCH = METHOD_ID_MAX - 1;
}
