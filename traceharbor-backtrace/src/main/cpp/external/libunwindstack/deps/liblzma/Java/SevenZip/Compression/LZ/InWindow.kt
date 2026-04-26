package SevenZip.Compression.LZ

import java.io.IOException
import java.io.InputStream

open class InWindow {
    @JvmField
    var _bufferBase: ByteArray? = null
    var _stream: InputStream? = null
    var _posLimit = 0
    var _streamEndWasReached = false
    var _pointerToLastSafePosition = 0

    @JvmField
    var _bufferOffset = 0

    @JvmField
    var _blockSize = 0

    @JvmField
    var _pos = 0
    var _keepSizeBefore = 0
    var _keepSizeAfter = 0

    @JvmField
    var _streamPos = 0

    fun MoveBlock() {
        var offset = _bufferOffset + _pos - _keepSizeBefore
        if (offset > 0) {
            offset--
        }

        val numBytes = _bufferOffset + _streamPos - offset
        val buffer = _bufferBase ?: return
        for (i in 0 until numBytes) {
            buffer[i] = buffer[offset + i]
        }
        _bufferOffset -= offset
    }

    @Throws(IOException::class)
    fun ReadBlock() {
        if (_streamEndWasReached) {
            return
        }
        while (true) {
            val size = (0 - _bufferOffset) + _blockSize - _streamPos
            if (size == 0) {
                return
            }
            val buffer = _bufferBase ?: return
            val numReadBytes = _stream!!.read(buffer, _bufferOffset + _streamPos, size)
            if (numReadBytes == -1) {
                _posLimit = _streamPos
                val pointerToPostion = _bufferOffset + _posLimit
                if (pointerToPostion > _pointerToLastSafePosition) {
                    _posLimit = _pointerToLastSafePosition - _bufferOffset
                }
                _streamEndWasReached = true
                return
            }
            _streamPos += numReadBytes
            if (_streamPos >= _pos + _keepSizeAfter) {
                _posLimit = _streamPos - _keepSizeAfter
            }
        }
    }

    fun Free() {
        _bufferBase = null
    }

    fun Create(keepSizeBefore: Int, keepSizeAfter: Int, keepSizeReserv: Int) {
        _keepSizeBefore = keepSizeBefore
        _keepSizeAfter = keepSizeAfter
        val blockSize = keepSizeBefore + keepSizeAfter + keepSizeReserv
        if (_bufferBase == null || _blockSize != blockSize) {
            Free()
            _blockSize = blockSize
            _bufferBase = ByteArray(_blockSize)
        }
        _pointerToLastSafePosition = _blockSize - keepSizeAfter
    }

    fun SetStream(stream: InputStream) {
        _stream = stream
    }

    fun ReleaseStream() {
        _stream = null
    }

    @Throws(IOException::class)
    open fun Init() {
        _bufferOffset = 0
        _pos = 0
        _streamPos = 0
        _streamEndWasReached = false
        ReadBlock()
    }

    @Throws(IOException::class)
    open fun MovePos() {
        _pos++
        if (_pos > _posLimit) {
            val pointerToPostion = _bufferOffset + _pos
            if (pointerToPostion > _pointerToLastSafePosition) {
                MoveBlock()
            }
            ReadBlock()
        }
    }

    fun GetIndexByte(index: Int): Byte {
        return _bufferBase!![_bufferOffset + _pos + index]
    }

    fun GetMatchLen(index: Int, distance: Int, limit: Int): Int {
        var localLimit = limit
        if (_streamEndWasReached) {
            if ((_pos + index) + localLimit > _streamPos) {
                localLimit = _streamPos - (_pos + index)
            }
        }
        val actualDistance = distance + 1
        val pby = _bufferOffset + _pos + index
        var i = 0
        val buffer = _bufferBase!!
        while (i < localLimit && buffer[pby + i] == buffer[pby + i - actualDistance]) {
            i++
        }
        return i
    }

    fun GetNumAvailableBytes(): Int {
        return _streamPos - _pos
    }

    fun ReduceOffsets(subValue: Int) {
        _bufferOffset += subValue
        _posLimit -= subValue
        _pos -= subValue
        _streamPos -= subValue
    }
}
