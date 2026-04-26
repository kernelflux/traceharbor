package SevenZip.Compression.LZ

import java.io.IOException
import kotlin.math.min

class BinTree : InWindow() {
    var _cyclicBufferPos = 0
    var _cyclicBufferSize = 0
    var _matchMaxLen = 0

    var _son = IntArray(0)
    var _hash = IntArray(0)

    var _cutValue = 0xFF
    var _hashMask = 0
    var _hashSizeSum = 0

    var HASH_ARRAY = true

    var kNumHashDirectBytes = 0
    var kMinMatchCheck = 4
    var kFixHashSize = kHash2Size + kHash3Size

    fun SetType(numHashBytes: Int) {
        HASH_ARRAY = numHashBytes > 2
        if (HASH_ARRAY) {
            kNumHashDirectBytes = 0
            kMinMatchCheck = 4
            kFixHashSize = kHash2Size + kHash3Size
        } else {
            kNumHashDirectBytes = 2
            kMinMatchCheck = 3
            kFixHashSize = 0
        }
    }

    @Throws(IOException::class)
    override fun Init() {
        super.Init()
        for (i in 0 until _hashSizeSum) {
            _hash[i] = kEmptyHashValue
        }
        _cyclicBufferPos = 0
        ReduceOffsets(-1)
    }

    @Throws(IOException::class)
    override fun MovePos() {
        if (++_cyclicBufferPos >= _cyclicBufferSize) {
            _cyclicBufferPos = 0
        }
        super.MovePos()
        if (_pos == kMaxValForNormalize) {
            Normalize()
        }
    }

    fun Create(historySize: Int, keepAddBufferBefore: Int, matchMaxLen: Int, keepAddBufferAfter: Int): Boolean {
        if (historySize > kMaxValForNormalize - 256) {
            return false
        }
        _cutValue = 16 + (matchMaxLen shr 1)

        val windowReservSize = (historySize + keepAddBufferBefore + matchMaxLen + keepAddBufferAfter) / 2 + 256
        super.Create(historySize + keepAddBufferBefore, matchMaxLen + keepAddBufferAfter, windowReservSize)

        _matchMaxLen = matchMaxLen

        val cyclicBufferSize = historySize + 1
        if (_cyclicBufferSize != cyclicBufferSize) {
            _cyclicBufferSize = cyclicBufferSize
            _son = IntArray(_cyclicBufferSize * 2)
        }

        var hs = kBT2HashSize
        if (HASH_ARRAY) {
            hs = historySize - 1
            hs = hs or (hs shr 1)
            hs = hs or (hs shr 2)
            hs = hs or (hs shr 4)
            hs = hs or (hs shr 8)
            hs = hs shr 1
            hs = hs or 0xFFFF
            if (hs > (1 shl 24)) {
                hs = hs shr 1
            }
            _hashMask = hs
            hs++
            hs += kFixHashSize
        }
        if (hs != _hashSizeSum) {
            _hashSizeSum = hs
            _hash = IntArray(_hashSizeSum)
        }
        return true
    }

