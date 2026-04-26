package com.kernelflux.traceharbor.resource.hproflib

object HprofConstants {
    const val RECORD_TAG_UNKNOWN: Int = 0x0
    const val RECORD_TAG_STRING: Int = 0x1
    const val RECORD_TAG_LOAD_CLASS: Int = 0x2
    const val RECORD_TAG_UNLOAD_CLASS: Int = 0x3
    const val RECORD_TAG_STACK_FRAME: Int = 0x4
    const val RECORD_TAG_STACK_TRACE: Int = 0x5
    const val RECORD_TAG_ALLOC_SITES: Int = 0x6
    const val RECORD_TAG_HEAP_SUMMARY: Int = 0x7
    const val RECORD_TAG_START_THREAD: Int = 0xa
    const val RECORD_TAG_END_THREAD: Int = 0xb
    const val RECORD_TAG_HEAP_DUMP: Int = 0xc
    const val RECORD_TAG_HEAP_DUMP_SEGMENT: Int = 0x1c
    const val RECORD_TAG_HEAP_DUMP_END: Int = 0x2c
    const val RECORD_TAG_CPU_SAMPLES: Int = 0xd
    const val RECORD_TAG_CONTROL_SETTINGS: Int = 0xe

    const val HEAPDUMP_ROOT_UNKNOWN: Int = 0xff
    const val HEAPDUMP_ROOT_JNI_GLOBAL: Int = 0x1
    const val HEAPDUMP_ROOT_JNI_LOCAL: Int = 0x2
    const val HEAPDUMP_ROOT_JAVA_FRAME: Int = 0x3
    const val HEAPDUMP_ROOT_NATIVE_STACK: Int = 0x4
    const val HEAPDUMP_ROOT_STICKY_CLASS: Int = 0x5
    const val HEAPDUMP_ROOT_THREAD_BLOCK: Int = 0x6
    const val HEAPDUMP_ROOT_MONITOR_USED: Int = 0x7
    const val HEAPDUMP_ROOT_THREAD_OBJECT: Int = 0x8
    const val HEAPDUMP_ROOT_CLASS_DUMP: Int = 0x20
    const val HEAPDUMP_ROOT_INSTANCE_DUMP: Int = 0x21
    const val HEAPDUMP_ROOT_OBJECT_ARRAY_DUMP: Int = 0x22
    const val HEAPDUMP_ROOT_PRIMITIVE_ARRAY_DUMP: Int = 0x23
    const val HEAPDUMP_ROOT_HEAP_DUMP_INFO: Int = 0xfe
    const val HEAPDUMP_ROOT_INTERNED_STRING: Int = 0x89
    const val HEAPDUMP_ROOT_FINALIZING: Int = 0x8a
    const val HEAPDUMP_ROOT_DEBUGGER: Int = 0x8b
    const val HEAPDUMP_ROOT_REFERENCE_CLEANUP: Int = 0x8c
    const val HEAPDUMP_ROOT_VM_INTERNAL: Int = 0x8d
    const val HEAPDUMP_ROOT_JNI_MONITOR: Int = 0x8e
    const val HEAPDUMP_ROOT_UNREACHABLE: Int = 0x90
    const val HEAPDUMP_ROOT_PRIMITIVE_ARRAY_NODATA_DUMP: Int = 0xc3
}

