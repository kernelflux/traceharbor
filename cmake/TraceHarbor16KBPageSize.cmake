if(NOT ANDROID)
    return()
endif()

if(NOT DEFINED ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES)
    set(ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES ON CACHE BOOL "Enable Android flexible page sizes" FORCE)
endif()

set(TRACEHARBOR_16KB_PAGE_SIZE_LD_FLAGS
        "-Wl,-z,common-page-size=16384"
        "-Wl,-z,max-page-size=16384")

foreach(TRACEHARBOR_LINKER_FLAG_VAR
        CMAKE_SHARED_LINKER_FLAGS
        CMAKE_MODULE_LINKER_FLAGS)
    set(_traceharbor_existing_flags "${${TRACEHARBOR_LINKER_FLAG_VAR}}")
    foreach(_traceharbor_page_size_flag ${TRACEHARBOR_16KB_PAGE_SIZE_LD_FLAGS})
        string(FIND " ${_traceharbor_existing_flags} " " ${_traceharbor_page_size_flag} " _traceharbor_flag_index)
        if(_traceharbor_flag_index EQUAL -1)
            set(_traceharbor_existing_flags
                    "${_traceharbor_existing_flags} ${_traceharbor_page_size_flag}")
        endif()
    endforeach()
    set(${TRACEHARBOR_LINKER_FLAG_VAR} "${_traceharbor_existing_flags}")
endforeach()