    @Throws(IOException::class)
    fun GetMatches(distances: IntArray): Int {
        var lenLimit: Int
        if (_pos + _matchMaxLen <= _streamPos) {
            lenLimit = _matchMaxLen
        } else {
            lenLimit = _streamPos - _pos
            if (lenLimit < kMinMatchCheck) {
                MovePos()
                return 0
            }
        }

        var offset = 0
        val matchMinPos = if (_pos > _cyclicBufferSize) _pos - _cyclicBufferSize else 0
        val cur = _bufferOffset + _pos
        val buffer = _bufferBase!!
        var maxLen = kStartMaxLen
        var hashValue: Int
        var hash2Value = 0
        var hash3Value = 0

        if (HASH_ARRAY) {
            var temp = CrcTable[buffer[cur].toInt() and 0xFF] xor (buffer[cur + 1].toInt() and 0xFF)
            hash2Value = temp and (kHash2Size - 1)
            temp = temp xor ((buffer[cur + 2].toInt() and 0xFF) shl 8)
            hash3Value = temp and (kHash3Size - 1)
            hashValue = (temp xor (CrcTable[buffer[cur + 3].toInt() and 0xFF] shl 5)) and _hashMask
        } else {
            hashValue = (buffer[cur].toInt() and 0xFF) xor ((buffer[cur + 1].toInt() and 0xFF) shl 8)
        }

        var curMatch = _hash[kFixHashSize + hashValue]
        if (HASH_ARRAY) {
            var curMatch2 = _hash[hash2Value]
            val curMatch3 = _hash[kHash3Offset + hash3Value]
            _hash[hash2Value] = _pos
            _hash[kHash3Offset + hash3Value] = _pos
            if (curMatch2 > matchMinPos && buffer[_bufferOffset + curMatch2] == buffer[cur]) {
                distances[offset++] = 2
                maxLen = 2
                distances[offset++] = _pos - curMatch2 - 1
            }
            if (curMatch3 > matchMinPos && buffer[_bufferOffset + curMatch3] == buffer[cur]) {
                if (curMatch3 == curMatch2) {
                    offset -= 2
                }
                distances[offset++] = 3
                maxLen = 3
                distances[offset++] = _pos - curMatch3 - 1
                curMatch2 = curMatch3
            }
            if (offset != 0 && curMatch2 == curMatch) {
                offset -= 2
                maxLen = kStartMaxLen
            }
        }

        _hash[kFixHashSize + hashValue] = _pos

        var ptr0 = (_cyclicBufferPos shl 1) + 1
        var ptr1 = _cyclicBufferPos shl 1

        var len0 = kNumHashDirectBytes
        var len1 = kNumHashDirectBytes

        if (kNumHashDirectBytes != 0) {
            if (curMatch > matchMinPos) {
                if (buffer[_bufferOffset + curMatch + kNumHashDirectBytes] != buffer[cur + kNumHashDirectBytes]) {
                    distances[offset++] = kNumHashDirectBytes
                    maxLen = kNumHashDirectBytes
                    distances[offset++] = _pos - curMatch - 1
                }
            }
        }

        var count = _cutValue
        while (true) {
            if (curMatch <= matchMinPos || count-- == 0) {
                _son[ptr0] = kEmptyHashValue
                _son[ptr1] = kEmptyHashValue
                break
            }
            val delta = _pos - curMatch
            val cyclicPos = (
                if (delta <= _cyclicBufferPos) {
                    _cyclicBufferPos - delta
                } else {
                    _cyclicBufferPos - delta + _cyclicBufferSize
                }
                ) shl 1

            val pby1 = _bufferOffset + curMatch
            var len = min(len0, len1)
            if (buffer[pby1 + len] == buffer[cur + len]) {
                while (++len != lenLimit) {
                    if (buffer[pby1 + len] != buffer[cur + len]) {
                        break
                    }
                }
                if (maxLen < len) {
                    distances[offset++] = len
                    maxLen = len
                    distances[offset++] = delta - 1
                    if (len == lenLimit) {
                        _son[ptr1] = _son[cyclicPos]
                        _son[ptr0] = _son[cyclicPos + 1]
                        break
                    }
                }
            }
            if ((buffer[pby1 + len].toInt() and 0xFF) < (buffer[cur + len].toInt() and 0xFF)) {
                _son[ptr1] = curMatch
                ptr1 = cyclicPos + 1
                curMatch = _son[ptr1]
                len1 = len
            } else {
                _son[ptr0] = curMatch
                ptr0 = cyclicPos
                curMatch = _son[ptr0]
                len0 = len
            }
        }
        MovePos()
        return offset
    }

