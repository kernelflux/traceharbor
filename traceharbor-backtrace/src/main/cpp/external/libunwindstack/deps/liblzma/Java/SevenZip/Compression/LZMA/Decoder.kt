package SevenZip.Compression.LZMA

import SevenZip.Compression.LZ.OutWindow
import SevenZip.Compression.RangeCoder.BitTreeDecoder
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.max

class Decoder {
    class LenDecoder {
        private val m_Choice = ShortArray(2)
        private val m_LowCoder = arrayOfNulls<BitTreeDecoder>(Base.kNumPosStatesMax)
        private val m_MidCoder = arrayOfNulls<BitTreeDecoder>(Base.kNumPosStatesMax)
        private val m_HighCoder = BitTreeDecoder(Base.kNumHighLenBits)
        private var m_NumPosStates = 0

        fun Create(numPosStates: Int) {
            while (m_NumPosStates < numPosStates) {
                m_LowCoder[m_NumPosStates] = BitTreeDecoder(Base.kNumLowLenBits)
                m_MidCoder[m_NumPosStates] = BitTreeDecoder(Base.kNumMidLenBits)
                m_NumPosStates++
            }
        }

        fun Init() {
            SevenZip.Compression.RangeCoder.Decoder.InitBitModels(m_Choice)
            for (posState in 0 until m_NumPosStates) {
                m_LowCoder[posState]!!.Init()
                m_MidCoder[posState]!!.Init()
            }
            m_HighCoder.Init()
        }

        @Throws(IOException::class)
        fun Decode(rangeDecoder: SevenZip.Compression.RangeCoder.Decoder, posState: Int): Int {
            if (rangeDecoder.DecodeBit(m_Choice, 0) == 0) {
                return m_LowCoder[posState]!!.Decode(rangeDecoder)
            }
            var symbol = Base.kNumLowLenSymbols
            if (rangeDecoder.DecodeBit(m_Choice, 1) == 0) {
                symbol += m_MidCoder[posState]!!.Decode(rangeDecoder)
            } else {
                symbol += Base.kNumMidLenSymbols + m_HighCoder.Decode(rangeDecoder)
            }
            return symbol
        }
    }

    class LiteralDecoder {
        class Decoder2 {
            private val m_Decoders = ShortArray(0x300)

            fun Init() {
                SevenZip.Compression.RangeCoder.Decoder.InitBitModels(m_Decoders)
            }

            @Throws(IOException::class)
            fun DecodeNormal(rangeDecoder: SevenZip.Compression.RangeCoder.Decoder): Byte {
                var symbol = 1
                do {
                    symbol = (symbol shl 1) or rangeDecoder.DecodeBit(m_Decoders, symbol)
                } while (symbol < 0x100)
                return symbol.toByte()
            }

            @Throws(IOException::class)
            fun DecodeWithMatchByte(rangeDecoder: SevenZip.Compression.RangeCoder.Decoder, matchByteIn: Byte): Byte {
                var symbol = 1
                var matchByte = matchByteIn
                do {
                    val matchBit = (matchByte.toInt() shr 7) and 1
                    matchByte = (matchByte.toInt() shl 1).toByte()
                    val bit = rangeDecoder.DecodeBit(m_Decoders, ((1 + matchBit) shl 8) + symbol)
                    symbol = (symbol shl 1) or bit
                    if (matchBit != bit) {
                        while (symbol < 0x100) {
                            symbol = (symbol shl 1) or rangeDecoder.DecodeBit(m_Decoders, symbol)
                        }
                        break
                    }
                } while (symbol < 0x100)
                return symbol.toByte()
            }
        }

        private var m_Coders: Array<Decoder2>? = null
        private var m_NumPrevBits = 0
        private var m_NumPosBits = 0
        private var m_PosMask = 0

        fun Create(numPosBits: Int, numPrevBits: Int) {
            if (m_Coders != null && m_NumPrevBits == numPrevBits && m_NumPosBits == numPosBits) {
                return
            }
            m_NumPosBits = numPosBits
            m_PosMask = (1 shl numPosBits) - 1
            m_NumPrevBits = numPrevBits
            val numStates = 1 shl (m_NumPrevBits + m_NumPosBits)
            m_Coders = Array(numStates) { Decoder2() }
        }

