package com.kernelflux.traceharbor.resource.analyzer.model

import com.kernelflux.traceharbor.resource.common.utils.Preconditions.checkNotNull
import java.io.Serializable
import java.util.LinkedHashMap
import java.util.Collections.unmodifiableMap

open class ExcludedRefs(
    builder: BuilderWithParams,
) : Serializable {
    @JvmField
    val fieldNameByClassName: Map<String, Map<String, Exclusion>> =
        unmodifiableRefStringMap(builder.fieldNameByClassName)

    @JvmField
    val staticFieldNameByClassName: Map<String, Map<String, Exclusion>> =
        unmodifiableRefStringMap(builder.staticFieldNameByClassName)

    @JvmField
    val threadNames: Map<String, Exclusion> = unmodifiableRefMap(builder.threadNames)

    @JvmField
    val classNames: Map<String, Exclusion> = unmodifiableRefMap(builder.classNames)

    private fun unmodifiableRefStringMap(
        mapmap: Map<String, Map<String, ParamsBuilder>>,
    ): Map<String, Map<String, Exclusion>> {
        val fieldNameByClassName = LinkedHashMap<String, Map<String, Exclusion>>()
        for ((key, value) in mapmap) {
            fieldNameByClassName[key] = unmodifiableRefMap(value)
        }
        return unmodifiableMap(fieldNameByClassName)
    }

    private fun unmodifiableRefMap(fieldBuilderMap: Map<String, ParamsBuilder>): Map<String, Exclusion> {
        val fieldMap = LinkedHashMap<String, Exclusion>()
        for ((key, value) in fieldBuilderMap) {
            fieldMap[key] = Exclusion(value)
        }
        return unmodifiableMap(fieldMap)
    }

    override fun toString(): String {
        var string = ""
        for ((clazz, fields) in fieldNameByClassName) {
            for ((field, exclusion) in fields) {
                val always = if (exclusion.alwaysExclude) " (always)" else ""
                string += "| Field: $clazz.$field$always\n"
            }
        }
        for ((clazz, fields) in staticFieldNameByClassName) {
            for ((field, exclusion) in fields) {
                val always = if (exclusion.alwaysExclude) " (always)" else ""
                string += "| Static field: $clazz.$field$always\n"
            }
        }
        for ((thread, exclusion) in threadNames) {
            val always = if (exclusion.alwaysExclude) " (always)" else ""
            string += "| Thread:$thread$always\n"
        }
        for ((clazz, exclusion) in classNames) {
            val always = if (exclusion.alwaysExclude) " (always)" else ""
            string += "| Class:$clazz$always\n"
        }
        return string
    }

    class ParamsBuilder(
        @JvmField val matching: String,
    ) {
        @JvmField var name: String? = null
        @JvmField var reason: String? = null
        @JvmField var alwaysExclude: Boolean = false
    }

    interface Builder {
        fun instanceField(className: String, fieldName: String): BuilderWithParams

        fun staticField(className: String, fieldName: String): BuilderWithParams

        fun thread(threadName: String): BuilderWithParams

        fun clazz(className: String): BuilderWithParams

        fun build(): ExcludedRefs
    }

    open class BuilderWithParams : Builder {
        val fieldNameByClassName: MutableMap<String, MutableMap<String, ParamsBuilder>> = LinkedHashMap()
        val staticFieldNameByClassName: MutableMap<String, MutableMap<String, ParamsBuilder>> = LinkedHashMap()
        val threadNames: MutableMap<String, ParamsBuilder> = LinkedHashMap()
        val classNames: MutableMap<String, ParamsBuilder> = LinkedHashMap()

        private var lastParams: ParamsBuilder? = null

        override fun instanceField(className: String, fieldName: String): BuilderWithParams {
            checkNotNull(className, "mClassName")
            checkNotNull(fieldName, "fieldName")
            val excludedFields = fieldNameByClassName.getOrPut(className) { LinkedHashMap() }
            lastParams = ParamsBuilder("field $className#$fieldName")
            excludedFields[fieldName] = lastParams!!
            return this
        }

        override fun staticField(className: String, fieldName: String): BuilderWithParams {
            checkNotNull(className, "mClassName")
            checkNotNull(fieldName, "fieldName")
            val excludedFields = staticFieldNameByClassName.getOrPut(className) { LinkedHashMap() }
            lastParams = ParamsBuilder("static field $className#$fieldName")
            excludedFields[fieldName] = lastParams!!
            return this
        }

        override fun thread(threadName: String): BuilderWithParams {
            checkNotNull(threadName, "threadName")
            lastParams = ParamsBuilder("any threads named $threadName")
            threadNames[threadName] = lastParams!!
            return this
        }

        override fun clazz(className: String): BuilderWithParams {
            checkNotNull(className, "mClassName")
            lastParams = ParamsBuilder("any subclass of $className")
            classNames[className] = lastParams!!
            return this
        }

        fun named(name: String): BuilderWithParams {
            lastParams?.name = name
            return this
        }

        fun reason(reason: String): BuilderWithParams {
            lastParams?.reason = reason
            return this
        }

        fun alwaysExclude(): BuilderWithParams {
            lastParams?.alwaysExclude = true
            return this
        }

        override fun build(): ExcludedRefs = ExcludedRefs(this)
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = BuilderWithParams()
    }
}

