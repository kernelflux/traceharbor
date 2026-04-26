package SevenZip.Compression.RangeCoder

import java.io.IOException
import java.io.OutputStream

class Encoder {
    var Stream: OutputStream? = null
    var Low = 0L
    var Range = 0
    var _cacheSize = 0
    var _cache = 0
    var _position = 0L

    fun SetStream(stream: OutputStream) {
        Stream = stream
    }

    fun ReleaseStream() {
        Stream = null
    }

    fun Init() {
        _position = 0
        Low = 0
        Range = -1
        _cacheSize = 1
        _cache = 0
    }

    @Throws(IOException::class)
    fun FlushData() {
        repeat(5) { ShiftLow() }
    }

    @Throws(IOException::class)
    fun FlushStream() {
        Stream!!.flush()
    }

    @Throws(IOException::class)
    fun ShiftLow() {
        val lowHi = (Low ushr 32).toInt()
        if (lowHi != 0 || Low < 0xFF000000L) {
            _position += _cacheSize.toLong()
            var temp = _cache
            do {
                Stream!!.write(temp + lowHi)
                temp = 0xFF
                _cacheSize--
            } while (_cacheSize != 0)
            _cache = (Low.toInt()) ushr 24
        }
        _cacheSize++
        Low = (Low and 0xFFFFFF) shl 8
    }

    @Throws(IOException::class)
    fun EncodeDirectBits(v: Int, numTotalBits: Int) {
        for (i in numTotalBits - 1 downTo 0) {
            Range = Range ushr 1
            if (((v ushr i) and 1) == 1) {
                Low += Range.toLong()
            }
            if ((Range and kTopMask) == 0) {
                Range = Range shl 8
                ShiftLow()
            }
        }
    }

    fun GetProcessedSizeAdd(): Long {
        return _cacheSize + _position + 4
    }

    @Throws(IOException::class)
    fun Encode(probs: ShortArray, index: Int, symbol: Int) {
        val prob = probs[index].toInt()
        val newBound = (Range ushr kNumBitModelTotalBits) * prob
        if (symbol == 0) {
            Range = newBound
            probs[index] = (prob + ((kBitModelTotal - prob) ushr kNumMoveBits)).toShort()
        } else {
            Low += newBound.toLong() and 0xFFFFFFFFL
            Range -= newBound
            probs[index] = (prob - (prob ushr kNumMoveBits)).toShort()
        }
        if ((Range and kTopMask) == 0) {
            Range = Range shl 8
            ShiftLow()
        }
    }

    companion object {
        const val kTopMask: Int = -0x1000000
        const val kNumBitModelTotalBits = 11
        const val kBitModelTotal = 1 shl kNumBitModelTotalBits
        const val kNumMoveBits = 5
        const val kNumMoveReducingBits = 2
        const val kNumBitPriceShiftBits = 6

        @JvmField
        val ProbPrices = IntArray(kBitModelTotal ushr kNumMoveReducingBits)

        init {
            val kNumBits = kNumBitModelTotalBits - kNumMoveReducingBits
            for (i in kNumBits - 1 downTo 0) {
                val start = 1 shl (kNumBits - i - 1)
                val end = 1 shl (kNumBits - i)
                for (j in start until end) {
                    ProbPrices[j] = (i shl kNumBitPriceShiftBits) +
                        (((end - j) shl kNumBitPriceShiftBits) ushr (kNumBits - i - 1))
                }
            }
        }

        @JvmStatic
        fun InitBitModels(probs: ShortArray) {
            for (i in probs.indices) {
                probs[i] = (kBitModelTotal ushr 1).toShort()
            }
        }

        @JvmStatic
        fun GetPrice(prob: Int, symbol: Int): Int {
            return ProbPrices[(((prob - symbol) xor (-symbol)) and (kBitModelTotal - 1)) ushr kNumMoveReducingBits]
        }

        @JvmStatic
        fun GetPrice0(prob: Int): Int {
            return ProbPrices[prob ushr kNumMoveReducingBits]
        }

        @JvmStatic
        fun GetPrice1(prob: Int): Int {
            return ProbPrices[(kBitModelTotal - prob) ushr kNumMoveReducingBits]
        }
    }
}
