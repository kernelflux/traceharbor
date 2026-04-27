/*
 * Tencent is pleased to support the open source community by making wechat-matrix available.
 * Copyright (C) 2018 THL A29 Limited, a Tencent company. All rights reserved.
 */

package com.kernelflux.traceharbor.plugin.task

import com.kernelflux.traceharbor.javalib.util.Log
import com.kernelflux.traceharbor.javalib.util.Util
import com.kernelflux.traceharbor.plugin.compat.CreationConfig
import com.kernelflux.traceharbor.plugin.extension.TraceHarborRemoveUnusedResExtension
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider


object TraceHarborRemoveUnusedRegistrar {
    const val TAG = "TraceHarbor.TasksManager"

    fun register(android: Any, project: Project, ext: TraceHarborRemoveUnusedResExtension) {
        project.afterEvaluate {
            if (!ext.enable) {
                return@afterEvaluate
            }
            val variants = android.javaClass.methods.firstOrNull {
                it.name == "getApplicationVariants" && it.parameterCount == 0
            }?.invoke(android) as? Iterable<*>
            if (variants == null) {
                Log.w(TAG, "Android extension does not expose applicationVariants. removeUnusedResources is disabled for now.")
                return@afterEvaluate
            }
            @Suppress("DEPRECATION")
            variants.forEach { rawVariant ->
                val variant = rawVariant as? com.android.build.gradle.api.BaseVariant ?: return@forEach
                if (Util.isNullOrNil(ext.variant) || variant.name.equals(ext.variant, true)) {
                    Log.i(TAG, "RemoveUnusedResourcesExtension: %s", ext)
                    val removeUnusedResourcesTaskProvider = if (ext.v2) {
                        val action = RemoveUnusedResourcesTaskV2.CreationAction(
                            CreationConfig(variant, project), ext
                        )
                        project.tasks.register(action.name, action.type, action)
                    } else {
                        val action = RemoveUnusedResourcesTask.CreationAction(
                            CreationConfig(variant, project), ext
                        )
                        project.tasks.register(action.name, action.type, action)
                    }
                    CreationConfig.getAssembleTaskDependency(project, variant)?.let { assembleDependency ->
                        when (assembleDependency) {
                            is TaskProvider<*> -> assembleDependency.configure { it.dependsOn(removeUnusedResourcesTaskProvider) }
                            is Task -> assembleDependency.dependsOn(removeUnusedResourcesTaskProvider)
                            else -> { }
                        }
                    }
                    removeUnusedResourcesTaskProvider.configure { task ->
                        val packageDependency = CreationConfig.getPackageTaskDependency(project, variant)
                        if (packageDependency != null) {
                            task.dependsOn(packageDependency)
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
