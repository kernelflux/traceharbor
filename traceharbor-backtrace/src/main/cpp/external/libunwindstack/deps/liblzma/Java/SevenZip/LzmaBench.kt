package SevenZip

import java.io.ByteArrayOutputStream
import java.io.IOException

class LzmaBench {
    class CRandomGenerator {
        var A1 = 0
        var A2 = 0

        init {
            Init()
        }

        fun Init() {
            A1 = 362436069
            A2 = 521288629
        }

        fun GetRnd(): Int {
            A1 = 36969 * (A1 and 0xFFFF) + (A1 ushr 16)
            A2 = 18000 * (A2 and 0xFFFF) + (A2 ushr 16)
            return (A1 shl 16) xor A2
        }
    }

    class CBitRandomGenerator {
        var RG = CRandomGenerator()
        var Value = 0
        var NumBits = 0

        fun Init() {
            Value = 0
            NumBits = 0
        }

        fun GetRnd(numBitsIn: Int): Int {
            var numBits = numBitsIn
            if (NumBits > numBits) {
                val result = Value and ((1 shl numBits) - 1)
                Value = Value ushr numBits
                NumBits -= numBits
                return result
            }
            numBits -= NumBits
            var result = Value shl numBits
            Value = RG.GetRnd()
            result = result or (Value and ((1 shl numBits) - 1))
            Value = Value ushr numBits
            NumBits = 32 - numBits
            return result
        }
    }

    class CBenchRandomGenerator {
        var RG = CBitRandomGenerator()
        var Pos = 0
        var Rep0 = 0

        var BufferSize = 0
        var Buffer: ByteArray? = null

        fun Set(bufferSize: Int) {
            Buffer = ByteArray(bufferSize)
            Pos = 0
            BufferSize = bufferSize
        }

        fun GetRndBit(): Int = RG.GetRnd(1)

        fun GetLogRandBits(numBits: Int): Int {
            val len = RG.GetRnd(numBits)
            return RG.GetRnd(len)
        }

        fun GetOffset(): Int {
            if (GetRndBit() == 0) {
                return GetLogRandBits(4)
            }
            return (GetLogRandBits(4) shl 10) or RG.GetRnd(10)
        }

        fun GetLen1(): Int = RG.GetRnd(1 + RG.GetRnd(2))

        fun GetLen2(): Int = RG.GetRnd(2 + RG.GetRnd(2))

        fun Generate() {
            RG.Init()
            Rep0 = 1
            val buffer = Buffer!!
            while (Pos < BufferSize) {
                if (GetRndBit() == 0 || Pos < 1) {
                    buffer[Pos++] = RG.GetRnd(8).toByte()
                } else {
                    val len: Int = if (RG.GetRnd(3) == 0) {
                        1 + GetLen1()
                    } else {
                        do {
                            Rep0 = GetOffset()
                        } while (Rep0 >= Pos)
                        Rep0++
                        2 + GetLen2()
                    }
                    var i = 0
                    while (i < len && Pos < BufferSize) {
                        buffer[Pos] = buffer[Pos - Rep0]
                        i++
                        Pos++
                    }
                }
            }
        }
    }

    class CrcOutStream : java.io.OutputStream() {
        var CRC = SevenZip.CRC()

        fun Init() {
            CRC.Init()
        }

        fun GetDigest(): Int = CRC.GetDigest()

        override fun write(b: ByteArray) {
            CRC.Update(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            CRC.Update(b, off, len)
        }

        override fun write(b: Int) {
            CRC.UpdateByte(b)
        }
    }

    class MyOutputStream(private val _buffer: ByteArray) : java.io.OutputStream() {
        private val _size = _buffer.size
        private var _pos = 0

        fun reset() {
            _pos = 0
        }

        @Throws(IOException::class)
        override fun write(b: Int) {
            if (_pos >= _size) {
                throw IOException("Error")
            }
            _buffer[_pos++] = b.toByte()
        }

        fun size(): Int = _pos
    }

    class MyInputStream(private val _buffer: ByteArray, private val _size: Int) : java.io.InputStream() {
        private var _pos = 0

        override fun reset() {
            _pos = 0
        }

        override fun read(): Int {
            if (_pos >= _size) {
                return -1
            }
            return _buffer[_pos++].toInt() and 0xFF
        }
    }

    class CProgressInfo : ICodeProgress {
        var ApprovedStart: Long = 0
        var InSize: Long = 0
        var Time: Long = 0

        fun Init() {
            InSize = 0
        }

        override fun SetProgress(inSize: Long, outSize: Long) {
            if (inSize >= ApprovedStart && InSize == 0L) {
                Time = System.currentTimeMillis()
                InSize = inSize
            }
        }
    }

