package com.kernelflux.traceharbor.resource.analyzer.utils

import com.squareup.haha.perflib.RootObj
import com.squareup.haha.perflib.Snapshot
import com.squareup.haha.trove.THashMap
import com.squareup.haha.trove.TObjectProcedure
import java.util.concurrent.TimeUnit.NANOSECONDS

object AnalyzeUtil {
    @JvmStatic
    fun deduplicateGcRoots(snapshot: Snapshot) {
        val uniqueRootMap = THashMap<String, RootObj>()
        val gcRoots = snapshot.gcRoots
        for (root in gcRoots) {
            val key = generateRootKey(root)
            if (!uniqueRootMap.containsKey(key)) {
                uniqueRootMap[key] = root
            }
        }
        gcRoots.clear()
        uniqueRootMap.forEach(
            object : TObjectProcedure<String> {
                override fun execute(key: String): Boolean {
                    return gcRoots.add(uniqueRootMap[key])
                }
            },
        )
    }

    private fun generateRootKey(root: RootObj): String {
        return String.format("%s@0x%08x", root.rootType.name, root.id)
    }

    @JvmStatic
    fun since(analysisStartNanoTime: Long): Long {
        return NANOSECONDS.toMillis(System.nanoTime() - analysisStartNanoTime)
    }
}

