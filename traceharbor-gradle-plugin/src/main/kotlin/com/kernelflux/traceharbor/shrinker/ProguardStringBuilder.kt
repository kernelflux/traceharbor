package com.kernelflux.traceharbor.shrinker

class ProguardStringBuilder {
    private var alphaIndex = 0
    private var numberIndex = 0
    private var prefix = ""

    private fun getPrefix(len: Int): String {
        if (len == 0) {
            return alphaArray[0].toString()
        }
        if (len == 1) {
            val curChar = prefix[0]
            return if (curChar < alphaArray[alphaArray.size - 1]) {
                (curChar + 1).toString()
            } else {
                "${alphaArray[0]}${alphaArray[0]}"
            }
        }
        val lastChar = prefix[len - 1]
        return when {
            lastChar == alphaArray[alphaArray.size - 1] -> prefix.substring(0, len - 1) + numberArray[0]
            lastChar == numberArray[numberArray.size - 1] -> getPrefix(len - 1) + alphaArray[0]
            else -> prefix.substring(0, len - 1) + (lastChar + 1)
        }
    }

    private fun nextTurn() {
        alphaIndex = 0
        numberIndex = 0
        prefix = getPrefix(prefix.length)
    }

    fun generateNextProguard(): String {
        return if (prefix.isEmpty()) {
            if (alphaIndex <= alphaArray.size - 1) {
                alphaArray[alphaIndex++].toString()
            } else {
                nextTurn()
                prefix + alphaArray[alphaIndex++]
            }
        } else {
            when {
                alphaIndex <= alphaArray.size - 1 -> prefix + alphaArray[alphaIndex++]
                numberIndex <= numberArray.size - 1 -> prefix + numberArray[numberIndex++]
                else -> {
                    nextTurn()
                    prefix + alphaArray[alphaIndex++]
                }
            }
        }
    }

    fun generateNextProguardFileName(): String {
        var result = generateNextProguard()
        while (WIN_INVALID_FILE_NAME.contains(result.lowercase())) {
            result = generateNextProguard()
        }
        return result
    }

    fun reset() {
        alphaIndex = 0
        numberIndex = 0
        prefix = ""
    }

    companion object {
        private val alphaArray = CharArray(26).apply {
            for (index in indices) {
                this[index] = ('a'.code + index).toChar()
            }
        }
        private val numberArray = CharArray(10).apply {
            for (index in indices) {
                this[index] = ('0'.code + index).toChar()
            }
        }
        private val WIN_INVALID_FILE_NAME = listOf(
            "aux", "nul", "prn", "nul", "con",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9"
        )
    }
}