        fun Init() {
            val coders = m_Coders ?: return
            val numStates = 1 shl (m_NumPrevBits + m_NumPosBits)
            for (i in 0 until numStates) {
                coders[i].Init()
            }
        }

        fun GetDecoder(pos: Int, prevByte: Byte): Decoder2 {
            val coders = m_Coders!!
            return coders[((pos and m_PosMask) shl m_NumPrevBits) + ((prevByte.toInt() and 0xFF) ushr (8 - m_NumPrevBits))]
        }
    }

    private val m_OutWindow = OutWindow()
    private val m_RangeDecoder = SevenZip.Compression.RangeCoder.Decoder()

    private val m_IsMatchDecoders = ShortArray(Base.kNumStates shl Base.kNumPosStatesBitsMax)
    private val m_IsRepDecoders = ShortArray(Base.kNumStates)
    private val m_IsRepG0Decoders = ShortArray(Base.kNumStates)
    private val m_IsRepG1Decoders = ShortArray(Base.kNumStates)
    private val m_IsRepG2Decoders = ShortArray(Base.kNumStates)
    private val m_IsRep0LongDecoders = ShortArray(Base.kNumStates shl Base.kNumPosStatesBitsMax)

    private val m_PosSlotDecoder = Array(Base.kNumLenToPosStates) { BitTreeDecoder(Base.kNumPosSlotBits) }
    private val m_PosDecoders = ShortArray(Base.kNumFullDistances - Base.kEndPosModelIndex)
    private val m_PosAlignDecoder = BitTreeDecoder(Base.kNumAlignBits)

    private val m_LenDecoder = LenDecoder()
    private val m_RepLenDecoder = LenDecoder()
    private val m_LiteralDecoder = LiteralDecoder()

    private var m_DictionarySize = -1
    private var m_DictionarySizeCheck = -1
    private var m_PosStateMask = 0

    fun SetDictionarySize(dictionarySize: Int): Boolean {
        if (dictionarySize < 0) {
            return false
        }
        if (m_DictionarySize != dictionarySize) {
            m_DictionarySize = dictionarySize
            m_DictionarySizeCheck = max(m_DictionarySize, 1)
            m_OutWindow.Create(max(m_DictionarySizeCheck, 1 shl 12))
        }
        return true
    }

    fun SetLcLpPb(lc: Int, lp: Int, pb: Int): Boolean {
        if (lc > Base.kNumLitContextBitsMax || lp > 4 || pb > Base.kNumPosStatesBitsMax) {
            return false
        }
        m_LiteralDecoder.Create(lp, lc)
        val numPosStates = 1 shl pb
        m_LenDecoder.Create(numPosStates)
        m_RepLenDecoder.Create(numPosStates)
        m_PosStateMask = numPosStates - 1
        return true
    }

    @Throws(IOException::class)
    fun Init() {
        m_OutWindow.Init(false)

        SevenZip.Compression.RangeCoder.Decoder.InitBitModels(m_IsMatchDecoders)
        SevenZip.Compression.RangeCoder.Decoder.InitBitModels(m_IsRep0LongDecoders)
        SevenZip.Compression.RangeCoder.Decoder.InitBitModels(m_IsRepDecoders)
        SevenZip.Compression.RangeCoder.Decoder.InitBitModels(m_IsRepG0Decoders)
        SevenZip.Compression.RangeCoder.Decoder.InitBitModels(m_IsRepG1Decoders)
        SevenZip.Compression.RangeCoder.Decoder.InitBitModels(m_IsRepG2Decoders)
        SevenZip.Compression.RangeCoder.Decoder.InitBitModels(m_PosDecoders)

        m_LiteralDecoder.Init()
        for (i in 0 until Base.kNumLenToPosStates) {
            m_PosSlotDecoder[i].Init()
        }
        m_LenDecoder.Init()
        m_RepLenDecoder.Init()
        m_PosAlignDecoder.Init()
        m_RangeDecoder.Init()
    }

