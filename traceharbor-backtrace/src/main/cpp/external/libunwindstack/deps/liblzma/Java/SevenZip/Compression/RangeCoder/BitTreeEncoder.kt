package SevenZip.Compression.RangeCoder

import java.io.IOException

class BitTreeEncoder(numBitLevels: Int) {
    @JvmField
    var Models: ShortArray = ShortArray(1 shl numBitLevels)

    @JvmField
    var NumBitLevels = numBitLevels

    fun Init() {
        Decoder.InitBitModels(Models)
    }

    @Throws(IOException::class)
    fun Encode(rangeEncoder: Encoder, symbol: Int) {
        var m = 1
        for (bitIndex in NumBitLevels - 1 downTo 0) {
            val bit = (symbol ushr bitIndex) and 1
            rangeEncoder.Encode(Models, m, bit)
            m = (m shl 1) or bit
        }
    }

    @Throws(IOException::class)
    fun ReverseEncode(rangeEncoder: Encoder, symbol: Int) {
        var m = 1
        var localSymbol = symbol
        for (i in 0 until NumBitLevels) {
            val bit = localSymbol and 1
            rangeEncoder.Encode(Models, m, bit)
            m = (m shl 1) or bit
            localSymbol = localSymbol shr 1
        }
    }

    fun GetPrice(symbol: Int): Int {
        var localSymbol = symbol
        var price = 0
        var m = 1
        for (bitIndex in NumBitLevels - 1 downTo 0) {
            val bit = (localSymbol ushr bitIndex) and 1
            price += Encoder.GetPrice(Models[m].toInt(), bit)
            m = (m shl 1) + bit
        }
        return price
    }

    fun ReverseGetPrice(symbol: Int): Int {
        var localSymbol = symbol
        var price = 0
        var m = 1
        for (i in NumBitLevels downTo 1) {
            val bit = localSymbol and 1
            localSymbol = localSymbol ushr 1
            price += Encoder.GetPrice(Models[m].toInt(), bit)
            m = (m shl 1) or bit
        }
        return price
    }

    companion object {
        @JvmStatic
        fun ReverseGetPrice(models: ShortArray, startIndex: Int, numBitLevels: Int, symbol: Int): Int {
            var localSymbol = symbol
            var price = 0
            var m = 1
            for (i in numBitLevels downTo 1) {
                val bit = localSymbol and 1
                localSymbol = localSymbol ushr 1
                price += Encoder.GetPrice(models[startIndex + m].toInt(), bit)
                m = (m shl 1) or bit
            }
            return price
        }

        @JvmStatic
        @Throws(IOException::class)
        fun ReverseEncode(
            models: ShortArray,
            startIndex: Int,
            rangeEncoder: Encoder,
            numBitLevels: Int,
            symbol: Int
        ) {
            var m = 1
            var localSymbol = symbol
            for (i in 0 until numBitLevels) {
                val bit = localSymbol and 1
                rangeEncoder.Encode(models, startIndex + m, bit)
                m = (m shl 1) or bit
                localSymbol = localSymbol shr 1
            }
        }
    }
}
