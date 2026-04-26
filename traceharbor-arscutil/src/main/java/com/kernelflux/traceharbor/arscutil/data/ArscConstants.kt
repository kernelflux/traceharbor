package com.kernelflux.traceharbor.arscutil.data

object ArscConstants {
    const val RES_TABLE_TYPE_SPEC_TYPE: Short = 514
    const val RES_TABLE_TYPE_TYPE: Short = 513

    const val NO_ENTRY_INDEX: Int = -0x1

    const val RES_STRING_POOL_SORTED_FLAG = 0x1
    const val RES_STRING_POOL_UTF8_FLAG = 0x0100

    const val RES_TABLE_ENTRY_FLAG_COMPLEX: Short = 0x0001
    const val RES_TABLE_ENTRY_FLAG_PUBLIC: Short = 0x0002

    const val RES_VALUE_DATA_TYPE_NULL = 0
    const val RES_VALUE_DATA_TYPE_REFERENCE = 0x01
    const val RES_VALUE_DATA_TYPE_ATTRIBUTE = 0x02
    const val RES_VALUE_DATA_TYPE_STRING = 0x03
    const val RES_VALUE_DATA_TYPE_FLOAT = 0x04
    const val RES_VALUE_DATA_TYPE_DIMENSION = 0x05
    const val RES_VALUE_DATA_TYPE_FRACTION = 0x06
    const val RES_VALUE_DATA_TYPE_DYNAMIC_REFERENCE = 0x07
    const val RES_VALUE_DATA_TYPE_DYNAMIC_ATTRIBUTE = 0x08

    const val RES_VALUE_DATA_TYPE_FIRST_INT = 0x10
    const val RES_VALUE_DATA_TYPE_INT_DEC = 0x10
    const val RES_VALUE_DATA_TYPE_INT_HEX = 0x11
    const val RES_VALUE_DATA_TYPE_INT_BOOLEAN = 0x12

    const val RES_VALUE_DATA_TYPE_FIRST_COLOR_INT = 0x1c
    const val RES_VALUE_DATA_TYPE_INT_COLOR_ARGB8 = 0x1c
    const val RES_VALUE_DATA_TYPE_INT_COLOR_RGB8 = 0x1d
    const val RES_VALUE_DATA_TYPE_INT_COLOR_ARGB4 = 0x1e
    const val RES_VALUE_DATA_TYPE_INT_COLOR_RGB4 = 0x1f

    const val RES_VALUE_DATA_TYPE_LAST_COLOR_INT = 0x1f
    const val RES_VALUE_DATA_TYPE_LAST_INT = 0x1f
}
