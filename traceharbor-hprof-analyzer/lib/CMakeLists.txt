cmake_minimum_required(VERSION 3.10.2)
project(traceharbor_hprof_analyzer)

set(CMAKE_CXX_STANDARD 17)

add_library(traceharbor_hprof_analyzer_error STATIC err/errorha.cpp)
target_include_directories(traceharbor_hprof_analyzer_error INTERFACE err/include)

add_library(traceharbor_hprof_analyzer_reader STATIC reader/reader.cpp)
target_include_directories(traceharbor_hprof_analyzer_reader INTERFACE reader/include)
target_link_libraries(traceharbor_hprof_analyzer_reader traceharbor_hprof_analyzer_error)

add_library(traceharbor_hprof_analyzer_heap STATIC heap/heap.cpp heap/primitive.cpp)
target_include_directories(traceharbor_hprof_analyzer_heap INTERFACE heap/include)
target_link_libraries(traceharbor_hprof_analyzer_heap traceharbor_hprof_analyzer_error traceharbor_hprof_analyzer_reader)

if (TEST_MODE)
    add_library(traceharbor_hprof_analyzer_parser STATIC parser/parser.cpp parser/engine.cpp)
    target_include_directories(traceharbor_hprof_analyzer_parser INTERFACE parser/include parser/internal)
    target_link_libraries(traceharbor_hprof_analyzer_parser traceharbor_hprof_analyzer_error traceharbor_hprof_analyzer_heap)
else ()
    add_library(traceharbor_hprof_analyzer_parser STATIC parser/parser.cpp parser/engine.cpp)
    target_include_directories(traceharbor_hprof_analyzer_parser INTERFACE parser/include)
    target_link_libraries(traceharbor_hprof_analyzer_parser traceharbor_hprof_analyzer_error traceharbor_hprof_analyzer_heap)
endif ()

add_library(traceharbor_hprof_analyzer_analyzer STATIC analyzer/analyzer.cpp)
target_include_directories(traceharbor_hprof_analyzer_analyzer INTERFACE analyzer/include)
target_link_libraries(traceharbor_hprof_analyzer_analyzer traceharbor_hprof_analyzer_error traceharbor_hprof_analyzer_heap)

if (TEST_MODE)
    add_library(traceharbor_hprof_analyzer SHARED main/analyzer.cpp main/chain.cpp main/heap.cpp)
    target_include_directories(traceharbor_hprof_analyzer INTERFACE main/include main/internal ../include)
    target_link_libraries(traceharbor_hprof_analyzer
            PRIVATE traceharbor_hprof_analyzer_error
            PRIVATE traceharbor_hprof_analyzer_heap
            PRIVATE traceharbor_hprof_analyzer_parser
            PRIVATE traceharbor_hprof_analyzer_analyzer)
else ()
    add_library(traceharbor_hprof_analyzer SHARED main/analyzer.cpp main/chain.cpp main/heap.cpp)
    target_include_directories(traceharbor_hprof_analyzer INTERFACE main/include ../include)
    target_link_libraries(traceharbor_hprof_analyzer
            PRIVATE traceharbor_hprof_analyzer_error
            PRIVATE traceharbor_hprof_analyzer_heap
            PRIVATE traceharbor_hprof_analyzer_parser
            PRIVATE traceharbor_hprof_analyzer_analyzer)
endif ()