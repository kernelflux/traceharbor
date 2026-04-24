package com.kernelflux.traceharbor.plugin.util

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Replaces com.android.utils.FileUtils for tasks — AGP 8+ does not expose [com.android.utils] on the plugin compile classpath.
 */
object PluginFileUtils {
    @JvmStatic
    fun copyFile(from: File, to: File) {
        to.parentFile?.mkdirs()
        Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    @JvmStatic
    fun deleteRecursivelyIfExists(dir: File) {
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }
}
