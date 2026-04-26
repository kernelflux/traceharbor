package com.kernelflux.traceharbor.resource.analyzer.model

import java.io.Serializable
import java.util.ArrayList
import java.util.Collections.unmodifiableList

class ReferenceChain(
    elements: List<ReferenceTraceElement>,
) : Serializable {
    @JvmField val elements: List<ReferenceTraceElement> = unmodifiableList(ArrayList(elements))

    fun isEmpty(): Boolean = elements.isEmpty()

    override fun toString(): String {
        val sb = StringBuilder()
        for (i in elements.indices) {
            val element = elements[i]
            sb.append("* ")
            when (i) {
                0 -> sb.append("GC ROOT ")
                elements.size - 1 -> sb.append("leaks ")
                else -> sb.append("references ")
            }
            sb.append(element).append('\n')
        }
        return sb.toString()
    }

    fun toDetailedString(): String {
        var string = ""
        for (element in elements) {
            string += element.toDetailedString()
        }
        return string
    }
}

