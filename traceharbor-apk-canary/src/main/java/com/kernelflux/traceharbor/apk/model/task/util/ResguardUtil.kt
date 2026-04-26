package com.kernelflux.traceharbor.apk.model.task.util

import com.kernelflux.traceharbor.javalib.util.Log
import com.kernelflux.traceharbor.javalib.util.Util
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.regex.Pattern

object ResguardUtil {
    private const val TAG = "TraceHarbor.ResguardUtil"
    private val RESOURCE_ID_PATTERN = Pattern.compile("^(\\S+\\.)*R\\.(\\S+?)\\.(\\S+)")

    @JvmStatic
    @Throws(IOException::class)
    fun readResMappingTxtFile(
        resMappingTxt: File?,
        resDirMap: MutableMap<String, String>?,
        resguardMap: MutableMap<String, String>?,
    ) {
        if (resMappingTxt != null) {
            val bufferedReader = BufferedReader(FileReader(resMappingTxt))
            try {
                var line = bufferedReader.readLine()
                var readResStart = false
                var readPathStart = false
                while (line != null) {
                    if (line.trim { it <= ' ' } == "res path mapping:") {
                        readPathStart = true
                    } else if (line.trim { it <= ' ' } == "res id mapping:") {
                        readResStart = true
                        readPathStart = false
                    } else if (readPathStart && resDirMap != null) {
                        val columns = line.split("->".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        if (columns.size == 2) {
                            val before = columns[0].trim { it <= ' ' }
                            val after = columns[1].trim { it <= ' ' }
                            if (!Util.isNullOrNil(before) && !Util.isNullOrNil(after)) {
                                Log.d(TAG, "%s->%s", before, after)
                                resDirMap[after] = before
                            }
                        }
                    } else if (readResStart && resguardMap != null) {
                        val columns = line.split("->".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        if (columns.size == 2) {
                            val before = parseResourceNameFromResguard(columns[0].trim { it <= ' ' })
                            val after = parseResourceNameFromResguard(columns[1].trim { it <= ' ' })
                            if (!Util.isNullOrNil(before) && !Util.isNullOrNil(after)) {
                                Log.d(TAG, "%s->%s", before, after)
                                resguardMap[after] = before
                            }
                        }
                    }
                    line = bufferedReader.readLine()
                }
            } finally {
                bufferedReader.close()
            }
        }
    }

    private fun parseResourceNameFromResguard(resName: String?): String {
        if (Util.isNullOrNil(resName)) return ""
        val matcher = RESOURCE_ID_PATTERN.matcher(resName)
        return if (matcher.find()) {
            val builder = StringBuilder()
            builder.append("R.")
            builder.append(matcher.group(2))
            /*
                The resource ID from resguard is read from ARSC file, which format is package-like
                (for example: R.style.Theme.AppCompat.Light.DarkActionBar). We should convert it as the regular format
                in code (for example: R.style.Theme_AppCompat_Light_DarkActionBar).
             */
            builder.append('.')
            builder.append(matcher.group(3).replace('.', '_'))
            builder.toString()
        } else {
            ""
        }
    }
}

