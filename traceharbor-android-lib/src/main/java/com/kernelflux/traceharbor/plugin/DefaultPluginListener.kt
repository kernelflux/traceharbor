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

package com.kernelflux.traceharbor.plugin

import android.content.Context
import com.kernelflux.traceharbor.report.Issue
import com.kernelflux.traceharbor.util.TraceHarborLog

open class DefaultPluginListener(@Suppress("unused") private val context: Context) : PluginListener {

    override fun onInit(plugin: Plugin) {
        TraceHarborLog.i(TAG, "%s plugin is inited", plugin.tag)
    }

    override fun onStart(plugin: Plugin) {
        TraceHarborLog.i(TAG, "%s plugin is started", plugin.tag)
    }

    override fun onStop(plugin: Plugin) {
        TraceHarborLog.i(TAG, "%s plugin is stopped", plugin.tag)
    }

    override fun onDestroy(plugin: Plugin) {
        TraceHarborLog.i(TAG, "%s plugin is destroyed", plugin.tag)
    }

    override fun onReportIssue(issue: Issue) {
        // Original Java tolerated a null Issue here. The interface signature is non-null,
        // but Java callers may still pass null at runtime — keep a defensive toString.
        TraceHarborLog.i(TAG, "report issue content: %s", issue.toString())
    }

    companion object {
        private const val TAG = "TraceHarbor.DefaultPluginListener"
    }
}
