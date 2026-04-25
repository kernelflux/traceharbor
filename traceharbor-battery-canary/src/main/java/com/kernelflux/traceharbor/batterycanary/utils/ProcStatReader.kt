/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.kernelflux.traceharbor.batterycanary.utils

import java.io.IOException
import java.io.RandomAccessFile
import java.nio.CharBuffer
import java.util.NoSuchElementException

internal class ProcStatReader @JvmOverloads constructor(
    private val path: String,
    private val buffer: ByteArray = ByteArray(512)
) {
    private var file: RandomAccessFile? = null

    private var position = -1
    private var bufferSize = 0

    private var char = 0.toChar()
    private var prev = 0.toChar()

    private var isValid = true
    private var rewound = false

    @Throws(ProcStatUtil.ParseException::class)
    fun reset(): ProcStatReader {
        // Be optimistic
        isValid = true

        // First, try to move the pointer if a file exists
        file?.let {
            try {
                it.seek(0)
            } catch (ioe: IOException) {
                close()
            }
        }

        // Otherwise try to open/reopen the file and fail
        if (file == null) {
            try {
                file = RandomAccessFile(path, "r")
            } catch (ioe: IOException) {
                isValid = false
                close()
                throw ProcStatUtil.ParseException("RAF err: " + ioe.message)
            }
        }

        if (isValid) {
            position = -1
            bufferSize = 0

            char = 0.toChar()
            prev = 0.toChar()

            rewound = false
        }

        return this
    }

    fun isValid(): Boolean {
        return isValid
    }

    fun hasNext(): Boolean {
        val currentFile = file
        if (!isValid || currentFile == null || position > bufferSize - 1) {
            return false
        }

        if (position < bufferSize - 1) {
            return true
        }

        try {
            bufferSize = currentFile.read(buffer)
            position = -1
        } catch (ioe: IOException) {
            isValid = false
            close()
        }

        return hasNext()
    }

    fun hasReachedEOF(): Boolean {
        return bufferSize == -1
    }

    private fun next() {
        if (!hasNext()) {
            throw NoSuchElementException()
        }

        position++
        prev = char
        char = buffer[position].toInt().toChar()

        rewound = false
    }

    private fun rewind() {
        if (rewound) {
            throw ParseException("Can only rewind one step!")
        }

        position--
        char = prev
        rewound = true
    }

    fun readWord(buffer: CharBuffer): CharBuffer {
        var output = buffer
        output.clear()

        var isFirstRun = true

        while (hasNext()) {
            next()
            if (!Character.isWhitespace(char)) {
                if (!output.hasRemaining()) {
                    val newBuffer = CharBuffer.allocate(output.capacity() * 2)
                    output.flip()
                    newBuffer.put(output)
                    output = newBuffer
                }

                output.put(char)
            } else if (isFirstRun) {
                throw ParseException("Couldn't read string!")
            } else {
                rewind()
                break
            }

            isFirstRun = false
        }

        if (isFirstRun) {
            throw ParseException("Couldn't read string because file ended!")
        }

        output.flip()
        return output
    }

    fun readNumber(): Long {
        var sign = 1L
        var result = 0L
        var isFirstRun = true

        while (hasNext()) {
            next()
            if (Character.isDigit(char)) {
                result = result * 10 + (char.code - '0'.code)
            } else if (isFirstRun) {
                if (char == '-') {
                    sign = -1L
                } else {
                    throw ParseException("Couldn't read number!")
                }
            } else {
                rewind()
                break
            }

            isFirstRun = false
        }

        if (isFirstRun) {
            throw ParseException("Couldn't read number because the file ended!")
        }

        return sign * result
    }

    fun readToSymbol(symbol: Char, buffer: CharBuffer): CharBuffer {
        var output = buffer
        output.clear()
        var isFirstRun = true

        while (hasNext()) {
            next()
            if (symbol != char) {
                if (!output.hasRemaining()) {
                    val newBuffer = CharBuffer.allocate(output.capacity() * 2)
                    output.flip()
                    newBuffer.put(output)
                    output = newBuffer
                }
                output.put(char)
            } else if (isFirstRun) {
                throw ParseException("Couldn't read string!")
            } else {
                rewind()
                break
            }
            isFirstRun = false
        }

        if (isFirstRun) {
            throw ParseException("Couldn't read string because file ended!")
        }
        output.flip()
        return output
    }

    fun skipSpaces() {
        skipPast(' ')
    }

    fun skipLeftBrace() {
        skipPast('(')
    }

    fun skipRightBrace() {
        skipPast(')')
    }

    fun skipLine() {
        skipPast('\n')
    }

    fun skipPast(skipPast: Char) {
        var found = false
        while (hasNext()) {
            next()

            if (char == skipPast) {
                found = true
            } else if (found) {
                rewind()
                break
            }
        }
    }

    fun close() {
        file?.let {
            try {
                it.close()
            } catch (ioe: IOException) {
                // Ignored
            } finally {
                file = null
            }
        }
    }

    @Throws(Throwable::class)
    protected fun finalize() {
        close()
    }

    private class ParseException(message: String) : RuntimeException(message)
}
