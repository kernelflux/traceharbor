package com.kernelflux.traceharbor.openglleak.utils

class AutoWrapBuilder {
    private val stringBuilder: StringBuilder = StringBuilder()

    fun append(content: String): AutoWrapBuilder {
        stringBuilder.append(content)
            .append("\n")
        return this
    }

    override fun toString(): String = stringBuilder.toString()

    fun appendDotted(): AutoWrapBuilder {
        stringBuilder.append(DOTTED_LINE)
            .append("\n")
        return this
    }

    fun appendWave(): AutoWrapBuilder {
        stringBuilder.append(WAVE_LINE)
            .append("\n")
        return this
    }

    fun wrap(): AutoWrapBuilder {
        stringBuilder.append("\n")
        return this
    }

    fun appendWithSpace(content: String, count: Int): AutoWrapBuilder {
        if (count > 0) {
            for (i in 0 until count) {
                stringBuilder.append("\t")
            }
        }
        stringBuilder.append(content)
            .append("\n")
        return this
    }

    companion object {
        private const val DOTTED_LINE =
            "-------------------------------------------------------------------------"
        private const val WAVE_LINE =
            "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<"
    }
}

