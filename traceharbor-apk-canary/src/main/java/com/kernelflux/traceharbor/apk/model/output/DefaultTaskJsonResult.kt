package com.kernelflux.traceharbor.apk.model.output

import com.android.utils.Pair
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.kernelflux.traceharbor.apk.model.result.TaskJsonResult
import com.kernelflux.traceharbor.apk.model.task.TaskFactory
import com.kernelflux.traceharbor.javalib.util.Util
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import java.util.HashMap
import java.util.HashSet
import javax.xml.parsers.ParserConfigurationException

class DefaultTaskJsonResult @Throws(ParserConfigurationException::class) constructor(
    type: Int,
    config: JsonObject?,
) : TaskJsonResult(type, config) {
    override fun format(jsonObject: JsonObject) {
        formatJson(jsonObject, this.jsonObject, config)
    }

    companion object {
        @JvmStatic
        fun formatJson(jsonObjectInput: JsonObject, jsonObjectOutput: JsonObject?, config: JsonObject?) {
            val taskType = jsonObjectInput.get("taskType").asInt
            when (taskType) {
                TaskFactory.TASK_TYPE_UNZIP -> formatUnzipTask(jsonObjectInput)
                TaskFactory.TASK_TYPE_MANIFEST -> formatManifestAnalyzeTask(jsonObjectInput)
                TaskFactory.TASK_TYPE_COUNT_METHOD -> formatMethodCountTask(jsonObjectInput, config)
                TaskFactory.TASK_TYPE_COUNT_R_CLASS -> formatCountR(jsonObjectInput)
                TaskFactory.TASK_TYPE_COUNT_CLASS -> formatCountClass(jsonObjectInput, config)
            }
            if (jsonObjectOutput != null) {
                for ((key, value) in jsonObjectInput.entrySet()) {
                    if (!jsonObjectOutput.has(key)) {
                        jsonObjectOutput.add(key, value)
                    }
                }
            }
        }

        private fun formatCountR(jsonObject: JsonObject) {
            val files = jsonObject.getAsJsonArray("R-classes")
            val rMaps: HashMap<String, Int> = HashMap()
            for (file in files) {
                val obj = file as JsonObject
                rMaps[obj.get("name").asString] = obj.get("field-count").asInt
            }

            val keys = ArrayList(rMaps.keys)
            Collections.sort(
                keys,
                Comparator { left, right ->
                    val pair1 = rMaps[left] ?: 0
                    val pair2 = rMaps[right] ?: 0
                    when {
                        pair1 > pair2 -> -1
                        pair1 < pair2 -> 1
                        else -> 0
                    }
                },
            )
            jsonObject.remove("R-classes")

            val groupArray = JsonArray()
            for (name in keys) {
                val groupObj = JsonObject()
                groupObj.addProperty("name", name)
                groupObj.addProperty("field-count", rMaps[name])
                groupArray.add(groupObj)
            }
            jsonObject.add("R-classes", groupArray)
        }

        private fun formatUnzipTask(jsonObject: JsonObject) {
            val fileGroupMap: MutableMap<String, Long> = HashMap()
            val fileListGroup: MutableMap<String, JsonArray> = HashMap()
            var otherFilesSize = 0L
            val otherFiles = JsonArray()
            val files = jsonObject.getAsJsonArray("entries")
            for (file in files) {
                val fileObj = file as JsonObject
                val filename = fileObj.get("entry-name").asString
                if (!Util.isNullOrNil(filename)) {
                    val index = filename.lastIndexOf('.')
                    if (index >= 0) {
                        val suffix = filename.substring(index)
                        if (!fileGroupMap.containsKey(suffix)) {
                            fileGroupMap[suffix] = fileObj.get("entry-size").asLong
                            val fileList = JsonArray()
                            fileList.add(file)
                            fileListGroup[suffix] = fileList
                        } else {
                            fileGroupMap[suffix] = (fileGroupMap[suffix] ?: 0L) + fileObj.get("entry-size").asLong
                            fileListGroup[suffix]?.add(file)
                        }
                    } else {
                        otherFilesSize += fileObj.get("entry-size").asLong
                        otherFiles.add(file)
                    }
                }
            }

            val fileGroupList: MutableList<Pair<String, Long>> = ArrayList()
            for ((key, value) in fileGroupMap) {
                fileGroupList.add(Pair.of(key, value))
            }
            Collections.sort(
                fileGroupList,
                Comparator { pair1, pair2 ->
                    when {
                        pair1.second > pair2.second -> -1
                        pair1.second < pair2.second -> 1
                        else -> 0
                    }
                },
            )

            val items = JsonArray()
            for (pair in fileGroupList) {
                val obj = JsonObject()
                obj.addProperty("suffix", pair.first)
                obj.addProperty("total-size", pair.second)
                obj.add("files", fileListGroup[pair.first])
                items.add(obj)
            }
            val other = JsonObject()
            other.addProperty("suffix", "others")
            other.addProperty("total-size", otherFilesSize)
            other.add("files", otherFiles)
            jsonObject.remove("entries")
            jsonObject.add("entries", items)
        }

        private fun formatManifestAnalyzeTask(jsonObject: JsonObject) {
            val manifest = jsonObject.getAsJsonObject("manifest")
            val attribute: MutableMap<String, String> = HashMap()

            if (manifest.has("package")) {
                attribute["package"] = manifest.get("package").asString
            }
            if (manifest.has("android:versionCode")) {
                attribute["android:versionCode"] = manifest.get("android:versionCode").asString
            }
            if (manifest.has("android:versionName")) {
                attribute["android:versionName"] = manifest.get("android:versionName").asString
            }

            if (manifest.has("uses-sdk")) {
                val sdks = manifest.getAsJsonArray("uses-sdk")
                if (sdks.size() > 0) {
                    val sdk = sdks[0].asJsonObject
                    if (sdk.has("android:minSdkVersion")) {
                        attribute["android:minSdkVersion"] = sdk.get("android:minSdkVersion").asString
                    }
                    if (sdk.has("android:targetSdkVersion")) {
                        attribute["android:targetSdkVersion"] = sdk.get("android:targetSdkVersion").asString
                    }
                }
            }

            if (manifest.has("application")) {
                val applications = manifest.getAsJsonArray("application")
                if (applications.size() > 0) {
                    val application = applications[0].asJsonObject
                    if (application.has("meta-data")) {
                        val metaDatas = application.getAsJsonArray("meta-data")
                        for (metaData in metaDatas) {
                            val obj = metaData.asJsonObject
                            if (obj.has("android:name") && obj.has("android:value")) {
                                val name = obj.get("android:name").asString
                                val value = obj.get("android:value").asString
                                when (name) {
                                    "com.kernelflux.mm.BuildInfo.CLIENT_VERSION" -> attribute["CLIENT_VERSION"] = value
                                    "com.kernelflux.mm.BuildInfo.BUILD_TAG" -> attribute["BUILD_TAG"] = value
                                    "com.kernelflux.mm.BuildInfo.BUILD_SVNPATH" -> attribute["BUILD_SVNPATH"] = value
                                    "com.kernelflux.mm.BuildInfo.BUILD_REV" -> attribute["BUILD_REV"] = value
                                }
                            }
                        }
                    }
                }
            }

            jsonObject.remove("manifest")
            val obj = JsonObject()
            for ((key, value) in attribute) {
                obj.addProperty(key, value)
            }
            jsonObject.add("manifest", obj)
        }

        private fun formatMethodCountTask(jsonObject: JsonObject, config: JsonObject?) {
            val groups = config?.getAsJsonArray("group")
            val defMethodMap: MutableMap<String, Int> = HashMap()
            val refMethodMap: MutableMap<String, Int> = HashMap()

            val dexFiles = jsonObject.getAsJsonArray("dex-files")
            for (entry in dexFiles) {
                val dexFile = entry.asJsonObject
                var defGroups: JsonArray? = null
                if (dexFile.has("internal-packages")) {
                    defGroups = dexFile.getAsJsonArray("internal-packages")
                } else if (dexFile.has("internal-classes")) {
                    defGroups = dexFile.getAsJsonArray("internal-classes")
                }
                if (defGroups != null) {
                    for (group in defGroups) {
                        val obj = group.asJsonObject
                        val name = obj.get("name").asString
                        defMethodMap[name] = obj.get("methods").asInt
                        if (!refMethodMap.containsKey(name)) {
                            refMethodMap[name] = 0
                        }
                    }
                }
                var refGroups: JsonArray? = null
                if (dexFile.has("external-packages")) {
                    refGroups = dexFile.getAsJsonArray("external-packages")
                } else if (dexFile.has("external-classes")) {
                    refGroups = dexFile.getAsJsonArray("external-classes")
                }
                if (refGroups != null) {
                    for (group in refGroups) {
                        val obj = group.asJsonObject
                        val name = obj.get("name").asString
                        refMethodMap[name] = (refMethodMap[name] ?: 0) + obj.get("methods").asInt
                        if (!defMethodMap.containsKey(name)) {
                            defMethodMap[name] = 0
                        }
                    }
                }
            }

            val groupMap: MutableMap<String, Pair<Int, Int>> = HashMap()
            if (groups != null) {
                val groupPattern: MutableMap<String, String> = HashMap()
                for (group in groups) {
                    val obj = group.asJsonObject
                    groupPattern[obj.get("name").asString] = obj.get("package").asString
                }
                val groupDefMap: MutableMap<String, Int> = HashMap()
                val groupRefMap: MutableMap<String, Int> = HashMap()
                groupDefMap["[others]"] = 0
                groupRefMap["[others]"] = 0

                for (pkg in defMethodMap.keys) {
                    var other = true
                    for (key in groupPattern.keys) {
                        var groupValue = groupPattern[key].orEmpty()
                        var groupName = key
                        val index = groupValue.indexOf('$')
                        if (index >= 0) {
                            groupValue = groupValue.substring(0, index)
                        }
                        if (pkg.startsWith(groupValue)) {
                            if (index >= 0) {
                                groupName = pkg.substring(index)
                                val nextIndex = groupName.indexOf('.')
                                if (nextIndex >= 0) {
                                    groupName = groupName.substring(0, nextIndex)
                                }
                                groupName = key.replace("$", groupName)
                            }
                            groupDefMap[groupName] = (groupDefMap[groupName] ?: 0) + (defMethodMap[pkg] ?: 0)
                            other = false
                        }
                    }
                    if (other) {
                        groupDefMap["[others]"] = (groupDefMap["[others]"] ?: 0) + (defMethodMap[pkg] ?: 0)
                    }
                }

                for (pkg in refMethodMap.keys) {
                    var other = true
                    for (key in groupPattern.keys) {
                        var groupValue = groupPattern[key].orEmpty()
                        var groupName = key
                        val index = groupValue.indexOf('$')
                        if (index >= 0) {
                            groupValue = groupValue.substring(0, index)
                        }
                        if (pkg.startsWith(groupValue)) {
                            if (index >= 0) {
                                groupName = pkg.substring(index)
                                val nextIndex = groupName.indexOf('.')
                                if (nextIndex >= 0) {
                                    groupName = groupName.substring(0, nextIndex)
                                }
                                groupName = key.replace("$", groupName)
                            }
                            groupRefMap[groupName] = (groupRefMap[groupName] ?: 0) + (refMethodMap[pkg] ?: 0)
                            other = false
                        }
                    }
                    if (other) {
                        groupRefMap["[others]"] = (groupRefMap["[others]"] ?: 0) + (refMethodMap[pkg] ?: 0)
                    }
                }

                for (pkg in groupDefMap.keys) {
                    groupMap[pkg] = Pair.of(groupDefMap[pkg] ?: 0, groupRefMap[pkg] ?: 0)
                }
            } else {
                for (pkg in defMethodMap.keys) {
                    groupMap[pkg] = Pair.of(defMethodMap[pkg] ?: 0, refMethodMap[pkg] ?: 0)
                }
            }

            val keys = ArrayList(groupMap.keys)
            Collections.sort(
                keys,
                Comparator { left, right ->
                    val pair1 = groupMap[left] ?: Pair.of(0, 0)
                    val pair2 = groupMap[right] ?: Pair.of(0, 0)
                    val total1 = pair1.first + pair1.second
                    val total2 = pair2.first + pair2.second
                    when {
                        total1 > total2 -> -1
                        total1 < total2 -> 1
                        else -> 0
                    }
                },
            )

            jsonObject.remove("dex-files")
            var totalMethods = 0L
            val groupArray = JsonArray()
            for (group in keys) {
                val groupObj = JsonObject()
                groupObj.addProperty("name", group)
                val pair = groupMap[group] ?: Pair.of(0, 0)
                totalMethods += (pair.first + pair.second).toLong()
                groupObj.addProperty("method-count", pair.first + pair.second)
                groupArray.add(groupObj)
            }
            jsonObject.addProperty("total-methods", totalMethods)
            jsonObject.add("groups", groupArray)
        }

        private fun formatUnusedResourcesTask(jsonObject: JsonObject) {
            val resources = jsonObject.getAsJsonArray("unused-resources")
            val group: MutableMap<String, MutableList<String>> = HashMap()
            jsonObject.addProperty("total-count", resources.size())
            for (resource in resources) {
                val res = resource.asString
                val type = res.substring(0, res.indexOf('.', 2))
                if (!group.containsKey(type)) {
                    group[type] = ArrayList()
                }
                group[type]?.add(res)
            }
            jsonObject.remove("unused-resources")
            for ((key, value) in group) {
                val list = JsonArray()
                for (res in value) {
                    list.add(res)
                }
                jsonObject.add(key, list)
            }
        }

        private fun formatCountClass(jsonObject: JsonObject, config: JsonObject?) {
            val groups = config?.getAsJsonArray("group")
            val dexFiles = jsonObject.getAsJsonArray("dex-files")
            val pkgMap: MutableMap<String, MutableSet<String>> = HashMap()

            for (entry in dexFiles) {
                val dexFile = entry.asJsonObject
                val pkgs = dexFile.get("packages").asJsonArray
                for (pkg in pkgs) {
                    val pkgObj = pkg.asJsonObject
                    val pkgName = pkgObj.get("package").asString
                    if (!pkgMap.containsKey(pkgName)) {
                        pkgMap[pkgName] = HashSet()
                    }
                    val classes = pkgObj.getAsJsonArray("classes")
                    for (clazz in classes) {
                        pkgMap[pkgName]?.add(clazz.asString)
                    }
                }
            }

            val groupDefMap: MutableMap<String, Int> = HashMap()
            if (groups != null) {
                val groupPattern: MutableMap<String, String> = HashMap()
                for (group in groups) {
                    val obj = group.asJsonObject
                    groupPattern[obj.get("name").asString] = obj.get("package").asString
                }
                groupDefMap["[others]"] = 0
                for (pkg in pkgMap.keys) {
                    var other = true
                    for (key in groupPattern.keys) {
                        var groupValue = groupPattern[key].orEmpty()
                        var groupName = key
                        val index = groupValue.indexOf('$')
                        if (index >= 0) {
                            groupValue = groupValue.substring(0, index)
                        }
                        if (pkg.startsWith(groupValue)) {
                            if (index >= 0) {
                                groupName = pkg.substring(index)
                                val nextIndex = groupName.indexOf('.')
                                if (nextIndex >= 0) {
                                    groupName = groupName.substring(0, nextIndex)
                                }
                                groupName = key.replace("$", groupName)
                            }
                            groupDefMap[groupName] = (groupDefMap[groupName] ?: 0) + (pkgMap[pkg]?.size ?: 0)
                            other = false
                        }
                    }
                    if (other) {
                        groupDefMap["[others]"] = (groupDefMap["[others]"] ?: 0) + (pkgMap[pkg]?.size ?: 0)
                    }
                }
            } else {
                for (pkg in pkgMap.keys) {
                    groupDefMap[pkg] = pkgMap[pkg]?.size ?: 0
                }
            }

            val keys = ArrayList(groupDefMap.keys)
            Collections.sort(
                keys,
                Comparator { left, right ->
                    val total1 = groupDefMap[left] ?: 0
                    val total2 = groupDefMap[right] ?: 0
                    when {
                        total1 > total2 -> -1
                        total1 < total2 -> 1
                        else -> 0
                    }
                },
            )

            jsonObject.remove("dex-files")
            var totalClasses = 0L
            val groupArray = JsonArray()
            for (group in keys) {
                val groupObj = JsonObject()
                groupObj.addProperty("name", group)
                totalClasses += (groupDefMap[group] ?: 0).toLong()
                groupObj.addProperty("class-count", groupDefMap[group] ?: 0)
                groupArray.add(groupObj)
            }
            jsonObject.addProperty("total-classes", totalClasses)
            jsonObject.add("groups", groupArray)
        }
    }
}

