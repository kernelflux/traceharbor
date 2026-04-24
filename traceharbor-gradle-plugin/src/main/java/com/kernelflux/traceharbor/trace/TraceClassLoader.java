package com.kernelflux.traceharbor.trace;

import com.google.common.collect.ImmutableList;

import org.gradle.api.Project;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;

/**
 * Created by habbyge on 2019/4/24.
 */
public class TraceClassLoader {

    public static URLClassLoader getClassLoader(Project project, Collection<File> inputFiles)
            throws MalformedURLException {

        ImmutableList.Builder<URL> urls = new ImmutableList.Builder<>();
        File androidJar = getAndroidJar(project);
        if (androidJar != null) {
            urls.add(androidJar.toURI().toURL());
        }

        for (File inputFile : inputFiles) {
            urls.add(inputFile.toURI().toURL());
        }

//        for (TransformInput inputs : Iterables.concat(invocation.getInputs(), invocation.getReferencedInputs())) {
//            for (DirectoryInput directoryInput : inputs.getDirectoryInputs()) {
//                if (directoryInput.getFile().isDirectory()) {
//                    urls.add(directoryInput.getFile().toURI().toURL());
//                }
//            }
//            for (JarInput jarInput : inputs.getJarInputs()) {
//                if (jarInput.getFile().isFile()) {
//                    urls.add(jarInput.getFile().toURI().toURL());
//                }
//            }
//        }

        ImmutableList<URL> urlImmutableList = urls.build();
        URL[] classLoaderUrls = urlImmutableList.toArray(new URL[urlImmutableList.size()]);
        return new URLClassLoader(classLoaderUrls);
    }

    private static File getAndroidJar(Project project) {
        Object extension = project.getExtensions().findByName("android");
        if (extension == null) {
            return null;
        }

        try {
            File sdkDirectory = (File) extension.getClass().getMethod("getSdkDirectory").invoke(extension);
            String compileSdkVersion = String.valueOf(extension.getClass().getMethod("getCompileSdkVersion").invoke(extension));
            String androidJarPath = sdkDirectory.getAbsolutePath()
                    + File.separator + "platforms"
                    + File.separator + compileSdkVersion
                    + File.separator + "android.jar";
            File androidJar = new File(androidJarPath);
            return androidJar.exists() ? androidJar : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
