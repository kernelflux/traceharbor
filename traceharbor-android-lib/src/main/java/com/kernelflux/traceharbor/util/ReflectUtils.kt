package com.kernelflux.traceharbor.util

import android.os.Build
import java.lang.reflect.Field
import java.lang.reflect.Method

object ReflectUtils {
    private const val TAG = "TraceHarbor.ReflectUtils"

    @JvmStatic
    @Throws(Exception::class)
    fun <T> get(clazz: Class<*>, fieldName: String): T? = ReflectFiled<T>(clazz, fieldName).get()

    @JvmStatic
    @Throws(Exception::class)
    fun <T> get(clazz: Class<*>, fieldName: String, instance: Any?): T? =
        ReflectFiled<T>(clazz, fieldName).get(instance)

    @JvmStatic
    @Throws(Exception::class)
    fun set(clazz: Class<*>, fieldName: String, value: Any?): Boolean =
        ReflectFiled<Any>(clazz, fieldName).set(value)

    @JvmStatic
    @Throws(Exception::class)
    fun set(clazz: Class<*>, fieldName: String, instance: Any?, value: Any?): Boolean =
        ReflectFiled<Any>(clazz, fieldName).set(instance, value)

    @JvmStatic
    @Throws(Exception::class)
    fun <T> invoke(clazz: Class<*>, methodName: String, instance: Any?, vararg args: Any?): T? =
        ReflectMethod(clazz, methodName).invoke(instance, *args)

    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun <T> reflectObject(instance: Any?, name: String, defaultValue: T?, isHard: Boolean): T? {
        if (null == instance) return defaultValue
        if (isHard) {
            try {
                val getDeclaredField =
                    Class::class.java.getDeclaredMethod("getDeclaredField", String::class.java)
                val field = getDeclaredField.invoke(instance.javaClass, name) as Field
                field.isAccessible = true
                return field.get(instance) as T?
            } catch (e: Exception) {
                TraceHarborLog.e(
                    TAG,
                    e.toString() + "isHard=%s\n%s",
                    true,
                    TraceHarborUtil.printException(e),
                )
            }
        } else {
            try {
                val field = instance.javaClass.getDeclaredField(name)
                field.isAccessible = true
                return field.get(instance) as T?
            } catch (e: Exception) {
                TraceHarborLog.e(
                    TAG,
                    e.toString() + "isHard=%s\n%s",
                    false,
                    TraceHarborUtil.printException(e),
                )
            }
        }
        return defaultValue
    }

    @JvmStatic
    fun <T> reflectObject(instance: Any?, name: String, defaultValue: T?): T? =
        reflectObject(instance, name, defaultValue, true)

    @JvmStatic
    fun reflectMethod(
        instance: Any?,
        isHard: Boolean,
        name: String,
        vararg argTypes: Class<*>,
    ): Method? {
        if (instance == null) return null
        if (isHard) {
            try {
                // `Class<?>[]` literal — Kotlin can't write `Array<Class<*>>::class.java`
                // because the projected `Class<*>` isn't a class literal, so build the
                // Java raw-array type via reflection instead.
                val classArrayType: Class<*> = java.lang.reflect.Array.newInstance(
                    Class::class.java, 0,
                ).javaClass
                val getDeclaredMethod = Class::class.java.getDeclaredMethod(
                    "getDeclaredMethod",
                    String::class.java,
                    classArrayType,
                )
                val method =
                    getDeclaredMethod.invoke(instance.javaClass, name, argTypes) as Method
                method.isAccessible = true
                return method
            } catch (e: Exception) {
                TraceHarborLog.e(
                    TAG,
                    e.toString() + "isHard=%s\n%s",
                    true,
                    TraceHarborUtil.printException(e),
                )
            }
        } else {
            try {
                val method = instance.javaClass.getDeclaredMethod(name, *argTypes)
                method.isAccessible = true
                return method
            } catch (e: Exception) {
                TraceHarborLog.e(
                    TAG,
                    e.toString() + "isHard=%s\n%s",
                    false,
                    TraceHarborUtil.printException(e),
                )
            }
        }
        return null
    }

    @JvmStatic
    fun reflectMethod(instance: Any?, name: String, vararg argTypes: Class<*>): Method? {
        val isHard = Build.VERSION.SDK_INT <= 29
        return reflectMethod(instance, isHard, name, *argTypes)
    }
}
