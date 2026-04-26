package SevenZip.Compression.RangeCoder

import java.io.IOException

class BitTreeDecoder(numBitLevels: Int) {
    @JvmField
    var Models: ShortArray = ShortArray(1 shl numBitLevels)

    @JvmField
    var NumBitLevels = numBitLevels

    fun Init() {
        Decoder.InitBitModels(Models)
    }

    @Throws(IOException::class)
    fun Decode(rangeDecoder: Decoder): Int {
        var m = 1
        for (bitIndex in NumBitLevels downTo 1) {
            m = (m shl 1) + rangeDecoder.DecodeBit(Models, m)
        }
        return m - (1 shl NumBitLevels)
    }

    @Throws(IOException::class)
    fun ReverseDecode(rangeDecoder: Decoder): Int {
        var m = 1
        var symbol = 0
        for (bitIndex in 0 until NumBitLevels) {
            val bit = rangeDecoder.DecodeBit(Models, m)
            m = m shl 1
            m += bit
            symbol = symbol or (bit shl bitIndex)
        }
        return symbol
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun ReverseDecode(models: ShortArray, startIndex: Int, rangeDecoder: Decoder, numBitLevels: Int): Int {
            var m = 1
            var symbol = 0
            for (bitIndex in 0 until numBitLevels) {
                val bit = rangeDecoder.DecodeBit(models, startIndex + m)
                m = m shl 1
                m += bit
                symbol = symbol or (bit shl bitIndex)
            }
            return symbol
        }
    }
}
