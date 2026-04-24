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

package com.kernelflux.traceharbor.dynamicconfig

interface IDynamicConfig {

    enum class ExptEnum {
        // trace
        clicfg_traceharbor_trace_fps_enable,
        clicfg_traceharbor_trace_care_scene_set,
        clicfg_traceharbor_trace_fps_time_slice,
        clicfg_traceharbor_trace_evil_method_threshold,

        clicfg_traceharbor_fps_dropped_normal,
        clicfg_traceharbor_fps_dropped_middle,
        clicfg_traceharbor_fps_dropped_high,
        clicfg_traceharbor_fps_dropped_frozen,
        clicfg_traceharbor_trace_evil_method_enable,
        clicfg_traceharbor_trace_anr_enable,
        clicfg_traceharbor_trace_startup_enable,

        clicfg_traceharbor_trace_app_start_up_threshold,
        clicfg_traceharbor_trace_warm_app_start_up_threshold,


        // io
        clicfg_traceharbor_io_file_io_main_thread_enable,
        clicfg_traceharbor_io_main_thread_enable_threshold,
        clicfg_traceharbor_io_small_buffer_enable,
        clicfg_traceharbor_io_small_buffer_threshold,
        clicfg_traceharbor_io_small_buffer_operator_times,
        clicfg_traceharbor_io_repeated_read_enable,
        clicfg_traceharbor_io_repeated_read_threshold,
        clicfg_traceharbor_io_closeable_leak_enable,

        // battery
        clicfg_traceharbor_battery_detect_wake_lock_enable,
        clicfg_traceharbor_battery_record_wake_lock_enable,
        clicfg_traceharbor_battery_wake_lock_hold_time_threshold,
        clicfg_traceharbor_battery_wake_lock_1h_acquire_cnt_threshold,
        clicfg_traceharbor_battery_wake_lock_1h_hold_time_threshold,
        clicfg_traceharbor_battery_detect_alarm_enable,
        clicfg_traceharbor_battery_record_alarm_enable,
        clicfg_traceharbor_battery_alarm_1h_trigger_cnt_threshold,
        clicfg_traceharbor_battery_wake_up_alarm_1h_trigger_cnt_threshold,


        // memory
        clicfg_traceharbor_memory_middle_min_span,
        clicfg_traceharbor_memory_high_min_span,
        clicfg_traceharbor_memory_threshold,
        clicfg_traceharbor_memory_special_activities,

        // resource
        clicfg_traceharbor_resource_detect_interval_millis,
        clicfg_traceharbor_resource_detect_interval_millis_bg,
        clicfg_traceharbor_resource_max_detect_times,
        clicfg_traceharbor_resource_dump_hprof_enable,

        // thread
        clicfg_traceharbor_thread_check_time,
        clicfg_traceharbor_thread_check_bg_time,
        clicfg_traceharbor_thread_limit_count,
        clicfg_traceharbor_thread_report_time,
        clicfg_traceharbor_thread_contain_sys,
        clicfg_traceharbor_thread_filter_thread_set,
    }

    fun get(key: String, defStr: String): String

    fun get(key: String, defInt: Int): Int

    fun get(key: String, defLong: Long): Long

    fun get(key: String, defBool: Boolean): Boolean

    fun get(key: String, defFloat: Float): Float
}
