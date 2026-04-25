package com.kernelflux.traceharbor.trace;

import com.kernelflux.traceharbor.javalib.util.FileUtil;
import com.kernelflux.traceharbor.javalib.util.Util;
import com.kernelflux.traceharbor.trace.retrace.MappingCollector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class Configuration {

    public String packageName;
    public String mappingDir;
    public String baseMethodMapPath;
    public String methodMapFilePath;
    public String ignoreMethodMapFilePath;
    public String blockListFilePath;
    public String traceClassOut;
    public boolean skipCheckClass;
    public HashSet<String> blockSet = new HashSet<>();

    /**
     * Extra block-list lines (already in ProGuard {@code -keeppackage} / {@code -keepclass}
     * format) appended after {@link TraceBuildConstants#DEFAULT_BLOCK_TRACE} and the contents
     * of {@link #blockListFilePath}. Populated from the inline DSL
     * ({@code ignorePackages} / {@code ignoreClasses}).
     */
    public List<String> extraBlockLines = new ArrayList<>();

    public Configuration() {
    }

    Configuration(String packageName, String mappingDir, String baseMethodMapPath, String methodMapFilePath,
                  String ignoreMethodMapFilePath, String blockListFilePath, String traceClassOut, boolean skipCheckClass,
                  List<String> extraBlockLines) {
        this.packageName = packageName;
        this.mappingDir = Util.nullAsNil(mappingDir);
        this.baseMethodMapPath = Util.nullAsNil(baseMethodMapPath);
        this.methodMapFilePath = Util.nullAsNil(methodMapFilePath);
        this.ignoreMethodMapFilePath = Util.nullAsNil(ignoreMethodMapFilePath);
        this.blockListFilePath = Util.nullAsNil(blockListFilePath);
        this.traceClassOut = Util.nullAsNil(traceClassOut);
        this.skipCheckClass = skipCheckClass;
        if (extraBlockLines != null) {
            this.extraBlockLines = new ArrayList<>(extraBlockLines);
        }
    }

    public int parseBlockFile(MappingCollector processor) {
        StringBuilder extra = new StringBuilder();
        if (extraBlockLines != null && !extraBlockLines.isEmpty()) {
            for (String line : extraBlockLines) {
                if (line == null || line.isEmpty()) {
                    continue;
                }
                extra.append(line).append('\n');
            }
        }
        String blockStr = TraceBuildConstants.DEFAULT_BLOCK_TRACE
                + FileUtil.readFileAsString(blockListFilePath)
                + (extra.length() > 0 ? ("\n" + extra) : "");

        String[] blockArray = blockStr.trim().replace("/", ".").replace("\r", "").split("\n");

        if (blockArray != null) {
            for (String block : blockArray) {
                if (block.length() == 0) {
                    continue;
                }
                if (block.startsWith("#")) {
                    continue;
                }
                if (block.startsWith("[")) {
                    continue;
                }

                if (block.startsWith("-keepclass ")) {
                    block = block.replace("-keepclass ", "");
                    blockSet.add(processor.proguardClassName(block, block));
                } else if (block.startsWith("-keeppackage ")) {
                    block = block.replace("-keeppackage ", "");
                    blockSet.add(processor.proguardPackageName(block, block));
                }
            }
        }
        return blockSet.size();
    }

    @Override
    public String toString() {
        return "\n# Configuration" + "\n"
                + "|* packageName:\t" + packageName + "\n"
                + "|* mappingDir:\t" + mappingDir + "\n"
                + "|* baseMethodMapPath:\t" + baseMethodMapPath + "\n"
                + "|* methodMapFilePath:\t" + methodMapFilePath + "\n"
                + "|* ignoreMethodMapFilePath:\t" + ignoreMethodMapFilePath + "\n"
                + "|* blockListFilePath:\t" + blockListFilePath + "\n"
                + "|* traceClassOut:\t" + traceClassOut + "\n";
    }

    public static class Builder {

        public String packageName;
        public String mappingPath;
        public String baseMethodMap;
        public String methodMapFile;
        public String ignoreMethodMapFile;
        public String blockListFile;
        public String traceClassOut;
        public boolean skipCheckClass = false;
        public List<String> extraBlockLines = new ArrayList<>();

        public Builder setPackageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder setMappingPath(String mappingPath) {
            this.mappingPath = mappingPath;
            return this;
        }

        public Builder setBaseMethodMap(String baseMethodMap) {
            this.baseMethodMap = baseMethodMap;
            return this;
        }

        public Builder setTraceClassOut(String traceClassOut) {
            this.traceClassOut = traceClassOut;
            return this;
        }

        public Builder setMethodMapFilePath(String methodMapDir) {
            methodMapFile = methodMapDir;
            return this;
        }

        public Builder setIgnoreMethodMapFilePath(String methodMapDir) {
            ignoreMethodMapFile = methodMapDir;
            return this;
        }

        public Builder setBlockListFile(String blockListFile) {
            this.blockListFile = blockListFile;
            return this;
        }

        public Builder setSkipCheckClass(boolean skipCheckClass) {
            this.skipCheckClass = skipCheckClass;
            return this;
        }

        public Builder setExtraBlockLines(List<String> extraBlockLines) {
            this.extraBlockLines = (extraBlockLines != null) ? new ArrayList<>(extraBlockLines) : new ArrayList<String>();
            return this;
        }

        public Configuration build() {
            return new Configuration(packageName, mappingPath, baseMethodMap, methodMapFile, ignoreMethodMapFile, blockListFile, traceClassOut, skipCheckClass, extraBlockLines);
        }

    }
}
