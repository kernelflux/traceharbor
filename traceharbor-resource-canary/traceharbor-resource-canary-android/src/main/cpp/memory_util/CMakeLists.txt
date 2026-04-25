cmake_minimum_required(VERSION 3.10.2)
project(traceharbor-resource-canary-android)

set(CMAKE_CXX_STANDARD 17)

add_subdirectory(../../../../../../traceharbor-hprof-analyzer traceharbor-hprof-analyzer.out)

add_library(traceharbor_mem_util SHARED
        excludes/excludes.cpp symbol/dlsym/dlsym.cpp symbol/symbol.cpp log.cpp memory_util.cpp)

find_library(
        log-lib
        log)

target_include_directories(
        traceharbor_mem_util
        PRIVATE ${EXT_DEP}/include
        PRIVATE ${EXT_DEP}/include/backtrace/common)

target_link_libraries(
        traceharbor_mem_util
        traceharbor_hprof_analyzer
        ${log-lib}
        ${EXT_DEP}/lib/${ANDROID_ABI}/libenhance_dlsym.a
        ${EXT_DEP}/lib/${ANDROID_ABI}/libsemi_dlfcn.a)

if(${ANDROID_ABI} MATCHES "armeabi-v7a" OR ${ANDROID_ABI} MATCHES "arm64-v8a")
    target_link_libraries(
            traceharbor_mem_util
            ${EXT_DEP}/lib/${ANDROID_ABI}/libtraceharbor-backtrace.so
    )
endif()


