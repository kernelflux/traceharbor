package com.kernelflux.traceharbor.plugin.trace;

import com.kernelflux.traceharbor.javalib.util.Log;
import com.kernelflux.traceharbor.javalib.util.Util;
import com.kernelflux.traceharbor.trace.Configuration;
import com.kernelflux.traceharbor.trace.MethodCollector;
import com.kernelflux.traceharbor.trace.MethodTracer;
import com.kernelflux.traceharbor.trace.TraceBuildConstants;
import com.kernelflux.traceharbor.trace.TraceClassLoader;
import com.kernelflux.traceharbor.trace.item.TraceMethod;
import com.kernelflux.traceharbor.trace.retrace.MappingCollector;
import com.kernelflux.traceharbor.trace.retrace.MappingReader;

import org.gradle.api.Project;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.stream.Collectors;

/**
 * AGP 8+ ScopedArtifacts transform: parse mapping, collect (with merged runtime classpath),
 * trace project classes only, pack a single classes jar.
 * <p>
 * Only {@code Scope.PROJECT} bytecode is rewritten; dependency classes are supplied on the
 * classpath for collection only (Matrix-style broad analysis without rewriting AAR contents).
 */
public final class TraceHarborAgp8TraceRunner {
    private static final String TAG = "TraceHarbor.Agp8Trace";

    private TraceHarborAgp8TraceRunner() {
    }

