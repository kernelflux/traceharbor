package SevenZip

class LzmaAlone {
    class CommandLine {
        var Command = -1
        var NumBenchmarkPasses = 10

        var DictionarySize = 1 shl 23
        var DictionarySizeIsDefined = false

        var Lc = 3
        var Lp = 0
        var Pb = 2

        var Fb = 128
        var FbIsDefined = false

        var Eos = false

        var Algorithm = 2
        var MatchFinder = 1

        lateinit var InFile: String
        lateinit var OutFile: String

        private fun ParseSwitch(s: String): Boolean {
            if (s.startsWith("d")) {
                DictionarySize = 1 shl s.substring(1).toInt()
                DictionarySizeIsDefined = true
            } else if (s.startsWith("fb")) {
                Fb = s.substring(2).toInt()
                FbIsDefined = true
            } else if (s.startsWith("a")) {
                Algorithm = s.substring(1).toInt()
            } else if (s.startsWith("lc")) {
                Lc = s.substring(2).toInt()
            } else if (s.startsWith("lp")) {
                Lp = s.substring(2).toInt()
            } else if (s.startsWith("pb")) {
                Pb = s.substring(2).toInt()
            } else if (s.startsWith("eos")) {
                Eos = true
            } else if (s.startsWith("mf")) {
                val mfs = s.substring(2)
                if (mfs == "bt2") {
                    MatchFinder = 0
                } else if (mfs == "bt4") {
                    MatchFinder = 1
                } else if (mfs == "bt4b") {
                    MatchFinder = 2
                } else {
                    return false
                }
            } else {
                return false
            }
            return true
        }

        @Throws(Exception::class)
        fun Parse(args: Array<String>): Boolean {
            var pos = 0
            var switchMode = true
            for (s in args) {
                if (s.isEmpty()) {
                    return false
                }
                if (switchMode) {
                    if (s == "--") {
                        switchMode = false
                        continue
                    }
                    if (s[0] == '-') {
                        val sw = s.substring(1).lowercase()
                        if (sw.isEmpty()) {
                            return false
                        }
                        try {
                            if (!ParseSwitch(sw)) {
                                return false
                            }
                        } catch (_: NumberFormatException) {
                            return false
                        }
                        continue
                    }
                }
                if (pos == 0) {
                    if (s.equals("e", ignoreCase = true)) {
                        Command = kEncode
                    } else if (s.equals("d", ignoreCase = true)) {
                        Command = kDecode
                    } else if (s.equals("b", ignoreCase = true)) {
                        Command = kBenchmak
                    } else {
                        return false
                    }
                } else if (pos == 1) {
                    if (Command == kBenchmak) {
                        try {
                            NumBenchmarkPasses = s.toInt()
                            if (NumBenchmarkPasses < 1) {
                                return false
                            }
                        } catch (_: NumberFormatException) {
                            return false
                        }
                    } else {
                        InFile = s
                    }
                } else if (pos == 2) {
                    OutFile = s
                } else {
                    return false
                }
                pos++
            }
            return true
        }

        companion object {
            const val kEncode = 0
            const val kDecode = 1
            const val kBenchmak = 2
        }
    }

    companion object {
        @JvmStatic
        fun PrintHelp() {
            println(
                "\nUsage:  LZMA <e|d> [<switches>...] inputFile outputFile\n" +
                    "  e: encode file\n" +
                    "  d: decode file\n" +
                    "  b: Benchmark\n" +
                    "<Switches>\n" +
                    "  -d{N}:  set dictionary - [0,28], default: 23 (8MB)\n" +
                    "  -fb{N}: set number of fast bytes - [5, 273], default: 128\n" +
                    "  -lc{N}: set number of literal context bits - [0, 8], default: 3\n" +
                    "  -lp{N}: set number of literal pos bits - [0, 4], default: 0\n" +
                    "  -pb{N}: set number of pos bits - [0, 4], default: 2\n" +
                    "  -mf{MF_ID}: set Match Finder: [bt2, bt4], default: bt4\n" +
                    "  -eos:   write End Of Stream marker\n"
            )
        }

        @JvmStatic
        @Throws(Exception::class)
        fun main(args: Array<String>) {
            println("\nLZMA (Java) 4.61  2008-11-23\n")

            if (args.isEmpty()) {
                PrintHelp()
                return
            }

            val params = CommandLine()
            if (!params.Parse(args)) {
                println("\nIncorrect command")
                return
            }

            if (params.Command == CommandLine.kBenchmak) {
                var dictionary = 1 shl 21
                if (params.DictionarySizeIsDefined) {
                    dictionary = params.DictionarySize
                }
                if (params.MatchFinder > 1) {
                    throw Exception("Unsupported match finder")
                }
                LzmaBench.LzmaBenchmark(params.NumBenchmarkPasses, dictionary)
            } else if (params.Command == CommandLine.kEncode || params.Command == CommandLine.kDecode) {
                val inFile = java.io.File(params.InFile)
                val outFile = java.io.File(params.OutFile)

                val inStream = java.io.BufferedInputStream(java.io.FileInputStream(inFile))
                val outStream = java.io.BufferedOutputStream(java.io.FileOutputStream(outFile))

                val eos = params.Eos
                if (params.Command == CommandLine.kEncode) {
                    val encoder = SevenZip.Compression.LZMA.Encoder()
                    if (!encoder.SetAlgorithm(params.Algorithm)) {
                        throw Exception("Incorrect compression mode")
                    }
                    if (!encoder.SetDictionarySize(params.DictionarySize)) {
                        throw Exception("Incorrect dictionary size")
                    }
                    if (!encoder.SetNumFastBytes(params.Fb)) {
                        throw Exception("Incorrect -fb value")
                    }
                    if (!encoder.SetMatchFinder(params.MatchFinder)) {
                        throw Exception("Incorrect -mf value")
                    }
                    if (!encoder.SetLcLpPb(params.Lc, params.Lp, params.Pb)) {
                        throw Exception("Incorrect -lc or -lp or -pb value")
                    }
                    encoder.SetEndMarkerMode(eos)
                    encoder.WriteCoderProperties(outStream)
                    val fileSize: Long = if (eos) -1 else inFile.length()
                    for (i in 0..7) {
                        outStream.write(((fileSize ushr (8 * i)) and 0xFF).toInt())
                    }
                    encoder.Code(inStream, outStream, -1, -1, null)
                } else {
                    val propertiesSize = 5
                    val properties = ByteArray(propertiesSize)
                    if (inStream.read(properties, 0, propertiesSize) != propertiesSize) {
                        throw Exception("input .lzma file is too short")
                    }
                    val decoder = SevenZip.Compression.LZMA.Decoder()
                    if (!decoder.SetDecoderProperties(properties)) {
                        throw Exception("Incorrect stream properties")
                    }
                    var outSize = 0L
                    for (i in 0..7) {
                        val v = inStream.read()
                        if (v < 0) {
                            throw Exception("Can't read stream size")
                        }
                        outSize = outSize or (v.toLong() shl (8 * i))
                    }
                    if (!decoder.Code(inStream, outStream, outSize)) {
                        throw Exception("Error in data stream")
                    }
                }
                outStream.flush()
                outStream.close()
                inStream.close()
            } else {
                throw Exception("Incorrect command")
            }
        }
    }
}