    @Throws(IOException::class)
    fun Skip(num: Int) {
        var remaining = num
        do {
            var lenLimit: Int
            if (_pos + _matchMaxLen <= _streamPos) {
                lenLimit = _matchMaxLen
            } else {
                lenLimit = _streamPos - _pos
                if (lenLimit < kMinMatchCheck) {
                    MovePos()
                    continue
                }
            }

            val matchMinPos = if (_pos > _cyclicBufferSize) _pos - _cyclicBufferSize else 0
            val cur = _bufferOffset + _pos
            val buffer = _bufferBase!!

            val hashValue: Int = if (HASH_ARRAY) {
                var temp = CrcTable[buffer[cur].toInt() and 0xFF] xor (buffer[cur + 1].toInt() and 0xFF)
                val hash2Value = temp and (kHash2Size - 1)
                _hash[hash2Value] = _pos
                temp = temp xor ((buffer[cur + 2].toInt() and 0xFF) shl 8)
                val hash3Value = temp and (kHash3Size - 1)
                _hash[kHash3Offset + hash3Value] = _pos
                (temp xor (CrcTable[buffer[cur + 3].toInt() and 0xFF] shl 5)) and _hashMask
            } else {
                (buffer[cur].toInt() and 0xFF) xor ((buffer[cur + 1].toInt() and 0xFF) shl 8)
            }

            var curMatch = _hash[kFixHashSize + hashValue]
            _hash[kFixHashSize + hashValue] = _pos

            var ptr0 = (_cyclicBufferPos shl 1) + 1
            var ptr1 = _cyclicBufferPos shl 1

            var len0 = kNumHashDirectBytes
            var len1 = kNumHashDirectBytes

            var count = _cutValue
            while (true) {
                if (curMatch <= matchMinPos || count-- == 0) {
                    _son[ptr0] = kEmptyHashValue
                    _son[ptr1] = kEmptyHashValue
                    break
                }

                val delta = _pos - curMatch
                val cyclicPos = (
                    if (delta <= _cyclicBufferPos) {
                        _cyclicBufferPos - delta
                    } else {
                        _cyclicBufferPos - delta + _cyclicBufferSize
                    }
                    ) shl 1

                val pby1 = _bufferOffset + curMatch
                var len = min(len0, len1)
                if (buffer[pby1 + len] == buffer[cur + len]) {
                    while (++len != lenLimit) {
                        if (buffer[pby1 + len] != buffer[cur + len]) {
                            break
                        }
                    }
                    if (len == lenLimit) {
                        _son[ptr1] = _son[cyclicPos]
                        _son[ptr0] = _son[cyclicPos + 1]
                        break
                    }
                }
                if ((buffer[pby1 + len].toInt() and 0xFF) < (buffer[cur + len].toInt() and 0xFF)) {
                    _son[ptr1] = curMatch
                    ptr1 = cyclicPos + 1
                    curMatch = _son[ptr1]
                    len1 = len
                } else {
                    _son[ptr0] = curMatch
                    ptr0 = cyclicPos
                    curMatch = _son[ptr0]
                    len0 = len
                }
            }
            MovePos()
        } while (--remaining != 0)
    }

    fun NormalizeLinks(items: IntArray, numItems: Int, subValue: Int) {
        for (i in 0 until numItems) {
            val value = items[i]
            items[i] = if (value <= subValue) {
                kEmptyHashValue
            } else {
                value - subValue
            }
        }
    }

    fun Normalize() {
        val subValue = _pos - _cyclicBufferSize
        NormalizeLinks(_son, _cyclicBufferSize * 2, subValue)
        NormalizeLinks(_hash, _hashSizeSum, subValue)
        ReduceOffsets(subValue)
    }

    fun SetCutValue(cutValue: Int) {
        _cutValue = cutValue
    }

    companion object {
        const val kHash2Size = 1 shl 10
        const val kHash3Size = 1 shl 16
        const val kBT2HashSize = 1 shl 16
        const val kStartMaxLen = 1
        const val kHash3Offset = kHash2Size
        const val kEmptyHashValue = 0
        const val kMaxValForNormalize = (1 shl 30) - 1

        @JvmField
        val CrcTable = IntArray(256)

        init {
            for (i in 0 until 256) {
                var r = i
                for (j in 0 until 8) {
                    r = if ((r and 1) != 0) {
                        (r ushr 1) xor 0xEDB88320.toInt()
                    } else {
                        r ushr 1
                    }
                }
                CrcTable[i] = r
            }
        }
    }
}
