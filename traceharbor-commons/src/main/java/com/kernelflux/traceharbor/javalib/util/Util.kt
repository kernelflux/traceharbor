package com.kernelflux.traceharbor.javalib.util

import java.io.File
import java.io.IOException
import java.util.regex.Pattern

object Util {

    @JvmStatic
    fun isNullOrNil(str: String?): Boolean = str.isNullOrEmpty()

    @JvmStatic
    fun nullAsNil(str: String?): String = str ?: ""

    @JvmStatic
    fun preventZipSlip(output: File, zipEntryName: String): Boolean {
        return try {
            zipEntryName.contains("..") &&
                File(output, zipEntryName).canonicalPath
                    .startsWith(output.canonicalPath + File.separator)
        } catch (e: IOException) {
            e.printStackTrace()
            true
        }
    }

    @JvmStatic
    fun isNumber(str: String): Boolean = NUMBER_PATTERN.matcher(str).matches()

    @JvmStatic
    fun byteArrayToHex(data: ByteArray): String {
        val str = CharArray(data.size * 2)
        var k = 0
        for (b in data) {
            val bi = b.toInt()
            str[k++] = HEX_DIGITS[(bi ushr 4) and 0xf]
            str[k++] = HEX_DIGITS[bi and 0xf]
        }
        return String(str)
    }

    @JvmStatic
    fun formatByteUnit(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format("%.2fMB", bytes / (1.0 * 1024 * 1024))
            bytes >= 1024        -> String.format("%.2fKB", bytes / (1.0 * 1024))
            else                 -> String.format("%dBytes", bytes)
        }
    }

    /*
     * Copyright (C) 2011 The Android Open Source Project
     * Licensed under the Apache License, Version 2.0.
     * http://www.apache.org/licenses/LICENSE-2.0
     */
    @JvmStatic
    fun globToRegexp(glob: String): String {
        val sb = StringBuilder(glob.length * 2)
        var begin = 0
        sb.append('^')
        var i = 0
        val n = glob.length
        while (i < n) {
            val c = glob[i]
            if (c == '*') {
                begin = appendQuoted(sb, glob, begin, i) + 1
                if (i < n - 1 && glob[i + 1] == '*') {
                    i++
                    begin++
                }
                sb.append(".*?")
            } else if (c == '?') {
                begin = appendQuoted(sb, glob, begin, i) + 1
                sb.append(".?")
            }
            i++
        }
        appendQuoted(sb, glob, begin, glob.length)
        sb.append('$')
        return sb.toString()
    }

    private fun appendQuoted(sb: StringBuilder, s: String, from: Int, to: Int): Int {
        if (to > from) {
            var isSimple = true
            for (i in from until to) {
                val c = s[i]
                if (!Character.isLetterOrDigit(c) && c != '/' && c != ' ') {
                    isSimple = false
                    break
                }
            }
            if (isSimple) {
                for (i in from until to) sb.append(s[i])
                return to
            }
            sb.append(Pattern.quote(s.substring(from, to)))
        }
        return to
    }

    @JvmStatic
    fun capitalize(word: String): String {
        if (word.isEmpty()) return word
        val upper = Character.toUpperCase(word[0])
        return upper + word.substring(1)
    }

    private val HEX_DIGITS = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
    )
    private val NUMBER_PATTERN: Pattern = Pattern.compile("\\d+")
}
