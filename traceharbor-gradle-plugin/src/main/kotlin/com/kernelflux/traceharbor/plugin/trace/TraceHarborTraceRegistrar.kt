/*
 * Tencent is pleased to support the open source community by making wechat-matrix available.
 * Copyright (C) 2018 THL A29 Limited, a Tencent company. All rights reserved.
 */

package com.kernelflux.traceharbor.plugin.trace

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.ScopedArtifacts
import com.kernelflux.traceharbor.javalib.util.Log
import com.kernelflux.traceharbor.trace.extension.TraceHarborTraceExtension
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import java.io.File
import java.util.Locale

/**
 * Registers TraceHarbor trace as an AGP 8 [com.android.build.api.variant.ScopedArtifacts] transform.
 *
 * **Scope note:** The wired artifact is [ScopedArtifacts.Scope.PROJECT] (only the app module's own
 * classes are rewritten). The variant's **runtime classpath** is attached to the task as
 * [TraceHarborTraceTask.dependencyClasspath] so [com.kernelflux.traceharbor.trace.MethodCollector] can still
 * see dependency bytecode for inheritance / id assignment (read-only), matching legacy Matrix
 * "full project" collection behavior as closely as the modern API allows. Dependency/AAR classes
 * are not copied into the transform output jar.
 */
object TraceHarborTraceRegistrar {
    const val TAG = "TraceHarbor.Agp8Registrar"

    fun registerIfEnabled(project: Project, traceExtension: TraceHarborTraceExtension) {
        val androidComponents =
            project.extensions.findByType(AndroidComponentsExtension::class.java)
        if (androidComponents == null) {
            Log.w(TAG, "AndroidComponentsExtension not found; AGP8 trace registration skipped.")
            return
        }
        androidComponents.onVariants { variant ->
            if (variant !is ApplicationVariant) {
                return@onVariants
            }
            val vName = variant.name
            val cap = vName.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
            val buildDir = project.buildDir
            val mappingOut = File(buildDir, "outputs/mapping/$vName")
            val traceClassOut = File(buildDir, "intermediates/traceharbor/$vName/traceClassOut")
            val taskName = "transform${cap}ClassesWithTraceHarbor"
            val taskProvider = project.tasks.register(
                taskName,
                TraceHarborTraceTask::class.java
            )
            val runtimeClasspathName = "${vName}RuntimeClasspath"
            val runtimeClasspath = project.configurations.findByName(runtimeClasspathName)
            if (runtimeClasspath != null) {
                val artifactType = Attribute.of("artifactType", String::class.java)
                fun fileCollectionFor(artifact: String) =
                    runtimeClasspath.incoming.artifactView {
                        it.attributes { attributeContainer ->
                            attributeContainer.attribute(artifactType, artifact)
                        }
                        it.lenient(true)
                    }.files

                val runtimeClassesJars = fileCollectionFor("android-classes-jar")
                val runtimeClassesDirs = fileCollectionFor("android-classes-directory")
                val runtimeJavaRes = fileCollectionFor("android-java-res")
                taskProvider.configure {
                    it.dependencyClasspath.from(
                        runtimeClassesJars,
                        runtimeClassesDirs,
                        runtimeJavaRes
                    )
                }
            } else {
                Log.w(
                    TAG,
                    "No configuration named %s; trace collector classpath may be incomplete.",
                    runtimeClasspathName
                )
            }
            taskProvider.configure { task ->
                task.enableTrace.set(project.provider { traceExtension.isEnable })
                task.applicationId.set(variant.applicationId)
                task.variantName.set(vName)
                task.mappingDir.set(mappingOut.absolutePath)
                task.traceClassOutDir.set(traceClassOut.absolutePath)
                task.methodMapFilePath.set(File(mappingOut, "methodMapping.txt").absolutePath)
                task.ignoreMethodMapFilePath.set(
                    File(
                        mappingOut,
                        "ignoreMethodMapping.txt"
                    ).absolutePath
                )
                task.baseMethodMapPath.set(project.provider {
                    traceExtension.baseMethodMapFile ?: ""
                })
                task.blockListFilePath.set(project.provider { traceExtension.blackListFile ?: "" })
                task.skipCheckClass.set(project.provider { traceExtension.isSkipCheckClass })
                task.extraBlockLines.set(project.provider { buildExtraBlockLines(traceExtension) })
                task.group = "traceharbor"
            }
            variant.artifacts
                .forScope(ScopedArtifacts.Scope.PROJECT)
                .use(taskProvider)
                .toTransform(
                    ScopedArtifact.CLASSES,
                    TraceHarborTraceTask::allJars,
                    TraceHarborTraceTask::allDirectories,
                    TraceHarborTraceTask::output
                )
        }
    }

    /**
     * Translate the inline `ignorePackages` / `ignoreClasses` DSL lists into the same
     * ProGuard-style `-keeppackage` / `-keepclass` lines the file-based blacklist uses, so
     * downstream parsing only has to deal with one format.
     *
     * Accepted package shapes (all map to the slash-form prefix):
     * - `com.acme.foo`        → `com/acme/foo/`
     * - `com.acme.foo.`       → `com/acme/foo/`
     * - `com.acme.foo.*`      → `com/acme/foo/`
     * - `com.acme.foo.**`     → `com/acme/foo/`
     * - `com/acme/foo/`       → `com/acme/foo/` (already slash form, no-op)
     */
    private fun buildExtraBlockLines(ext: TraceHarborTraceExtension): List<String> {
        val out = ArrayList<String>()
        ext.ignorePackages.orEmpty().forEach { raw ->
            val pkg = normalizePackagePrefix(raw) ?: return@forEach
            out.add("-keeppackage $pkg")
        }
        ext.ignoreClasses.orEmpty().forEach { raw ->
            val cls = normalizeClassName(raw) ?: return@forEach
            out.add("-keepclass $cls")
        }
        return out
    }

    private fun normalizePackagePrefix(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var s = raw.trim()
        // Allow trailing wildcard sugar but treat them all as plain prefix matches.
        if (s.endsWith(".**")) s = s.removeSuffix(".**")
        if (s.endsWith(".*")) s = s.removeSuffix(".*")
        if (s.endsWith("/")) s = s.removeSuffix("/")
        if (s.endsWith(".")) s = s.removeSuffix(".")
        if (s.isEmpty()) return null
        return s.replace('.', '/') + "/"
    }

    private fun normalizeClassName(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()
        if (s.endsWith(".*") || s.endsWith(".**") || s.endsWith("/")) {
            // looks like a package — encourage migration to ignorePackages instead of silently
            // converting; keeppackage matches more entries than the user expects from "class".
            Log.w(
                TAG,
                "ignoreClasses entry '%s' looks like a package; use ignorePackages instead.",
                raw
            )
            return null
        }
        return s.replace('.', '/')
    }
}
