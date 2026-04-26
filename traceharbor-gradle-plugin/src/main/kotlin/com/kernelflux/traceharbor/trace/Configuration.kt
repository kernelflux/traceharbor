package com.kernelflux.traceharbor.trace

import com.kernelflux.traceharbor.javalib.util.FileUtil
import com.kernelflux.traceharbor.javalib.util.Util
import com.kernelflux.traceharbor.trace.retrace.MappingCollector
import java.util.ArrayList
import java.util.HashSet

class Configuration {
    var packageName: String? = null
    var mappingDir: String = ""
    var baseMethodMapPath: String = ""
    var methodMapFilePath: String = ""
    var ignoreMethodMapFilePath: String = ""
    var blockListFilePath: String = ""
    var traceClassOut: String = ""
    var skipCheckClass: Boolean = false
    var blockSet: HashSet<String> = HashSet()

    /**
     * Extra block-list lines (already in ProGuard `-keeppackage` / `-keepclass`
     * format) appended after `TraceBuildConstants.DEFAULT_BLOCK_TRACE` and the contents
     * of `blockListFilePath`. Populated from the inline DSL
     * (`ignorePackages` / `ignoreClasses`).
     */
    var extraBlockLines: List<String> = ArrayList()

    constructor()

    internal constructor(
        packageName: String?,
        mappingDir: String?,
        baseMethodMapPath: String?,
        methodMapFilePath: String?,
        ignoreMethodMapFilePath: String?,
        blockListFilePath: String?,
        traceClassOut: String?,
        skipCheckClass: Boolean,
        extraBlockLines: List<String>?
    ) {
        this.packageName = packageName
        this.mappingDir = Util.nullAsNil(mappingDir)
        this.baseMethodMapPath = Util.nullAsNil(baseMethodMapPath)
        this.methodMapFilePath = Util.nullAsNil(methodMapFilePath)
        this.ignoreMethodMapFilePath = Util.nullAsNil(ignoreMethodMapFilePath)
        this.blockListFilePath = Util.nullAsNil(blockListFilePath)
        this.traceClassOut = Util.nullAsNil(traceClassOut)
        this.skipCheckClass = skipCheckClass
        if (extraBlockLines != null) {
            this.extraBlockLines = ArrayList(extraBlockLines)
        }
    }

    fun parseBlockFile(processor: MappingCollector): Int {
        val extra = StringBuilder()
        if (extraBlockLines.isNotEmpty()) {
            for (line in extraBlockLines) {
                if (line.isEmpty()) {
                    continue
                }
                extra.append(line).append('\n')
            }
        }
        val blockStr = TraceBuildConstants.DEFAULT_BLOCK_TRACE +
            FileUtil.readFileAsString(blockListFilePath) +
            if (extra.isNotEmpty()) ("\n$extra") else ""

        val blockArray = blockStr.trim { it <= ' ' }.replace("/", ".").replace("\r", "").split("\n")

        for (item in blockArray) {
            var block = item
            if (block.isEmpty()) {
                continue
            }
            if (block.startsWith("#")) {
                continue
            }
            if (block.startsWith("[")) {
                continue
            }

            if (block.startsWith("-keepclass ")) {
                block = block.replace("-keepclass ", "")
                blockSet.add(processor.proguardClassName(block, block))
            } else if (block.startsWith("-keeppackage ")) {
                block = block.replace("-keeppackage ", "")
                blockSet.add(processor.proguardPackageName(block, block))
            }
        }
        return blockSet.size
    }

    override fun toString(): String {
        return "\n# Configuration\n" +
            "|* packageName:\t$packageName\n" +
            "|* mappingDir:\t$mappingDir\n" +
            "|* baseMethodMapPath:\t$baseMethodMapPath\n" +
            "|* methodMapFilePath:\t$methodMapFilePath\n" +
            "|* ignoreMethodMapFilePath:\t$ignoreMethodMapFilePath\n" +
            "|* blockListFilePath:\t$blockListFilePath\n" +
            "|* traceClassOut:\t$traceClassOut\n"
    }

    class Builder {
        var packageName: String? = null
        var mappingPath: String? = null
        var baseMethodMap: String? = null
        var methodMapFile: String? = null
        var ignoreMethodMapFile: String? = null
        var blockListFile: String? = null
        var traceClassOut: String? = null
        var skipCheckClass: Boolean = false
        var extraBlockLines: List<String> = ArrayList()

        fun setPackageName(packageName: String?): Builder {
            this.packageName = packageName
            return this
        }

        fun setMappingPath(mappingPath: String?): Builder {
            this.mappingPath = mappingPath
            return this
        }

        fun setBaseMethodMap(baseMethodMap: String?): Builder {
            this.baseMethodMap = baseMethodMap
            return this
        }

        fun setTraceClassOut(traceClassOut: String?): Builder {
            this.traceClassOut = traceClassOut
            return this
        }

        fun setMethodMapFilePath(methodMapDir: String?): Builder {
            methodMapFile = methodMapDir
            return this
        }

        fun setIgnoreMethodMapFilePath(methodMapDir: String?): Builder {
            ignoreMethodMapFile = methodMapDir
            return this
        }

        fun setBlockListFile(blockListFile: String?): Builder {
            this.blockListFile = blockListFile
            return this
        }

        fun setSkipCheckClass(skipCheckClass: Boolean): Builder {
            this.skipCheckClass = skipCheckClass
            return this
        }

        fun setExtraBlockLines(extraBlockLines: List<String>?): Builder {
            this.extraBlockLines = extraBlockLines?.let { ArrayList(it) } ?: ArrayList()
            return this
        }

        fun build(): Configuration {
            return Configuration(
                packageName,
                mappingPath,
                baseMethodMap,
                methodMapFile,
                ignoreMethodMapFile,
                blockListFile,
                traceClassOut,
                skipCheckClass,
                extraBlockLines
            )
        }
    }
}

