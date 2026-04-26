package com.kernelflux.traceharbor.resource.hproflib.model

import java.util.Arrays

class ID(
    idBytes: ByteArray,
) {
    private val mIdBytes: ByteArray = idBytes.copyOf()

    fun getBytes(): ByteArray = mIdBytes

    fun getSize(): Int = mIdBytes.size

    override fun equals(other: Any?): Boolean {
        if (other !is ID) {
            return false
        }
        return Arrays.equals(mIdBytes, other.mIdBytes)
    }

    override fun hashCode(): Int = Arrays.hashCode(mIdBytes)

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("0x")
        for (b in mIdBytes) {
            val eb = b.toInt() and 0xFF
            sb.append(Integer.toHexString(eb))
        }
        return sb.toString()
    }

    companion object {
        @JvmStatic
        fun createNullID(size: Int): ID = ID(ByteArray(size))
    }
}