    public static void run(
            Project project,
            Configuration config,
            File outputJar,
            List<File> projectClassDirectories,
            List<File> projectInputJars,
            Collection<File> additionalClasspath,
            org.gradle.api.logging.Logger gradleLog
    ) throws ExecutionException, InterruptedException, IOException {
        File traceRoot = new File(config.traceClassOut);
        if (!traceRoot.exists() && !traceRoot.mkdirs()) {
            throw new IOException("Failed to create traceClassOut: " + traceRoot);
        }
        File workRoot = new File(traceRoot, "agp8-work-" + System.nanoTime());
        if (!workRoot.mkdirs()) {
            throw new IOException("Failed to create work dir: " + workRoot);
        }
        final ExecutorService executor = Executors.newFixedThreadPool(16);
        try {
            final MappingCollector mappingCollector = new MappingCollector();
            final AtomicInteger methodId = new AtomicInteger(0);
            final ConcurrentHashMap<String, TraceMethod> collectedMethodMap = new ConcurrentHashMap<>();

            parseAndPrepareMapping(config, mappingCollector, collectedMethodMap, methodId);

            Set<File> srcFolders = new HashSet<>();
            Set<File> depJars = new HashSet<>();
            for (File d : projectClassDirectories) {
                if (d != null && d.isDirectory()) {
                    srcFolders.add(d);
                }
            }
            for (File j : projectInputJars) {
                if (j != null && j.isFile()) {
                    depJars.add(j);
                }
            }
            for (File f : additionalClasspath) {
                if (f == null || !f.exists()) {
                    continue;
                }
                if (f.isDirectory()) {
                    srcFolders.add(f);
                } else if (f.isFile() && f.getName().toLowerCase().endsWith(".jar")) {
                    depJars.add(f);
                }
            }
            if (gradleLog != null) {
                gradleLog.lifecycle(String.format(
                        "TraceHarbor AGP8: collect using %d source root(s) and %d dependency jar(s); transform applies to project classes only.",
                        srcFolders.size(), depJars.size()));
            }
            MethodCollector methodCollector = new MethodCollector(executor, mappingCollector, methodId, config, collectedMethodMap);
            methodCollector.collect(srcFolders, depJars);

            ConcurrentHashMap<String, String> classExtend = methodCollector.getCollectedClassExtendMap();
            Map<File, File> dirInOut = new HashMap<>();
            Map<File, File> jarInOut = new HashMap<>();
            for (File inDir : projectClassDirectories) {
                if (inDir == null || !inDir.isDirectory()) {
                    continue;
                }
                File outDir = new File(workRoot, "dir-out-" + Integer.toHexString(inDir.getAbsolutePath().hashCode()));
                if (!outDir.mkdirs()) {
                    throw new IOException("Failed to create: " + outDir);
                }
                dirInOut.put(inDir, outDir);
            }
            for (File inJar : projectInputJars) {
                if (inJar == null || !inJar.isFile()) {
                    continue;
                }
                File outJarF = new File(workRoot, uniqueJarName(inJar));
                jarInOut.put(inJar, outJarF);
            }
            if (dirInOut.isEmpty() && jarInOut.isEmpty()) {
                if (!outputJar.getParentFile().exists()) {
                    outputJar.getParentFile().mkdirs();
                }
                try (JarOutputStream jos = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(outputJar)))) {
                    jos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
                    jos.write("Manifest-Version: 1.0\n\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    jos.closeEntry();
                }
                if (gradleLog != null) {
                    gradleLog.lifecycle("TraceHarbor AGP8: no project class inputs; wrote empty jar.");
                }
                return;
            }
            List<File> classPathForLoader = new ArrayList<>();
            classPathForLoader.addAll(projectClassDirectories);
            classPathForLoader.addAll(projectInputJars);
            classPathForLoader.addAll(additionalClasspath);

            final java.net.URLClassLoader urlClassLoader = TraceClassLoader.getClassLoader(project, classPathForLoader);
            MethodTracer methodTracer = new MethodTracer(executor, mappingCollector, config, methodCollector.getCollectedMethodMap(), classExtend);
            methodTracer.trace(dirInOut, jarInOut, urlClassLoader, config.skipCheckClass);

            packToJar(dirInOut.values(), jarInOut.values(), outputJar, gradleLog);
        } finally {
            executor.shutdown();
            project.delete(workRoot);
        }
    }

    private static String uniqueJarName(File jar) {
        String n = jar.getName();
        int d = n.lastIndexOf('.');
        String h = Integer.toHexString(jar.getAbsolutePath().hashCode());
        if (d < 0) {
            return n + "_th_" + h + ".jar";
        }
        return n.substring(0, d) + "_th_" + h + n.substring(d);
    }

    private static void parseAndPrepareMapping(
            Configuration config,
            MappingCollector mappingCollector,
            ConcurrentHashMap<String, TraceMethod> collectedMethodMap,
            AtomicInteger methodId
    ) throws IOException {
        long start = System.currentTimeMillis();
        File mappingFile = new File(config.mappingDir, "mapping.txt");
        if (mappingFile.exists() && mappingFile.isFile()) {
            MappingReader mappingReader = new MappingReader(mappingFile);
            mappingReader.read(mappingCollector);
        }
        int blockSize = config.parseBlockFile(mappingCollector);
        File baseMethodMapFile = new File(config.baseMethodMapPath);
        getMethodFromBaseMethod(baseMethodMapFile, collectedMethodMap, methodId);
        retraceMethodMap(mappingCollector, collectedMethodMap);
        Log.i(TAG, "[parse] cost:%sms black:%s methodMap size:%s",
                System.currentTimeMillis() - start, blockSize, collectedMethodMap.size());
    }

    private static void retraceMethodMap(MappingCollector processor, ConcurrentHashMap<String, TraceMethod> methodMap) {
        if (processor == null || methodMap == null) {
            return;
        }
        HashMap<String, TraceMethod> retrace = new HashMap<>(methodMap.size());
        for (Map.Entry<String, TraceMethod> entry : methodMap.entrySet()) {
            TraceMethod traceMethod = entry.getValue();
            traceMethod.proguard(processor);
            retrace.put(traceMethod.getMethodName(), traceMethod);
        }
        methodMap.clear();
        methodMap.putAll(retrace);
    }

    private static void getMethodFromBaseMethod(
            File baseMethodFile,
            ConcurrentHashMap<String, TraceMethod> collectedMethodMap,
            AtomicInteger methodId
    ) {
        if (!baseMethodFile.exists()) {
            Log.w(TAG, "[getMethodFromBaseMethod] not exist! %s", baseMethodFile.getAbsolutePath());
            return;
        }
        Scanner fileReader = null;
        try {
            fileReader = new Scanner(new FileInputStream(baseMethodFile), "UTF-8");
            while (fileReader.hasNext()) {
                String nextLine = fileReader.nextLine();
                if (Util.isNullOrNil(nextLine)) {
                    continue;
                }
                nextLine = nextLine.trim();
                if (nextLine.startsWith("#")) {
                    continue;
                }
                String[] fields = nextLine.split(",");
                if (fields.length < 3) {
                    continue;
                }
                TraceMethod traceMethod = new TraceMethod();
                traceMethod.id = Integer.parseInt(fields[0]);
                traceMethod.accessFlag = Integer.parseInt(fields[1]);
                String[] methodField = fields[2].split(" ");
                traceMethod.className = methodField[0].replace("/", ".");
                traceMethod.methodName = methodField[1];
                if (methodField.length > 2) {
                    traceMethod.desc = methodField[2].replace("/", ".");
                }
                collectedMethodMap.put(traceMethod.getMethodName(), traceMethod);
                if (methodId.get() < traceMethod.id && traceMethod.id != TraceBuildConstants.METHOD_ID_DISPATCH) {
                    methodId.set(traceMethod.id);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "[getMethodFromBaseMethod] err! %s", e.getMessage());
        } finally {
            if (fileReader != null) {
                fileReader.close();
            }
        }
    }

    private static void packToJar(
            Collection<File> outDirs,
            Collection<File> outJars,
            File outputFile,
            org.gradle.api.logging.Logger log
    ) throws IOException {
        if (outputFile.getParentFile() != null && !outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }
        Set<String> seen = new HashSet<>();
        int count = 0;
        try (JarOutputStream jos = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile)))) {
            for (File dir : outDirs) {
                if (dir == null || !dir.isDirectory()) {
                    continue;
                }
                Path base = dir.toPath();
                java.util.List<Path> files;
                try (Stream<Path> walk = Files.walk(base)) {
                    files = walk.filter(Files::isRegularFile).collect(Collectors.toList());
                }
                for (Path p : files) {
                    String name = base.relativize(p).toString().replace(File.separatorChar, '/');
                    if (!seen.add(name)) {
                        continue;
                    }
                    jos.putNextEntry(new ZipEntry(name));
                    Files.copy(p, jos);
                    jos.closeEntry();
                    count++;
                }
            }
            for (File jarF : outJars) {
                if (jarF == null || !jarF.isFile()) {
                    continue;
                }
                try (JarFile jf = new JarFile(jarF)) {
                    java.util.Enumeration<JarEntry> en = jf.entries();
                    while (en.hasMoreElements()) {
                        JarEntry e = en.nextElement();
                        if (e.isDirectory()) {
                            continue;
                        }
                        String name = e.getName();
                        if (!seen.add(name)) {
                            continue;
                        }
                        jos.putNextEntry(new ZipEntry(name));
                        try (InputStream in = jf.getInputStream(e)) {
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = in.read(buf)) > 0) {
                                jos.write(buf, 0, n);
                            }
                        }
                        jos.closeEntry();
                        count++;
                    }
                }
            }
        }
        if (log != null) {
            log.lifecycle("TraceHarbor AGP8 trace output: " + outputFile.getAbsolutePath() + " (entries: " + count + ")");
        }
    }
}
