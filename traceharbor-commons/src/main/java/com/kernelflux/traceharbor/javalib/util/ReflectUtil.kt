package com.kernelflux.traceharbor.javalib.util

import java.lang.reflect.Field
import java.lang.reflect.Method

object ReflectUtil {

    @JvmStatic
    @Throws(NoSuchFieldException::class, ClassNotFoundException::class)
    fun getDeclaredFieldRecursive(clazz: Any, fieldName: String): Field {
        val realClazz: Class<*> = when (clazz) {
            is String -> Class.forName(clazz)
            is Class<*> -> clazz
            else -> throw IllegalArgumentException("Illegal clazz type: ${clazz.javaClass}")
        }
        var currClazz: Class<*> = realClazz
        while (true) {
            try {
                val field = currClazz.getDeclaredField(fieldName)
                field.isAccessible = true
                return field
            } catch (e: NoSuchFieldException) {
                if (currClazz == Any::class.java) throw e
                currClazz = currClazz.superclass ?: throw e
            }
        }
    }

    @JvmStatic
    @Throws(NoSuchMethodException::class, ClassNotFoundException::class)
    fun getDeclaredMethodRecursive(
        clazz: Any,
        methodName: String,
        vararg argTypes: Class<*>,
    ): Method {
        val realClazz: Class<*> = when (clazz) {
            is String -> Class.forName(clazz)
            is Class<*> -> clazz
            else -> throw IllegalArgumentException("Illegal clazz type: ${clazz.javaClass}")
        }
        var currClazz: Class<*> = realClazz
        while (true) {
            try {
                // NOTE: original Java uses `getDeclaredMethod(methodName)` and IGNORES argTypes —
                // preserve the bug verbatim to avoid silently changing call resolution.
                val method = currClazz.getDeclaredMethod(methodName)
                method.isAccessible = true
                return method
            } catch (e: NoSuchMethodException) {
                if (currClazz == Any::class.java) throw e
                currClazz = currClazz.superclass ?: throw e
            }
        }
    }
}
