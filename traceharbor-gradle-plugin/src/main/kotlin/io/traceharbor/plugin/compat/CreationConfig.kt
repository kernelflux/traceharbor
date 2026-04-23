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

package io.traceharbor.plugin.compat

import com.android.build.gradle.api.BaseVariant
import com.android.builder.model.CodeShrinker
import org.gradle.api.Task
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import java.io.File

class CreationConfig(
        val variant: BaseVariant,
        val project: Project
) {
    companion object {
        private fun String.capitalized(): String {
            return replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase()
                else ch.toString()
            }
        }

        private fun Any.invokeNoArg(name: String): Any? {
            return javaClass.methods.firstOrNull {
                it.name == name && it.parameterCount == 0
            }?.invoke(this)
        }

        fun getCodeShrinker(project: Project): CodeShrinker {

            var enableR8: Boolean = when (val property = project.properties["android.enableR8"]) {
                null -> true
                else -> (property as String).toBoolean()
            }

            return when {
                enableR8 -> CodeShrinker.R8
                else -> CodeShrinker.PROGUARD
            }
        }

        fun getAssembleTaskDependency(project: Project, variant: BaseVariant): Any? {
            val provider = variant.invokeNoArg("getAssembleProvider")
            if (provider != null) {
                return provider
            }
            return findNamedTask(project, "assemble${variant.name.capitalized()}")
        }

        fun getPackageTaskDependency(project: Project, variant: BaseVariant): Any? {
            val provider = variant.invokeNoArg("getPackageApplicationProvider")
            if (provider != null) {
                return provider
            }
            val candidateNames = listOf(
                "package${variant.name.capitalized()}",
                "package${variant.name.capitalized()}Bundle",
                "bundle${variant.name.capitalized()}",
                "package${variant.buildType.name.capitalized()}"
            )
            for (name in candidateNames) {
                val task = findNamedTask(project, name)
                if (task != null) {
                    return task
                }
            }
            return null
        }

        fun getOutputApkFiles(variant: BaseVariant): List<File> {
            val apkFiles = mutableListOf<File>()
            variant.outputs.forEach { output ->
                val direct = output.invokeNoArg("getOutputFile") as? File
                if (direct != null) {
                    apkFiles += direct
                    return@forEach
                }

                val outputFileName = output.invokeNoArg("getOutputFileName") as? String
                if (!outputFileName.isNullOrBlank()) {
                    val packageTask = output.invokeNoArg("getPackageApplication")
                    val outputDirectory = packageTask?.invokeNoArg("getOutputDirectory") as? File
                    if (outputDirectory != null) {
                        apkFiles += outputDirectory.resolve(outputFileName)
                    }
                }
            }
            return apkFiles.distinct()
        }

        private fun findNamedTask(project: Project, name: String): TaskProvider<Task>? {
            return try {
                project.tasks.named(name)
            } catch (_: Throwable) {
                null
            }
        }
    }
}