package com.kernelflux.traceharbor.resource.hproflib.model

enum class Type(
    private val mId: Int,
    private val mSize: Int,
) {
    OBJECT(2, 0),
    BOOLEAN(4, 1),
    CHAR(5, 2),
    FLOAT(6, 4),
    DOUBLE(7, 8),
    BYTE(8, 1),
    SHORT(9, 2),
    INT(10, 4),
    LONG(11, 8),
    ;

    fun getSize(idSize: Int): Int = if (mSize != 0) mSize else idSize

    fun getTypeId(): Int = mId

    companion object {
        private val sTypeMap: MutableMap<Int, Type> = HashMap()

        init {
            for (type in values()) {
                sTypeMap[type.mId] = type
            }
        }

        @JvmStatic
        fun getType(id: Int): Type? = sTypeMap[id]

        @JvmStatic
        fun getClassNameOfPrimitiveArray(type: Type): String {
            return when (type) {
                BOOLEAN -> "boolean[]"
                CHAR -> "char[]"
                FLOAT -> "float[]"
                DOUBLE -> "double[]"
                BYTE -> "byte[]"
                SHORT -> "short[]"
                INT -> "int[]"
                LONG -> "long[]"
                OBJECT -> throw IllegalArgumentException("OBJECT type is not a primitive type")
            }
        }
    }
}

