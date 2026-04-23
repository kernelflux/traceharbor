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

package io.traceharbor.plugin.trace

import io.traceharbor.javalib.util.Log
import org.gradle.api.Project
import java.io.File

class TraceHarborTrace(
        private val ignoreMethodMapFilePath: String,
        private val methodMapFilePath: String,
        private val baseMethodMapPath: String?,
        private val blockListFilePath: String?,
        private val mappingDir: String,
        private val project: Project
) {
    companion object {
        private const val TAG: String = "TraceHarbor.Trace"

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

    }

    @Suppress("UNUSED_PARAMETER")
    fun doTransform(classInputs: Collection<File>,
                    changedFiles: Map<File, *>,
                    inputToOutput: Map<File, File>,
                    isIncremental: Boolean,
                    skipCheckClass: Boolean,
                    traceClassDirectoryOutput: File,
                    legacyReplaceChangedFile: ((File, Map<File, *>) -> Any)?,
                    legacyReplaceFile: ((File, File) -> Any)?,
                    uniqueOutputName: Boolean
    ) {
        Log.w(TAG, "TraceHarbor trace bytecode instrumentation is disabled while AGP 8 support is being rebuilt.")
    }
}