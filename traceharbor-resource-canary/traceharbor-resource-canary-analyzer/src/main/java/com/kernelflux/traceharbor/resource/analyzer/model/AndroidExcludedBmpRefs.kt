package com.kernelflux.traceharbor.resource.analyzer.model

import java.lang.ref.WeakReference

enum class AndroidExcludedBmpRefs {
    EXCLUDE_GCROOT_WITH_SYSTEM_CLASS {
        override fun config(builder: ExcludedBmps.Builder) {
            builder.addClassNamePattern("^android\\..*", true)
            builder.addClassNamePattern("^com\\.android\\..*", true)
        }
    },
    EXCLUDE_WEAKREFERENCE_HOLDER {
        override fun config(builder: ExcludedBmps.Builder) {
            builder.instanceField(WeakReference::class.java.name, "referent")
        }
    },
    ;

    abstract fun config(builder: ExcludedBmps.Builder)

    companion object {
        @JvmStatic
        fun createDefaults(): ExcludedBmps.Builder {
            val builder = ExcludedBmps.builder()
            for (item in values()) {
                item.config(builder)
            }
            return builder
        }
    }
}

