package SevenZip

class CRC {
    var _value = -1

    fun Init() {
        _value = -1
    }

    fun Update(data: ByteArray, offset: Int, size: Int) {
        for (i in 0 until size) {
            _value = Table[(_value xor data[offset + i].toInt()) and 0xFF] xor (_value ushr 8)
        }
    }

    fun Update(data: ByteArray) {
        val size = data.size
        for (i in 0 until size) {
            _value = Table[(_value xor data[i].toInt()) and 0xFF] xor (_value ushr 8)
        }
    }

    fun UpdateByte(b: Int) {
        _value = Table[(_value xor b) and 0xFF] xor (_value ushr 8)
    }

    fun GetDigest(): Int {
        return _value xor -1
    }

    companion object {
        @JvmField
        val Table = IntArray(256)

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
                Table[i] = r
            }
        }
    }
}
