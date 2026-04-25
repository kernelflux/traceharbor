cmake_minimum_required(VERSION 3.4.1)

project(TraceHarborTraffic)

include(${CMAKE_CURRENT_LIST_DIR}/../cmake/TraceHarbor16KBPageSize.cmake)

include_directories(src/main/cpp)

add_library(traceharbor-traffic
            SHARED
            src/main/cpp/TraceHarborTraffic.cc
            src/main/cpp/TrafficCollector.cc
            src/main/cpp/util/blocking_queue.h
            src/main/cpp/util/managed_jnienv.cc
             )


target_include_directories(
        traceharbor-traffic
        PRIVATE ${EXT_DEP}/include
        PRIVATE ${EXT_DEP}/include/backtrace
        PRIVATE ${EXT_DEP}/include/backtrace/common
)


TARGET_INCLUDE_DIRECTORIES(traceharbor-traffic PRIVATE ${EXT_DEP}/include)


find_library( log-lib
              log )


target_link_libraries(traceharbor-traffic
        PRIVATE ${log-lib}
        PRIVATE ${EXT_DEP}/lib/${ANDROID_ABI}/libxhook.a
        PRIVATE ${EXT_DEP}/lib/${ANDROID_ABI}/libsemi_dlfcn.a
        PRIVATE ${EXT_DEP}/lib/${ANDROID_ABI}/libtraceharbor-backtrace.so
                    )

set_target_properties(traceharbor-traffic PROPERTIES
        CXX_STANDARD_REQUIRED ON
        CXX_STANDARD 17
        CXX_EXTENSIONS OFF
        )