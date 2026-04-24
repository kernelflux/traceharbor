package com.kernelflux.traceharbor.resource.common.utils

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

object DigestUtil {

    private val HEX_DIGITS = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
    )

    @JvmStatic
    fun getMD5String(buffer: ByteArray): String {
        val md = try {
            MessageDigest.getInstance("MD5")
        } catch (e: NoSuchAlgorithmException) {
            // Should not happen — MD5 is mandated by every JRE.
            throw IllegalStateException(e)
        }
        md.update(buffer)
        return bytesToHexString(md.digest())
    }

    private fun bytesToHexString(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            // Original Java tested `b >= 0 && b <= 15` — that branch only ever fires for
            // `b in 0..15`, where the high nibble is 0; preserve the same output.
            if (b in 0..15) {
                sb.append('0').append(HEX_DIGITS[b.toInt()])
            } else {
                sb.append(HEX_DIGITS[(b.toInt() shr 4) and 0x0F])
                  .append(HEX_DIGITS[b.toInt() and 0x0F])
            }
        }
        return sb.toString()
    }
}
