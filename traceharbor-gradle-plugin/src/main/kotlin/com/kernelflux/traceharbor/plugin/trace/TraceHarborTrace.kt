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

package com.kernelflux.traceharbor.plugin.trace

import com.kernelflux.traceharbor.trace.Configuration
import org.gradle.api.Project
import java.io.File

/**
 * Runtime helper for the AGP 8+ trace pipeline. The production entry point is
 * [TraceHarborTraceAgp8Task], which calls [TraceHarborAgp8TraceRunner] directly.
 */
object TraceHarborTrace {
    @JvmStatic
    fun appendSuffix(jarFile: File, suffix: String): String {
        val origJarName = jarFile.name
        val dotPos = origJarName.lastIndexOf('.')
        return if (dotPos < 0) {
            String.format("%s_%s", origJarName, suffix)
        } else {
            val nameWithoutDotExt = origJarName.substring(0, dotPos)
            val dotExt = origJarName.substring(dotPos)
            String.format("%s_%s%s", nameWithoutDotExt, suffix, dotExt)
        }
    }

    /**
     * Same work as [TraceHarborTraceAgp8Task] / [TraceHarborAgp8TraceRunner] for tests or custom wiring.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun runAgp8(
        project: Project,
        config: Configuration,
        outputJar: File,
        projectClassDirs: List<File>,
        projectJars: List<File>,
        depClasspath: Collection<File>
    ) {
        TraceHarborAgp8TraceRunner.run(
            project, config, outputJar, projectClassDirs, projectJars, depClasspath, null
        )
    }
}
