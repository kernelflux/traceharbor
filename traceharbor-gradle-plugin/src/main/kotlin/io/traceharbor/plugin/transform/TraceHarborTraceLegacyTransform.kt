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

package io.traceharbor.plugin.transform

import io.traceharbor.javalib.util.Log
import io.traceharbor.trace.extension.TraceHarborTraceExtension
import org.gradle.api.Project

class TraceHarborTraceLegacyTransform(
        private val project: Project
) {

    companion object {
        const val TAG = "TraceHarbor.TraceLegacyTransform"

        @Suppress("UNUSED_PARAMETER")
        fun inject(extension: TraceHarborTraceExtension, project: Project, variant: Any) {
            Log.w(TAG, "Legacy TraceHarbor trace transform injection is disabled in the AGP 8 migration branch.")
        }
    }
}
