/*
 * Tencent is pleased to support the open source community by making wechat-matrix available.
 * Copyright (C) 2018 THL A29 Limited, a Tencent company. All rights reserved.
 */

package com.kernelflux.traceharbor.plugin.trace

import com.kernelflux.traceharbor.trace.Configuration
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.nio.file.Files
import java.nio.file.Path

/**
 * AGP 8 [com.android.build.api.artifact.ScopedArtifacts] transform: project [allJars]/[allDirectories]
 * in, single classes jar out. [dependencyClasspath] is read-only for MethodCollector / classloader.
 */
abstract class TraceHarborTraceTask : DefaultTask() {

    /** When false (default), rewrites the transform output to match inputs without tracing. */
    @get:Input
    abstract val enableTrace: Property<Boolean>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val allJars: ListProperty<RegularFile>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val allDirectories: ListProperty<Directory>

    @get:Classpath
    abstract val dependencyClasspath: ConfigurableFileCollection

    @get:Input
    abstract val applicationId: Property<String>

    @get:Input
    abstract val variantName: Property<String>

    @get:Input
    abstract val mappingDir: Property<String>

    @get:Input
    abstract val traceClassOutDir: Property<String>

    @get:Input
    abstract val methodMapFilePath: Property<String>

    @get:Input
    abstract val ignoreMethodMapFilePath: Property<String>

    @get:Input
    abstract val baseMethodMapPath: Property<String>

    @get:Input
    abstract val blockListFilePath: Property<String>

    /**
     * Extra ProGuard-style block lines fed inline from the DSL ({@code ignorePackages} /
     * {@code ignoreClasses}); merged with [blockListFilePath] at execution time.
     */
    @get:Input
    abstract val extraBlockLines: ListProperty<String>

    @get:Input
    abstract val skipCheckClass: Property<Boolean>

    @get:OutputFile
    abstract val output: org.gradle.api.file.RegularFileProperty

    @TaskAction
    fun run() {
        if (!enableTrace.getOrElse(false)) {
            passthroughToOutput()
            return
        }
        val extBase = baseMethodMapPath.get().ifEmpty { "" }
        val extBlock = blockListFilePath.get().ifEmpty { "" }
        val config = Configuration.Builder()
            .setPackageName(applicationId.get())
            .setMappingPath(mappingDir.get())
            .setBaseMethodMap(extBase)
            .setMethodMapFilePath(methodMapFilePath.get())
            .setIgnoreMethodMapFilePath(ignoreMethodMapFilePath.get())
            .setBlockListFile(extBlock)
            .setTraceClassOut(traceClassOutDir.get())
            .setSkipCheckClass(skipCheckClass.getOrElse(true))
            .setExtraBlockLines(extraBlockLines.getOrElse(emptyList()))
            .build()

        val projDirs = allDirectories.get().map { it.asFile }
        val projJars = allJars.get().map { it.asFile }
        val depClasspath = dependencyClasspath.files
        val out = output.get().asFile
        TraceHarborTraceRunner.run(
            project,
            config,
            out,
            projDirs,
            projJars,
            depClasspath,
            logger
        )
    }

    /** Identity transform for the ScopedArtifacts output when [enableTrace] is off. */
    private fun passthroughToOutput() {
        val out = output.get().asFile
        out.parentFile?.mkdirs()
        val seen = linkedSetOf<String>()
        JarOutputStream(BufferedOutputStream(FileOutputStream(out))).use { jos ->
            for (dir in allDirectories.get()) {
                val base = dir.asFile.toPath()
                Files.walk(base).use { stream ->
                    val paths = ArrayList<Path>()
                    stream.filter(Files::isRegularFile).forEach { paths.add(it) }
                    for (p in paths) {
                        val name = base.relativize(p).toString().replace(File.separatorChar, '/')
                        if (!seen.add(name)) {
                            continue
                        }
                        jos.putNextEntry(ZipEntry(name))
                        Files.copy(p, jos)
                        jos.closeEntry()
                    }
                }
            }
            for (j in allJars.get()) {
                JarFile(j.asFile).use { jarfile ->
                    val en = jarfile.entries()
                    while (en.hasMoreElements()) {
                        val e = en.nextElement()
                        if (e.isDirectory) {
                            continue
                        }
                        val name = e.name
                        if (!seen.add(name)) {
                            continue
                        }
                        jos.putNextEntry(ZipEntry(name))
                        jarfile.getInputStream(e).use { stream ->
                            stream.copyTo(jos, 8192)
                        }
                        jos.closeEntry()
                    }
                }
            }
        }
        logger.lifecycle("TraceHarbor: trace disabled (enable=false); passthrough merge to " + out.absolutePath + " (entries: " + seen.size + ")")
    }
}
