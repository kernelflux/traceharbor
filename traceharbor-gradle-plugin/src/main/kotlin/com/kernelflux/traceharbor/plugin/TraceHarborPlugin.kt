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

import com.kernelflux.traceharbor.javalib.util.Log
import com.kernelflux.traceharbor.plugin.compat.AGPVersion
import com.kernelflux.traceharbor.plugin.compat.VersionsCompat
import com.kernelflux.traceharbor.plugin.extension.TraceHarborExtension
import com.kernelflux.traceharbor.plugin.extension.TraceHarborRemoveUnusedResExtension
import com.kernelflux.traceharbor.plugin.task.TraceHarborRemoveUnusedRegistrar
import com.kernelflux.traceharbor.plugin.trace.TraceHarborTraceRegistrar
import com.kernelflux.traceharbor.trace.extension.TraceHarborTraceExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware

class TraceHarborPlugin : Plugin<Project> {
    companion object {
        const val TAG = "TraceHarbor.Plugin"
    }

    override fun apply(project: Project) {
        val traceHarbor = project.extensions.create("traceHarbor", TraceHarborExtension::class.java)
        val traceExtension = (traceHarbor as ExtensionAware).extensions.create(
            "trace",
            TraceHarborTraceExtension::class.java
        )
        val removeUnusedResourcesExtension = traceHarbor.extensions.create(
            "removeUnusedResources",
            TraceHarborRemoveUnusedResExtension::class.java
        )

        if (!project.plugins.hasPlugin("com.android.application")) {
            throw GradleException("TraceHarbor Plugin, Android Application plugin required.")
        }

        if (VersionsCompat.lessThan(AGPVersion.AGP_8_0_0)) {
            throw GradleException(
                "TraceHarbor requires AGP 8.0+ (current: ${VersionsCompat.androidGradlePluginVersion}). " +
                        "Pre-AGP-8 Transform path was removed in 2.1.0."
            )
        }

        val androidExtension = project.extensions.findByName("android")
            ?: run {
                Log.w(
                    TAG,
                    "TraceHarbor plugin could not obtain the Android extension. TraceHarbor tasks are limited."
                )
                return
            }

        project.afterEvaluate {
            Log.setLogLevel(traceHarbor.logLevel)
        }


        // onVariants must be registered while AndroidComponentsExtension is being configured (synchronously
        // from apply()), not from afterEvaluate, or AGP fails with "It is too late to add actions".
        TraceHarborTraceRegistrar.registerIfEnabled(project, traceExtension)

        if (removeUnusedResourcesExtension.enable) {
            // removeUnusedResources still uses the legacy applicationVariants/BaseVariant path. It is
            // opt-in (default disabled) and pending its own AGP 8 migration. Loading the registrar
            // class is gated by this branch so BaseVariant is not pulled in for default builds.
            Log.w(
                TAG,
                "removeUnusedResources is enabled and uses the legacy variant API on AGP 8. This is a known follow-up; functionality may be limited."
            )
            TraceHarborRemoveUnusedRegistrar.register(
                androidExtension,
                project,
                removeUnusedResourcesExtension
            )
        }
    }
}
