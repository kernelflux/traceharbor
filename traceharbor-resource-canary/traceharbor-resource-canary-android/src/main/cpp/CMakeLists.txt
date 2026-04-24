cmake_minimum_required(VERSION 3.10.2)
project(traceharbor-resource-canary-android)

include(${CMAKE_CURRENT_LIST_DIR}/../../../../../cmake/TraceHarbor16KBPageSize.cmake)

set(CMAKE_CXX_STANDARD 17)

add_subdirectory(memory_util)