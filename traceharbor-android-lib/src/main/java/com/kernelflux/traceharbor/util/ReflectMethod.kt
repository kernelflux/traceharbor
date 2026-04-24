package com.kernelflux.traceharbor.util

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

class ReflectMethod(
    clazz: Class<*>?,
    methodName: String?,
    vararg parameterTypes: Class<*>,
) {
    private val mClazz: Class<*>
    private val mMethodName: String
    private val mParameterTypes: Array<out Class<*>>

    private var mInit: Boolean = false
    private var mMethod: Method? = null

    init {
        if (clazz == null || methodName.isNullOrEmpty()) {
            throw IllegalArgumentException("Both of invoker and fieldName can not be null or nil.")
        }
        this.mClazz = clazz
        this.mMethodName = methodName
        this.mParameterTypes = parameterTypes
    }

    @Synchronized
    private fun prepare() {
        if (mInit) {
            return
        }
        var clazz: Class<*>? = mClazz
        while (clazz != null) {
            try {
                val method = clazz.getDeclaredMethod(mMethodName, *mParameterTypes)
                method.isAccessible = true
                mMethod = method
                break
            } catch (e: Exception) {
                // Walk the superclass chain — original behaviour.
            }
            clazz = clazz.superclass
        }
        mInit = true
    }

    @Synchronized
    @Throws(
        NoSuchFieldException::class,
        IllegalAccessException::class,
        IllegalArgumentException::class,
        InvocationTargetException::class,
    )
    fun <T> invoke(instance: Any?, vararg args: Any?): T? = invoke(instance, false, *args)

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    @Throws(
        NoSuchFieldException::class,
        IllegalAccessException::class,
        IllegalArgumentException::class,
        InvocationTargetException::class,
    )
    fun <T> invoke(instance: Any?, ignoreFieldNoExist: Boolean, vararg args: Any?): T? {
        prepare()
        if (mMethod == null) {
            if (!ignoreFieldNoExist) {
                throw NoSuchFieldException("Method $mMethodName is not exists.")
            }
            TraceHarborLog.w(TAG, "Field %s is no exists", mMethodName)
            return null
        }
        return mMethod!!.invoke(instance, *args) as T?
    }

    @Synchronized
    fun <T> invokeWithoutThrow(instance: Any?, vararg args: Any?): T? {
        return try {
            invoke<T>(instance, true, *args)
        } catch (e: NoSuchFieldException) {
            TraceHarborLog.e(TAG, "invokeWithoutThrow, exception occur :%s", e); null
        } catch (e: IllegalAccessException) {
            TraceHarborLog.e(TAG, "invokeWithoutThrow, exception occur :%s", e); null
        } catch (e: IllegalArgumentException) {
            TraceHarborLog.e(TAG, "invokeWithoutThrow, exception occur :%s", e); null
        } catch (e: InvocationTargetException) {
            TraceHarborLog.e(TAG, "invokeWithoutThrow, exception occur :%s", e); null
        }
    }

    companion object {
        private const val TAG = "ReflectFiled"
    }
}
