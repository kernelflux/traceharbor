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

package io.traceharbor.plugin.task

import io.traceharbor.javalib.util.Log
import io.traceharbor.javalib.util.Util
import io.traceharbor.plugin.compat.CreationConfig
import io.traceharbor.plugin.compat.TraceHarborTraceCompat
import io.traceharbor.plugin.extension.TraceHarborRemoveUnusedResExtension
import io.traceharbor.trace.extension.TraceHarborTraceExtension
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

class TraceHarborTasksManager {

    companion object {
        const val TAG = "TraceHarbor.TasksManager"
    }

    fun createTraceHarborTasks(android: Any,
                          project: Project,
                          traceExtension: TraceHarborTraceExtension,
                          removeUnusedResourcesExtension: TraceHarborRemoveUnusedResExtension) {

        createTraceHarborTraceTask(android, project, traceExtension)

        createRemoveUnusedResourcesTask(android, project, removeUnusedResourcesExtension)
    }

    private fun createTraceHarborTraceTask(
            android: Any,
            project: Project,
            traceExtension: TraceHarborTraceExtension) {
        TraceHarborTraceCompat().inject(android, project, traceExtension)
    }
    private fun createRemoveUnusedResourcesTask(
            android: Any,
            project: Project,
            removeUnusedResourcesExtension: TraceHarborRemoveUnusedResExtension) {

        project.afterEvaluate {

            if (!removeUnusedResourcesExtension.enable) {
                return@afterEvaluate
            }

            val variants = android.javaClass.methods.firstOrNull {
                it.name == "getApplicationVariants" && it.parameterCount == 0
            }?.invoke(android) as? Iterable<*>

            if (variants == null) {
                Log.w(TAG, "Android extension does not expose applicationVariants. removeUnusedResources is disabled for now.")
                return@afterEvaluate
            }

            variants.forEach { rawVariant ->
                val variant = rawVariant as? com.android.build.gradle.api.BaseVariant ?: return@forEach
                if (Util.isNullOrNil(removeUnusedResourcesExtension.variant) ||
                        variant.name.equals(removeUnusedResourcesExtension.variant, true)) {
                    Log.i(TAG, "RemoveUnusedResourcesExtension: %s", removeUnusedResourcesExtension)

                    val removeUnusedResourcesTaskProvider = if (removeUnusedResourcesExtension.v2) {
                        val action = RemoveUnusedResourcesTaskV2.CreationAction(
                                CreationConfig(variant, project), removeUnusedResourcesExtension
                        )
                        project.tasks.register(action.name, action.type, action)
                    } else {
                        val action = RemoveUnusedResourcesTask.CreationAction(
                                CreationConfig(variant, project), removeUnusedResourcesExtension
                        )
                        project.tasks.register(action.name, action.type, action)
                    }

                    CreationConfig.getAssembleTaskDependency(project, variant)?.let { assembleDependency ->
                        when (assembleDependency) {
                            is TaskProvider<*> -> assembleDependency.configure {
                                it.dependsOn(removeUnusedResourcesTaskProvider)
                            }
                            is Task -> {
                                assembleDependency.dependsOn(removeUnusedResourcesTaskProvider)
                            }
                        }
                    }

                    removeUnusedResourcesTaskProvider.configure {
                        val packageDependency = CreationConfig.getPackageTaskDependency(project, variant)
                        if (packageDependency != null) {
                            it.dependsOn(packageDependency)
                        } else {
                            Log.w(TAG, "Could not locate package task/provider for variant %s. removeUnusedResources may need AGP-specific follow-up.",
                                    variant.name)
                        }
                    }
                }
            }
        }
    }
}