    @Throws(IOException::class)
    fun Code(inStream: InputStream, outStream: OutputStream, outSize: Long): Boolean {
        m_RangeDecoder.SetStream(inStream)
        m_OutWindow.SetStream(outStream)
        Init()

        var state = Base.StateInit()
        var rep0 = 0
        var rep1 = 0
        var rep2 = 0
        var rep3 = 0

        var nowPos64 = 0L
        var prevByte: Byte = 0
        while (outSize < 0 || nowPos64 < outSize) {
            val posState = nowPos64.toInt() and m_PosStateMask
            if (m_RangeDecoder.DecodeBit(m_IsMatchDecoders, (state shl Base.kNumPosStatesBitsMax) + posState) == 0) {
                val decoder2 = m_LiteralDecoder.GetDecoder(nowPos64.toInt(), prevByte)
                prevByte = if (!Base.StateIsCharState(state)) {
                    decoder2.DecodeWithMatchByte(m_RangeDecoder, m_OutWindow.GetByte(rep0))
                } else {
                    decoder2.DecodeNormal(m_RangeDecoder)
                }
                m_OutWindow.PutByte(prevByte)
                state = Base.StateUpdateChar(state)
                nowPos64++
            } else {
                var len: Int
                if (m_RangeDecoder.DecodeBit(m_IsRepDecoders, state) == 1) {
                    len = 0
                    if (m_RangeDecoder.DecodeBit(m_IsRepG0Decoders, state) == 0) {
                        if (m_RangeDecoder.DecodeBit(
                                m_IsRep0LongDecoders,
                                (state shl Base.kNumPosStatesBitsMax) + posState
                            ) == 0
                        ) {
                            state = Base.StateUpdateShortRep(state)
                            len = 1
                        }
                    } else {
                        val distance: Int
                        if (m_RangeDecoder.DecodeBit(m_IsRepG1Decoders, state) == 0) {
                            distance = rep1
                        } else {
                            if (m_RangeDecoder.DecodeBit(m_IsRepG2Decoders, state) == 0) {
                                distance = rep2
                            } else {
                                distance = rep3
                                rep3 = rep2
                            }
                            rep2 = rep1
                        }
                        rep1 = rep0
                        rep0 = distance
                    }
                    if (len == 0) {
                        len = m_RepLenDecoder.Decode(m_RangeDecoder, posState) + Base.kMatchMinLen
                        state = Base.StateUpdateRep(state)
                    }
                } else {
                    rep3 = rep2
                    rep2 = rep1
                    rep1 = rep0
                    len = Base.kMatchMinLen + m_LenDecoder.Decode(m_RangeDecoder, posState)
                    state = Base.StateUpdateMatch(state)
                    val posSlot = m_PosSlotDecoder[Base.GetLenToPosState(len)].Decode(m_RangeDecoder)
                    if (posSlot >= Base.kStartPosModelIndex) {
                        val numDirectBits = (posSlot shr 1) - 1
                        rep0 = (2 or (posSlot and 1)) shl numDirectBits
                        if (posSlot < Base.kEndPosModelIndex) {
                            rep0 += BitTreeDecoder.ReverseDecode(
                                m_PosDecoders,
                                rep0 - posSlot - 1,
                                m_RangeDecoder,
                                numDirectBits
                            )
                        } else {
                            rep0 += (m_RangeDecoder.DecodeDirectBits(numDirectBits - Base.kNumAlignBits) shl Base.kNumAlignBits)
                            rep0 += m_PosAlignDecoder.ReverseDecode(m_RangeDecoder)
                            if (rep0 < 0) {
                                if (rep0 == -1) {
                                    break
                                }
                                return false
                            }
                        }
                    } else {
                        rep0 = posSlot
                    }
                }
                if (rep0.toLong() >= nowPos64 || rep0 >= m_DictionarySizeCheck) {
                    return false
                }
                m_OutWindow.CopyBlock(rep0, len)
                nowPos64 += len.toLong()
                prevByte = m_OutWindow.GetByte(0)
            }
        }
        m_OutWindow.Flush()
        m_OutWindow.ReleaseStream()
        m_RangeDecoder.ReleaseStream()
        return true
    }

    fun SetDecoderProperties(properties: ByteArray): Boolean {
        if (properties.size < 5) {
            return false
        }
        val value = properties[0].toInt() and 0xFF
        val lc = value % 9
        val remainder = value / 9
        val lp = remainder % 5
        val pb = remainder / 5
        var dictionarySize = 0
        for (i in 0..3) {
            dictionarySize += (properties[1 + i].toInt() and 0xFF) shl (i * 8)
        }
        if (!SetLcLpPb(lc, lp, pb)) {
            return false
        }
        return SetDictionarySize(dictionarySize)
    }
}
