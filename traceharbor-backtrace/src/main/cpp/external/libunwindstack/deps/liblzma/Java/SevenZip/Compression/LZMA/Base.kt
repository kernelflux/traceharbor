package SevenZip.Compression.LZMA

class Base {
    companion object {
        const val kNumRepDistances = 4
        const val kNumStates = 12

        @JvmStatic
        fun StateInit(): Int = 0

        @JvmStatic
        fun StateUpdateChar(index: Int): Int {
            if (index < 4) return 0
            if (index < 10) return index - 3
            return index - 6
        }

        @JvmStatic
        fun StateUpdateMatch(index: Int): Int = if (index < 7) 7 else 10

        @JvmStatic
        fun StateUpdateRep(index: Int): Int = if (index < 7) 8 else 11

        @JvmStatic
        fun StateUpdateShortRep(index: Int): Int = if (index < 7) 9 else 11

        @JvmStatic
        fun StateIsCharState(index: Int): Boolean = index < 7

        const val kNumPosSlotBits = 6
        const val kDicLogSizeMin = 0
        const val kNumLenToPosStatesBits = 2
        const val kNumLenToPosStates = 1 shl kNumLenToPosStatesBits
        const val kMatchMinLen = 2

        @JvmStatic
        fun GetLenToPosState(len: Int): Int {
            val localLen = len - kMatchMinLen
            return if (localLen < kNumLenToPosStates) localLen else kNumLenToPosStates - 1
        }

        const val kNumAlignBits = 4
        const val kAlignTableSize = 1 shl kNumAlignBits
        const val kAlignMask = kAlignTableSize - 1

        const val kStartPosModelIndex = 4
        const val kEndPosModelIndex = 14
        const val kNumPosModels = kEndPosModelIndex - kStartPosModelIndex
        const val kNumFullDistances = 1 shl (kEndPosModelIndex / 2)

        const val kNumLitPosStatesBitsEncodingMax = 4
        const val kNumLitContextBitsMax = 8

        const val kNumPosStatesBitsMax = 4
        const val kNumPosStatesMax = 1 shl kNumPosStatesBitsMax
        const val kNumPosStatesBitsEncodingMax = 4
        const val kNumPosStatesEncodingMax = 1 shl kNumPosStatesBitsEncodingMax

        const val kNumLowLenBits = 3
        const val kNumMidLenBits = 3
        const val kNumHighLenBits = 8
        const val kNumLowLenSymbols = 1 shl kNumLowLenBits
        const val kNumMidLenSymbols = 1 shl kNumMidLenBits
        const val kNumLenSymbols = kNumLowLenSymbols + kNumMidLenSymbols + (1 shl kNumHighLenBits)
        const val kMatchMaxLen = kMatchMinLen + kNumLenSymbols - 1
    }
}
