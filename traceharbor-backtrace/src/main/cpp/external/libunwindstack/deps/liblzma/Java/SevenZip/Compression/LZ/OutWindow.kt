package SevenZip.Compression.LZ

import java.io.IOException
import java.io.OutputStream

class OutWindow {
    var _buffer = ByteArray(0)
    var _pos = 0
    var _windowSize = 0
    var _streamPos = 0
    var _stream: OutputStream? = null

    fun Create(windowSize: Int) {
        if (_buffer.isEmpty() || _windowSize != windowSize) {
            _buffer = ByteArray(windowSize)
        }
        _windowSize = windowSize
        _pos = 0
        _streamPos = 0
    }

    @Throws(IOException::class)
    fun SetStream(stream: OutputStream) {
        ReleaseStream()
        _stream = stream
    }

    @Throws(IOException::class)
    fun ReleaseStream() {
        Flush()
        _stream = null
    }

    fun Init(solid: Boolean) {
        if (!solid) {
            _streamPos = 0
            _pos = 0
        }
    }

    @Throws(IOException::class)
    fun Flush() {
        val size = _pos - _streamPos
        if (size == 0) {
            return
        }
        _stream!!.write(_buffer, _streamPos, size)
        if (_pos >= _windowSize) {
            _pos = 0
        }
        _streamPos = _pos
    }

    @Throws(IOException::class)
    fun CopyBlock(distance: Int, len: Int) {
        var pos = _pos - distance - 1
        if (pos < 0) {
            pos += _windowSize
        }
        var left = len
        while (left != 0) {
            if (pos >= _windowSize) {
                pos = 0
            }
            _buffer[_pos++] = _buffer[pos++]
            if (_pos >= _windowSize) {
                Flush()
            }
            left--
        }
    }

    @Throws(IOException::class)
    fun PutByte(b: Byte) {
        _buffer[_pos++] = b
        if (_pos >= _windowSize) {
            Flush()
        }
    }

    fun GetByte(distance: Int): Byte {
        var pos = _pos - distance - 1
        if (pos < 0) {
            pos += _windowSize
        }
        return _buffer[pos]
    }
}
