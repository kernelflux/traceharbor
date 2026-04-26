package com.kernelflux.traceharbor.resource.analyzer.model

import java.io.Serializable
import java.util.ArrayList
import java.util.Collections.unmodifiableList
import java.util.Locale.US

class ReferenceTraceElement(
    val referenceName: String?,
    val type: Type?,
    val holder: Holder,
    val className: String,
    val extra: String?,
    val exclusion: Exclusion?,
    fields: List<String>,
) : Serializable {
    @JvmField val fields: List<String> = unmodifiableList(ArrayList(fields))

    enum class Type {
        INSTANCE_FIELD,
        STATIC_FIELD,
        LOCAL,
        ARRAY_ENTRY,
    }

    enum class Holder {
        OBJECT,
        CLASS,
        THREAD,
        ARRAY,
    }

    override fun toString(): String = toString(false)

    fun toCollectableString(): String = toString(true)

    private fun toString(collectable: Boolean): String {
        var string = ""
        if (type == Type.STATIC_FIELD) {
            string += "static "
        }
        if (holder == Holder.ARRAY || holder == Holder.THREAD) {
            string += holder.name.lowercase(US) + " "
        }
        string += className
        if (referenceName != null) {
            if (collectable && holder == Holder.ARRAY) {
                string += " [*]"
            } else {
                string += " $referenceName"
            }
        } else {
            string += " instance"
        }
        if (extra != null) {
            string += " $extra"
        }
        if (exclusion != null) {
            string += " , matching exclusion ${exclusion.matching}"
        }
        return string
    }

    fun toDetailedString(): String {
        var string = "* "
        string +=
            when (holder) {
                Holder.ARRAY -> "Array of"
                Holder.CLASS -> "Class"
                else -> "Instance of"
            }
        string += " $className\n"
        for (field in fields) {
            string += "|   $field\n"
        }
        return string
    }
}

