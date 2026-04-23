# TraceHarbor Hprof Analyzer

`traceharbor-hprof-analyzer` is a native C++ heap-dump analysis library used by TraceHarbor's Resource Canary pipeline.

## Why It Looks Unused

This module is not wired in as a normal Gradle dependency, so it is easy to miss when scanning `settings.gradle` or Android module dependencies.

Instead, it is pulled in from the native build of `traceharbor-resource-canary-android` via CMake:

- `traceharbor-resource-canary/traceharbor-resource-canary-android/src/main/cpp/memory_util/CMakeLists.txt`
- `add_subdirectory(../../../../../../traceharbor-hprof-analyzer traceharbor-hprof-analyzer.out)`
- `target_link_libraries(traceharbor_mem_util traceharbor_hprof_analyzer ...)`

So this module is used indirectly by Resource Canary's native memory-analysis path, not as a standalone Android or Java artifact.

## What It Does

This library provides native HPROF parsing and analysis pieces used by Resource Canary's memory tooling, including:

- heap structures
- parser and reader components
- analyzer logic
- a single native entry library exported from `lib/main/include/traceharbor_hprof_analyzer.h`

Its top-level build entry is:

- `traceharbor-hprof-analyzer/CMakeLists.txt`

## Relationship To Resource Canary

TraceHarbor currently has two different HPROF-analysis layers under Resource Canary:

1. `traceharbor-hprof-analyzer`
   Native C++ analyzer linked into the Android native memory utility path.

2. `traceharbor-resource-canary/traceharbor-resource-canary-analyzer`
   Java analyzer module based on `haha`, used for higher-level heap snapshot and leak analysis.

In other words, this module is not redundant. It is part of the lower-level native analysis stack, while `traceharbor-resource-canary-analyzer` covers the Java-side analysis flow.

## When To Touch This Module

You usually only need to modify this module when working on:

- Resource Canary native heap-dump analysis
- HPROF parsing or chain analysis in native code
- `traceharbor_mem_util` build/link failures
- native memory-analysis performance or ABI issues

If you are only working on AGP, publishing, Java plugin code, or top-level Android integration, you usually do not need to change this module.
