package com.kernelflux.traceharbor.apk.model.job

import com.android.utils.Pair
import com.google.gson.JsonArray

class JobConfig {
    var inputDir: String? = null
    var apkPath: String? = null
    var unzipPath: String? = null
    var outputPath: String? = null
    var mappingFilePath: String? = null
    var resMappingFilePath: String? = null
    var outputConfig: JsonArray? = null
    var outputFormatList: List<String>? = null
    var proguardClassMap: Map<String, String>? = null
    var resguardMap: Map<String, String>? = null
    var entrySizeMap: Map<String, Pair<Long, Long>>? = null
    var entryNameMap: Map<String, String>? = null
}

