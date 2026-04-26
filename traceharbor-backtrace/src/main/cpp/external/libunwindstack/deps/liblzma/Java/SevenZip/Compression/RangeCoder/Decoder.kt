package SevenZip.Compression.RangeCoder

import java.io.IOException
import java.io.InputStream

class Decoder {
    var Range = 0
    var Code = 0
    var Stream: InputStream? = null

    fun SetStream(stream: InputStream) {
        Stream = stream
    }

    fun ReleaseStream() {
        Stream = null
    }

    @Throws(IOException::class)
    fun Init() {
        Code = 0
        Range = -1
        for (i in 0 until 5) {
            Code = (Code shl 8) or Stream!!.read()
        }
    }

    @Throws(IOException::class)
    fun DecodeDirectBits(numTotalBits: Int): Int {
        var bits = numTotalBits
        var result = 0
        while (bits != 0) {
            Range = Range ushr 1
            val t = (Code - Range) ushr 31
            Code -= Range and (t - 1)
            result = (result shl 1) or (1 - t)

            if ((Range and kTopMask) == 0) {
                Code = (Code shl 8) or Stream!!.read()
                Range = Range shl 8
            }
            bits--
        }
        return result
    }

    @Throws(IOException::class)
    fun DecodeBit(probs: ShortArray, index: Int): Int {
        val prob = probs[index].toInt()
        val newBound = (Range ushr kNumBitModelTotalBits) * prob
        return if ((Code xor Int.MIN_VALUE) < (newBound xor Int.MIN_VALUE)) {
            Range = newBound
            probs[index] = (prob + ((kBitModelTotal - prob) ushr kNumMoveBits)).toShort()
            if ((Range and kTopMask) == 0) {
                Code = (Code shl 8) or Stream!!.read()
                Range = Range shl 8
            }
            0
        } else {
            Range -= newBound
            Code -= newBound
            probs[index] = (prob - (prob ushr kNumMoveBits)).toShort()
            if ((Range and kTopMask) == 0) {
                Code = (Code shl 8) or Stream!!.read()
                Range = Range shl 8
            }
            1
        }
    }

    companion object {
        const val kTopMask: Int = -0x1000000
        const val kNumBitModelTotalBits = 11
        const val kBitModelTotal = 1 shl kNumBitModelTotalBits
        const val kNumMoveBits = 5

        @JvmStatic
        fun InitBitModels(probs: ShortArray) {
            for (i in probs.indices) {
                probs[i] = (kBitModelTotal ushr 1).toShort()
            }
        }
    }
}
