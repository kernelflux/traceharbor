package com.kernelflux.traceharbor.plugin.trace

import com.kernelflux.traceharbor.javalib.util.Log
import com.kernelflux.traceharbor.javalib.util.Util
import com.kernelflux.traceharbor.trace.Configuration
import com.kernelflux.traceharbor.trace.MethodCollector
import com.kernelflux.traceharbor.trace.MethodTracer
import com.kernelflux.traceharbor.trace.TraceBuildConstants
import com.kernelflux.traceharbor.trace.TraceClassLoader
import com.kernelflux.traceharbor.trace.item.TraceMethod
import com.kernelflux.traceharbor.trace.retrace.MappingCollector
import com.kernelflux.traceharbor.trace.retrace.MappingReader
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayList
import java.util.Enumeration
import java.util.HashMap
import java.util.HashSet
import java.util.Scanner
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.stream.Collectors
import java.util.zip.ZipEntry
import kotlin.collections.iterator

/**
 * AGP 8+ ScopedArtifacts transform: parse mapping, collect (with merged runtime classpath),
 * trace project classes only, pack a single classes jar.
 *
 * Only `Scope.PROJECT` bytecode is rewritten; dependency classes are supplied on the
 * classpath for collection only (Matrix-style broad analysis without rewriting AAR contents).
 */
object TraceHarborAgp8TraceRunner {
    private const val TAG = "TraceHarbor.Agp8Trace"

    @Throws(ExecutionException::class, InterruptedException::class, IOException::class)
    @JvmStatic
    fun run(
        project: Project,
        config: Configuration,
        outputJar: File,
        projectClassDirectories: List<File>,
        projectInputJars: List<File>,
        additionalClasspath: Collection<File>,
        gradleLog: Logger?
    ) {
        val traceRoot = File(config.traceClassOut)
        if (!traceRoot.exists() && !traceRoot.mkdirs()) {
            throw IOException("Failed to create traceClassOut: $traceRoot")
        }
        val workRoot = File(traceRoot, "agp8-work-" + System.nanoTime())
        if (!workRoot.mkdirs()) {
            throw IOException("Failed to create work dir: $workRoot")
        }
        val executor: ExecutorService = Executors.newFixedThreadPool(16)
        try {
            val mappingCollector = MappingCollector()
            val methodId = AtomicInteger(0)
            val collectedMethodMap = ConcurrentHashMap<String, TraceMethod>()

            parseAndPrepareMapping(config, mappingCollector, collectedMethodMap, methodId)

            val srcFolders = HashSet<File>()
            val depJars = HashSet<File>()
            for (d in projectClassDirectories) {
                if (d.isDirectory) {
                    srcFolders.add(d)
                }
            }
            for (j in projectInputJars) {
                if (j.isFile) {
                    depJars.add(j)
                }
            }
            for (f in additionalClasspath) {
                if (!f.exists()) {
                    continue
                }
                if (f.isDirectory) {
                    srcFolders.add(f)
                } else if (f.isFile && f.name.lowercase().endsWith(".jar")) {
                    depJars.add(f)
                }
            }
            gradleLog?.lifecycle(
                String.format(
                    "TraceHarbor AGP8: collect using %d source root(s) and %d dependency jar(s); transform applies to project classes only.",
                    srcFolders.size,
                    depJars.size
                )
            )
            val methodCollector =
                MethodCollector(executor, mappingCollector, methodId, config, collectedMethodMap)
            methodCollector.collect(srcFolders, depJars)

            val classExtend = methodCollector.getCollectedClassExtendMap()
            val dirInOut = HashMap<File, File>()
            val jarInOut = HashMap<File, File>()
            for (inDir in projectClassDirectories) {
                if (!inDir.isDirectory) {
                    continue
                }
                val outDir =
                    File(workRoot, "dir-out-" + Integer.toHexString(inDir.absolutePath.hashCode()))
                if (!outDir.mkdirs()) {
                    throw IOException("Failed to create: $outDir")
                }
                dirInOut[inDir] = outDir
            }
            for (inJar in projectInputJars) {
                if (!inJar.isFile) {
                    continue
                }
                val outJarF = File(workRoot, uniqueJarName(inJar))
                jarInOut[inJar] = outJarF
            }
            if (dirInOut.isEmpty() && jarInOut.isEmpty()) {
                outputJar.parentFile?.let {
                    if (!it.exists()) {
                        it.mkdirs()
                    }
                }
                JarOutputStream(BufferedOutputStream(FileOutputStream(outputJar))).use { jos ->
                    jos.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
                    jos.write("Manifest-Version: 1.0\n\n".toByteArray(Charsets.UTF_8))
                    jos.closeEntry()
                }
                gradleLog?.lifecycle("TraceHarbor AGP8: no project class inputs; wrote empty jar.")
                return
            }
            val classPathForLoader = ArrayList<File>()
            classPathForLoader.addAll(projectClassDirectories)
            classPathForLoader.addAll(projectInputJars)
            classPathForLoader.addAll(additionalClasspath)

            val urlClassLoader: URLClassLoader = TraceClassLoader.getClassLoader(project, classPathForLoader)
            val methodTracer =
                MethodTracer(
                    executor,
                    mappingCollector,
                    config,
                    methodCollector.getCollectedMethodMap(),
                    classExtend
                )
            methodTracer.trace(dirInOut, jarInOut, urlClassLoader, config.skipCheckClass)

            packToJar(dirInOut.values, jarInOut.values, outputJar, gradleLog)
        } finally {
            executor.shutdown()
            project.delete(workRoot)
        }
    }