    companion object {
        const val kAdditionalSize = 1 shl 21
        const val kCompressedAdditionalSize = 1 shl 10
        const val kSubBits = 8

        @JvmStatic
        fun GetLogSize(size: Int): Int {
            for (i in kSubBits until 32) {
                for (j in 0 until (1 shl kSubBits)) {
                    if (size <= (1 shl i) + (j shl (i - kSubBits))) {
                        return (i shl kSubBits) + j
                    }
                }
            }
            return 32 shl kSubBits
        }

        @JvmStatic
        fun MyMultDiv64(value: Long, elapsedTime: Long): Long {
            var freq = 1000L
            var elTime = elapsedTime
            while (freq > 1000000) {
                freq = freq ushr 1
                elTime = elTime ushr 1
            }
            if (elTime == 0L) {
                elTime = 1
            }
            return value * freq / elTime
        }

        @JvmStatic
        fun GetCompressRating(dictionarySize: Int, elapsedTime: Long, size: Long): Long {
            val t = GetLogSize(dictionarySize) - (18 shl kSubBits)
            val numCommandsForOne = 1060 + ((t * t * 10) shr (2 * kSubBits))
            val numCommands = size * numCommandsForOne
            return MyMultDiv64(numCommands, elapsedTime)
        }

        @JvmStatic
        fun GetDecompressRating(elapsedTime: Long, outSize: Long, inSize: Long): Long {
            val numCommands = inSize * 220 + outSize * 20
            return MyMultDiv64(numCommands, elapsedTime)
        }

        @JvmStatic
        fun GetTotalRating(
            dictionarySize: Int,
            elapsedTimeEn: Long,
            sizeEn: Long,
            elapsedTimeDe: Long,
            inSizeDe: Long,
            outSizeDe: Long
        ): Long {
            return (GetCompressRating(dictionarySize, elapsedTimeEn, sizeEn) +
                GetDecompressRating(elapsedTimeDe, inSizeDe, outSizeDe)) / 2
        }

        @JvmStatic
        fun PrintValue(v: Long) {
            val s = v.toString()
            for (i in 0 until (6 - s.length).coerceAtLeast(0)) {
                print(" ")
            }
            print(s)
        }

        @JvmStatic
        fun PrintRating(rating: Long) {
            PrintValue(rating / 1000000)
            print(" MIPS")
        }

        @JvmStatic
        fun PrintResults(
            dictionarySize: Int,
            elapsedTime: Long,
            size: Long,
            decompressMode: Boolean,
            secondSize: Long
        ) {
            val speed = MyMultDiv64(size, elapsedTime)
            PrintValue(speed / 1024)
            print(" KB/s  ")
            val rating = if (decompressMode) {
                GetDecompressRating(elapsedTime, size, secondSize)
            } else {
                GetCompressRating(dictionarySize, elapsedTime, size)
            }
            PrintRating(rating)
        }

        @JvmStatic
        @Throws(Exception::class)
        fun LzmaBenchmark(numIterations: Int, dictionarySize: Int): Int {
            if (numIterations <= 0) {
                return 0
            }
            if (dictionarySize < (1 shl 18)) {
                println("\nError: dictionary size for benchmark must be >= 18 (256 KB)")
                return 1
            }
            print("\n       Compressing                Decompressing\n\n")

            val encoder = SevenZip.Compression.LZMA.Encoder()
            val decoder = SevenZip.Compression.LZMA.Decoder()

            if (!encoder.SetDictionarySize(dictionarySize)) {
                throw Exception("Incorrect dictionary size")
            }

            val kBufferSize = dictionarySize + kAdditionalSize
            val kCompressedBufferSize = (kBufferSize / 2) + kCompressedAdditionalSize

            val propStream = ByteArrayOutputStream()
            encoder.WriteCoderProperties(propStream)
            val propArray = propStream.toByteArray()
            decoder.SetDecoderProperties(propArray)

            val rg = CBenchRandomGenerator()
            rg.Set(kBufferSize)
            rg.Generate()
            val crc = CRC()
            crc.Init()
            crc.Update(rg.Buffer!!, 0, rg.BufferSize)

            val progressInfo = CProgressInfo()
            progressInfo.ApprovedStart = dictionarySize.toLong()

            var totalBenchSize = 0L
            var totalEncodeTime = 0L
            var totalDecodeTime = 0L
            var totalCompressedSize = 0L

            val inStream = MyInputStream(rg.Buffer!!, rg.BufferSize)

            val compressedBuffer = ByteArray(kCompressedBufferSize)
            val compressedStream = MyOutputStream(compressedBuffer)
            val crcOutStream = CrcOutStream()
            var inputCompressedStream: MyInputStream? = null
            var compressedSize = 0

            for (i in 0 until numIterations) {
                progressInfo.Init()
                inStream.reset()
                compressedStream.reset()
                encoder.Code(inStream, compressedStream, -1, -1, progressInfo)
                val encodeTime = System.currentTimeMillis() - progressInfo.Time

                if (i == 0) {
                    compressedSize = compressedStream.size()
                    inputCompressedStream = MyInputStream(compressedBuffer, compressedSize)
                } else if (compressedSize != compressedStream.size()) {
                    throw Exception("Encoding error")
                }

                if (progressInfo.InSize == 0L) {
                    throw Exception("Internal ERROR 1282")
                }

                var decodeTime = 0L
                for (j in 0..1) {
                    inputCompressedStream!!.reset()
                    crcOutStream.Init()

                    val outSize = kBufferSize.toLong()
                    val startTime = System.currentTimeMillis()
                    if (!decoder.Code(inputCompressedStream, crcOutStream, outSize)) {
                        throw Exception("Decoding Error")
                    }
                    decodeTime = System.currentTimeMillis() - startTime
                    if (crcOutStream.GetDigest() != crc.GetDigest()) {
                        throw Exception("CRC Error")
                    }
                }
                val benchSize = kBufferSize - progressInfo.InSize
                PrintResults(dictionarySize, encodeTime, benchSize, false, 0)
                print("     ")
                PrintResults(dictionarySize, decodeTime, kBufferSize.toLong(), true, compressedSize.toLong())
                println()

                totalBenchSize += benchSize
                totalEncodeTime += encodeTime
                totalDecodeTime += decodeTime
                totalCompressedSize += compressedSize.toLong()
            }
            println("---------------------------------------------------")
            PrintResults(dictionarySize, totalEncodeTime, totalBenchSize, false, 0)
            print("     ")
            PrintResults(
                dictionarySize,
                totalDecodeTime,
                kBufferSize.toLong() * numIterations.toLong(),
                true,
                totalCompressedSize
            )
            println("    Average")
            return 0
        }
    }
}
