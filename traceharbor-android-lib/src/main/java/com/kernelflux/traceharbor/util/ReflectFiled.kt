package com.kernelflux.traceharbor.util

import java.lang.reflect.Field

/**
 * Reflective accessor to a `Field` of a class, walking the superclass chain
 * during preparation. Method names retain the original Tencent typo
 * (`Filed` rather than `Field`) for binary compatibility with
 * [ReflectUtils] and other Java callers.
 */
class ReflectFiled<Type>(clazz: Class<*>?, fieldName: String?) {
    private val mClazz: Class<*>
    private val mFieldName: String

    private var mInit: Boolean = false
    private var mField: Field? = null

    init {
        if (clazz == null || fieldName.isNullOrEmpty()) {
            throw IllegalArgumentException("Both of invoker and fieldName can not be null or nil.")
        }
        this.mClazz = clazz
        this.mFieldName = fieldName
    }

    @Synchronized
    private fun prepare() {
        if (mInit) {
            return
        }
        var clazz: Class<*>? = mClazz
        while (clazz != null) {
            try {
                val f = clazz.getDeclaredField(mFieldName)
                f.isAccessible = true
                mField = f
                break
            } catch (e: Exception) {
                // Walk the superclass chain — original behaviour.
            }
            clazz = clazz.superclass
        }
        mInit = true
    }

    @Synchronized
    @Throws(NoSuchFieldException::class, IllegalAccessException::class, IllegalArgumentException::class)
    fun get(): Type? = get(false)

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    @Throws(NoSuchFieldException::class, IllegalAccessException::class, IllegalArgumentException::class)
    fun get(ignoreFieldNoExist: Boolean): Type? {
        prepare()
        if (mField == null) {
            if (!ignoreFieldNoExist) {
                throw NoSuchFieldException()
            }
            TraceHarborLog.w(TAG, String.format("Field %s is no exists.", mFieldName))
            return null
        }
        return try {
            mField!!.get(null) as Type?
        } catch (e: ClassCastException) {
            throw IllegalArgumentException("unable to cast object")
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    @Throws(NoSuchFieldException::class, IllegalAccessException::class, IllegalArgumentException::class)
    fun get(ignoreFieldNoExist: Boolean, instance: Any?): Type? {
        prepare()
        if (mField == null) {
            if (!ignoreFieldNoExist) {
                throw NoSuchFieldException()
            }
            TraceHarborLog.w(TAG, String.format("Field %s is no exists.", mFieldName))
            return null
        }
        return try {
            mField!!.get(instance) as Type?
        } catch (e: ClassCastException) {
            throw IllegalArgumentException("unable to cast object")
        }
    }

    @Synchronized
    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    fun get(instance: Any?): Type? = get(false, instance)

    @Synchronized
    fun getWithoutThrow(instance: Any?): Type? {
        return try {
            get(true, instance)
        } catch (e: NoSuchFieldException) {
            TraceHarborLog.i(TAG, "getWithoutThrow, exception occur :%s", e); null
        } catch (e: IllegalAccessException) {
            TraceHarborLog.i(TAG, "getWithoutThrow, exception occur :%s", e); null
        } catch (e: IllegalArgumentException) {
            TraceHarborLog.i(TAG, "getWithoutThrow, exception occur :%s", e); null
        }
    }

    @Synchronized
    fun getWithoutThrow(): Type? {
        return try {
            get(true)
        } catch (e: NoSuchFieldException) {
            TraceHarborLog.i(TAG, "getWithoutThrow, exception occur :%s", e); null
        } catch (e: IllegalAccessException) {
            TraceHarborLog.i(TAG, "getWithoutThrow, exception occur :%s", e); null
        } catch (e: IllegalArgumentException) {
            TraceHarborLog.i(TAG, "getWithoutThrow, exception occur :%s", e); null
        }
    }

    @Synchronized
    @Throws(NoSuchFieldException::class, IllegalAccessException::class, IllegalArgumentException::class)
    fun set(instance: Any?, `val`: Type?): Boolean = set(instance, `val`, false)

    @Synchronized
    @Throws(NoSuchFieldException::class, IllegalAccessException::class, IllegalArgumentException::class)
    fun set(instance: Any?, `val`: Type?, ignoreFieldNoExist: Boolean): Boolean {
        prepare()
        if (mField == null) {
            if (!ignoreFieldNoExist) {
                throw NoSuchFieldException("Method $mFieldName is not exists.")
            }
            TraceHarborLog.w(TAG, String.format("Field %s is no exists.", mFieldName))
            return false
        }
        mField!!.set(instance, `val`)
        return true
    }

    @Synchronized
    fun setWithoutThrow(instance: Any?, `val`: Type?): Boolean {
        return try {
            set(instance, `val`, true)
        } catch (e: NoSuchFieldException) {
            TraceHarborLog.i(TAG, "setWithoutThrow, exception occur :%s", e); false
        } catch (e: IllegalAccessException) {
            TraceHarborLog.i(TAG, "setWithoutThrow, exception occur :%s", e); false
        } catch (e: IllegalArgumentException) {
            TraceHarborLog.i(TAG, "setWithoutThrow, exception occur :%s", e); false
        }
    }

    @Synchronized
    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    fun set(`val`: Type?): Boolean = set(null, `val`, false)

    @Synchronized
    fun setWithoutThrow(`val`: Type?): Boolean {
        return try {
            set(null, `val`, true)
        } catch (e: NoSuchFieldException) {
            TraceHarborLog.i(TAG, "setWithoutThrow, exception occur :%s", e); false
        } catch (e: IllegalAccessException) {
            TraceHarborLog.i(TAG, "setWithoutThrow, exception occur :%s", e); false
        } catch (e: IllegalArgumentException) {
            TraceHarborLog.i(TAG, "setWithoutThrow, exception occur :%s", e); false
        }
    }

    companion object {
        private const val TAG = "ReflectFiled"
    }
}
