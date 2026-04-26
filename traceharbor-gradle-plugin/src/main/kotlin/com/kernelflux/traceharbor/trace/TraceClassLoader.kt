package com.kernelflux.traceharbor.trace

import com.google.common.collect.ImmutableList
import org.gradle.api.Project
import java.io.File
import java.net.MalformedURLException
import java.net.URL
import java.net.URLClassLoader

/**
 * Created by habbyge on 2019/4/24.
 */
object TraceClassLoader {

    @Throws(MalformedURLException::class)
    @JvmStatic
    fun getClassLoader(project: Project, inputFiles: Collection<File>): URLClassLoader {
        val urls = ImmutableList.Builder<URL>()
        val androidJar = getAndroidJar(project)
        if (androidJar != null) {
            urls.add(androidJar.toURI().toURL())
        }

        for (inputFile in inputFiles) {
            urls.add(inputFile.toURI().toURL())
        }

        val urlImmutableList = urls.build()
        return URLClassLoader(urlImmutableList.toTypedArray())
    }

    private fun getAndroidJar(project: Project): File? {
        val extension = project.extensions.findByName("android") ?: return null

        return try {
            val sdkDirectory = extension.javaClass.getMethod("getSdkDirectory").invoke(extension) as File
            val compileSdkVersion =
                extension.javaClass.getMethod("getCompileSdkVersion").invoke(extension).toString()
            val androidJarPath = sdkDirectory.absolutePath +
                File.separator + "platforms" +
                File.separator + compileSdkVersion +
                File.separator + "android.jar"
            val androidJar = File(androidJarPath)
            if (androidJar.exists()) androidJar else null
        } catch (ignored: Throwable) {
            null
        }
    }
}

