package com.squareup.haha.perflib

import com.kernelflux.traceharbor.resource.common.utils.Preconditions.checkNotNull
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.charset.Charset
import java.util.Arrays.asList
import java.util.HashSet

object HahaHelper {
    private val WRAPPER_TYPES =
        HashSet(
            asList(
                Boolean::class.java.name,
                Character::class.java.name,
                Float::class.java.name,
                Double::class.java.name,
                Byte::class.java.name,
                Short::class.java.name,
                Integer::class.java.name,
                Long::class.java.name,
            ),
        )

    @JvmStatic
    fun fieldToString(entry: Map.Entry<Field, Any?>): String {
        return fieldToString(entry.key, entry.value)
    }

    @JvmStatic
    fun fieldToString(fieldValue: ClassInstance.FieldValue): String {
        return fieldToString(fieldValue.field, fieldValue.value)
    }

    @JvmStatic
    fun fieldToString(field: Field, value: Any?): String {
        return field.name + " = " + value
    }

    @JvmStatic
    fun threadName(holder: Instance): String {
        val values = classInstanceValues(holder)
        val nameField: Any? = fieldValue(values, "name")
        if (nameField == null) {
            return "Thread name not available"
        }
        return asString(nameField)
    }

    @JvmStatic
    fun extendsThread(clazz: ClassObj): Boolean {
        var extendsThread = false
        var parentClass = clazz
        while (parentClass.superClassObj != null) {
            if (clazz.className == Thread::class.java.name) {
                extendsThread = true
                break
            }
            parentClass = parentClass.superClassObj
        }
        return extendsThread
    }

    @JvmStatic
    fun asString(stringObject: Any): String {
        val instance = stringObject as Instance
        val values = classInstanceValues(instance)

        val count = checkNotNull(fieldValue<Int?>(values, "count"), "count")
        if (count == 0) {
            return ""
        }

        val value: Any? = fieldValue(values, "value")
        checkNotNull(value, "value")

        if (isCharArray(value)) {
            val array = value as ArrayInstance
            var offset = 0
            if (hasField(values, "offset")) {
                val offsetValue = checkNotNull(fieldValue<Int?>(values, "offset"), "offset")
                offset = offsetValue
            }
            val chars = array.asCharArray(offset, count)
            return String(chars)
        } else if (isByteArray(value)) {
            val array = value as ArrayInstance
            try {
                val asRawByteArray: Method =
                    ArrayInstance::class.java.getDeclaredMethod("asRawByteArray", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                asRawByteArray.isAccessible = true
                val rawByteArray = asRawByteArray.invoke(array, 0, count) as ByteArray
                return String(rawByteArray, Charset.forName("UTF-8"))
            } catch (e: NoSuchMethodException) {
                throw RuntimeException(e)
            } catch (e: IllegalAccessException) {
                throw RuntimeException(e)
            } catch (e: InvocationTargetException) {
                throw RuntimeException(e)
            }
        } else {
            throw UnsupportedOperationException("Could not find char array in $instance")
        }
    }

    @JvmStatic
    fun isPrimitiveWrapper(value: Any?): Boolean {
        if (value !is ClassInstance) {
            return false
        }
        return WRAPPER_TYPES.contains(value.classObj.className)
    }

    @JvmStatic
    fun isPrimitiveOrWrapperArray(value: Any?): Boolean {
        if (value !is ArrayInstance) {
            return false
        }
        if (value.arrayType != Type.OBJECT) {
            return true
        }
        return WRAPPER_TYPES.contains(value.classObj.className)
    }

    private fun isCharArray(value: Any?): Boolean {
        return value is ArrayInstance && value.arrayType == Type.CHAR
    }

    private fun isByteArray(value: Any?): Boolean {
        return value is ArrayInstance && value.arrayType == Type.BYTE
    }

    @JvmStatic
    fun classInstanceValues(instance: Instance): List<ClassInstance.FieldValue> {
        val classInstance = instance as ClassInstance
        return classInstance.values
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T> fieldValue(values: List<ClassInstance.FieldValue>, fieldName: String): T {
        for (fieldValue in values) {
            if (fieldValue.field.name == fieldName) {
                return fieldValue.value as T
            }
        }
        throw IllegalArgumentException("Field $fieldName does exists")
    }

    @JvmStatic
    fun hasField(values: List<ClassInstance.FieldValue>, fieldName: String): Boolean {
        for (fieldValue in values) {
            if (fieldValue.field.name == fieldName) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun getArrayInstanceLength(instance: ArrayInstance): Int {
        try {
            val lengthField = ArrayInstance::class.java.getDeclaredField("mLength")
            lengthField.isAccessible = true
            return lengthField.getInt(instance)
        } catch (thr: Throwable) {
            throw RuntimeException(thr)
        }
    }

    @JvmStatic
    fun asRawByteArray(instance: ArrayInstance, start: Int, elementCount: Int): ByteArray {
        try {
            val asRawByteArrayMethod =
                ArrayInstance::class.java.getDeclaredMethod("asRawByteArray", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            asRawByteArrayMethod.isAccessible = true
            return asRawByteArrayMethod.invoke(instance, start, elementCount) as ByteArray
        } catch (thr: Throwable) {
            throw RuntimeException(thr)
        }
    }

    @JvmStatic
    fun getInstanceStack(instance: Instance): StackTrace? {
        try {
            val stackField = Instance::class.java.getDeclaredField("mStack")
            stackField.isAccessible = true
            return stackField.get(instance) as StackTrace?
        } catch (e: NoSuchFieldException) {
            throw RuntimeException(e)
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        }
    }

    @JvmStatic
    fun getInstanceStackFrames(instance: Instance): Array<StackFrame> {
        try {
            val stackTrace = getInstanceStack(instance)
            if (stackTrace != null) {
                val framesField = StackTrace::class.java.getDeclaredField("mFrames")
                framesField.isAccessible = true
                return framesField.get(stackTrace) as Array<StackFrame>
            }
            return emptyArray()
        } catch (e: NoSuchFieldException) {
            throw RuntimeException(e)
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        }
    }
}