    private fun uniqueJarName(jar: File): String {
        val n = jar.name
        val d = n.lastIndexOf('.')
        val h = Integer.toHexString(jar.absolutePath.hashCode())
        if (d < 0) {
            return "${n}_th_${h}.jar"
        }
        return n.substring(0, d) + "_th_" + h + n.substring(d)
    }

    @Throws(IOException::class)
    private fun parseAndPrepareMapping(
        config: Configuration,
        mappingCollector: MappingCollector,
        collectedMethodMap: ConcurrentHashMap<String, TraceMethod>,
        methodId: AtomicInteger
    ) {
        val start = System.currentTimeMillis()
        val mappingFile = File(config.mappingDir, "mapping.txt")
        if (mappingFile.exists() && mappingFile.isFile) {
            val mappingReader = MappingReader(mappingFile)
            mappingReader.read(mappingCollector)
        }
        val blockSize = config.parseBlockFile(mappingCollector)
        val baseMethodMapFile = File(config.baseMethodMapPath)
        getMethodFromBaseMethod(baseMethodMapFile, collectedMethodMap, methodId)
        retraceMethodMap(mappingCollector, collectedMethodMap)
        Log.Companion.i(
            TAG,
            "[parse] cost:%sms black:%s methodMap size:%s",
            System.currentTimeMillis() - start,
            blockSize,
            collectedMethodMap.size
        )
    }

    private fun retraceMethodMap(
        processor: MappingCollector?,
        methodMap: ConcurrentHashMap<String, TraceMethod>?
    ) {
        if (processor == null || methodMap == null) {
            return
        }
        val retrace = HashMap<String, TraceMethod>(methodMap.size)
        for ((_, traceMethod) in methodMap) {
            traceMethod.proguard(processor)
            retrace[traceMethod.getMethodName()] = traceMethod
        }
        methodMap.clear()
        methodMap.putAll(retrace)
    }

    private fun getMethodFromBaseMethod(
        baseMethodFile: File,
        collectedMethodMap: ConcurrentHashMap<String, TraceMethod>,
        methodId: AtomicInteger
    ) {
        if (!baseMethodFile.exists()) {
            Log.Companion.w(TAG, "[getMethodFromBaseMethod] not exist! %s", baseMethodFile.absolutePath)
            return
        }
        var fileReader: Scanner? = null
        try {
            fileReader = Scanner(FileInputStream(baseMethodFile), "UTF-8")
            while (fileReader.hasNext()) {
                var nextLine = fileReader.nextLine()
                if (Util.isNullOrNil(nextLine)) {
                    continue
                }
                nextLine = nextLine.trim()
                if (nextLine.startsWith("#")) {
                    continue
                }
                val fields = nextLine.split(",").toTypedArray()
                if (fields.size < 3) {
                    continue
                }
                val traceMethod = TraceMethod()
                traceMethod.id = fields[0].toInt()
                traceMethod.accessFlag = fields[1].toInt()
                val methodField = fields[2].split(" ").toTypedArray()
                traceMethod.className = methodField[0].replace("/", ".")
                traceMethod.methodName = methodField[1]
                if (methodField.size > 2) {
                    traceMethod.desc = methodField[2].replace("/", ".")
                }
                collectedMethodMap[traceMethod.getMethodName()] = traceMethod
                if (methodId.get() < traceMethod.id && traceMethod.id != TraceBuildConstants.METHOD_ID_DISPATCH) {
                    methodId.set(traceMethod.id)
                }
            }
        } catch (e: Exception) {
            Log.Companion.e(TAG, "[getMethodFromBaseMethod] err! %s", e.message)
        } finally {
            fileReader?.close()
        }
    }

    @Throws(IOException::class)
    private fun packToJar(
        outDirs: Collection<File>,
        outJars: Collection<File>,
        outputFile: File,
        log: Logger?
    ) {
        outputFile.parentFile?.let {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
        val seen = HashSet<String>()
        var count = 0
        JarOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { jos ->
            for (dir in outDirs) {
                if (!dir.isDirectory) {
                    continue
                }
                val base = dir.toPath()
                val files: List<Path>
                Files.walk(base).use { walk ->
                    files = walk.filter { p: Path -> Files.isRegularFile(p) }.collect(Collectors.toList())
                }
                for (p in files) {
                    val name = base.relativize(p).toString().replace(File.separatorChar, '/')
                    if (!seen.add(name)) {
                        continue
                    }
                    jos.putNextEntry(ZipEntry(name))
                    Files.copy(p, jos)
                    jos.closeEntry()
                    count++
                }
            }
            for (jarF in outJars) {
                if (!jarF.isFile) {
                    continue
                }
                JarFile(jarF).use { jf ->
                    val en: Enumeration<JarEntry> = jf.entries()
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
                        jf.getInputStream(e).use { input ->
                            val buf = ByteArray(8192)
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) {
                                    break
                                }
                                jos.write(buf, 0, n)
                            }
                        }
                        jos.closeEntry()
                        count++
                    }
                }
            }
        }
        log?.lifecycle("TraceHarbor AGP8 trace output: ${outputFile.absolutePath} (entries: $count)")
    }
}