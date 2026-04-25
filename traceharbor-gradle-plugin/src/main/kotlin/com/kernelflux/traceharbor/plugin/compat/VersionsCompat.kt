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

package com.kernelflux.traceharbor.plugin.compat

enum class AGPVersion(
        val value: String
) {
    // Versions that we cared about.
    AGP_3_5_0("3.5.0"),
    AGP_3_6_0("3.6.0"),
    AGP_4_0_0("4.0.0"),
    AGP_4_1_0("4.1.0"),
    AGP_7_0_0("7.0.0"),
    AGP_8_0_0("8.0.0"),
    AGP_8_2_0("8.2.0")
}

class VersionsCompat {

    companion object {
        private fun normalize(version: String): List<Int> {
            val main = version.substringBefore('-')
            return main.split('.').map {
                it.toIntOrNull() ?: 0
            }
        }

        private fun compareVersions(left: String, right: String): Int {
            val lhs = normalize(left)
            val rhs = normalize(right)
            val maxSize = maxOf(lhs.size, rhs.size)
            for (index in 0 until maxSize) {
                val lhsValue = lhs.getOrElse(index) { 0 }
                val rhsValue = rhs.getOrElse(index) { 0 }
                if (lhsValue != rhsValue) {
                    return lhsValue.compareTo(rhsValue)
                }
            }
            return 0
        }

        @Suppress("DEPRECATION")
        private fun initCurrentAndroidGradlePluginVersion(): String {
            return try {
                val c = Class.forName("com.android.Version")
                c.getField("ANDROID_GRADLE_PLUGIN_VERSION").get(null) as String
            } catch (_: Throwable) {
                try {
                    val c2 = Class.forName("com.android.builder.model.Version")
                    c2.getField("ANDROID_GRADLE_PLUGIN_VERSION").get(null) as String
                } catch (_: Throwable) {
                    "8.0.0"
                }
            }
        }

        /**
         * Defer reading AGP's version to first use. Eager [init] during plugin apply can break Gradle 8
         * (IllegalStateException: state Configure vs TaskSchedule) in multi-project builds.
         */
        val androidGradlePluginVersion: String by lazy { initCurrentAndroidGradlePluginVersion() }

        val lessThan = {agpVersion: AGPVersion -> compareVersions(androidGradlePluginVersion, agpVersion.value) < 0 }

        val greatThanOrEqual = {agpVersion: AGPVersion -> compareVersions(androidGradlePluginVersion, agpVersion.value) >= 0 }

        val equalTo = {agpVersion: AGPVersion -> compareVersions(androidGradlePluginVersion, agpVersion.value) == 0}
    }

}