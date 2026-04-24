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

package com.kernelflux.traceharbor.plugin.task

import com.kernelflux.traceharbor.javalib.util.Log
import com.kernelflux.traceharbor.plugin.compat.AGPVersion
import com.kernelflux.traceharbor.plugin.compat.VersionsCompat
import com.kernelflux.traceharbor.plugin.extension.TraceHarborRemoveUnusedResExtension
import com.kernelflux.traceharbor.plugin.trace.TraceHarborTraceAgp8Registrar
import com.kernelflux.traceharbor.trace.extension.TraceHarborTraceExtension
import org.gradle.api.GradleException
import org.gradle.api.Project

class TraceHarborTasksManager {

    companion object {
        const val TAG = "TraceHarbor.TasksManager"
    }

    fun createTraceHarborTasks(
        android: Any,
        project: Project,
        traceExtension: TraceHarborTraceExtension,
        removeUnusedResourcesExtension: TraceHarborRemoveUnusedResExtension
    ) {
        if (VersionsCompat.lessThan(AGPVersion.AGP_8_0_0)) {
            throw GradleException(
                "TraceHarbor requires AGP 8.0+ (current: ${VersionsCompat.androidGradlePluginVersion}). " +
                        "Pre-AGP-8 Transform path was removed in 2.1.0."
            )
        }

        // onVariants must be registered while AndroidComponentsExtension is being configured (synchronously
        // from apply()), not from afterEvaluate, or AGP fails with "It is too late to add actions".
        TraceHarborTraceAgp8Registrar.registerIfEnabled(project, traceExtension)

        if (removeUnusedResourcesExtension.enable) {
            // removeUnusedResources still uses the legacy applicationVariants/BaseVariant path. It is
            // opt-in (default disabled) and pending its own AGP 8 migration. Loading the registrar
            // class is gated by this branch so BaseVariant is not pulled in for default builds.
            Log.w(
                TAG,
                "removeUnusedResources is enabled and uses the legacy variant API on AGP 8. This is a known follow-up; functionality may be limited."
            )
            TraceHarborRemoveUnusedRegistrar.register(android, project, removeUnusedResourcesExtension)
        }
    }
}
