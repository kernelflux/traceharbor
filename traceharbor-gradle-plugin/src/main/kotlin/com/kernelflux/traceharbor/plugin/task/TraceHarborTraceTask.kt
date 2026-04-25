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
import com.kernelflux.traceharbor.plugin.compat.CreationConfig
import com.kernelflux.traceharbor.trace.extension.TraceHarborTraceExtension
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

abstract class TraceHarborTraceTask : DefaultTask() {
    companion object {
        private const val TAG: String = "TraceHarbor.TraceTask"

        fun getTraceClassOut(project: Project, creationConfig: CreationConfig): String {
            return project.buildDir.resolve("outputs")
                .resolve("traceharbor-trace-disabled")
                .resolve(creationConfig.variant.name)
                .absolutePath
        }
    }

    @TaskAction
    fun execute() {
        Log.w(TAG, "TraceHarbor trace task is disabled in the AGP 8 migration branch.")
    }

    class CreationAction(
            private val creationConfig: CreationConfig,
            private val extension: TraceHarborTraceExtension
    ) : Action<TraceHarborTraceTask>, BaseCreationAction<TraceHarborTraceTask>(creationConfig) {

        override val name = computeTaskName("traceHarbor", "Trace")
        override val type = TraceHarborTraceTask::class.java

        override fun execute(task: TraceHarborTraceTask) {
            task.group = "traceharbor"
            task.description = "Placeholder task while TraceHarbor trace is being migrated to AGP 8 APIs."
            Log.w(TAG, "Skipping TraceHarbor trace task wiring for variant %s.", creationConfig.variant.name)
        }
    }
}