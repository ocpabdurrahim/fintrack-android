package io.github.ocpabdurrahim.fin.domain

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The app-lock PIN is never stored, only "salt:sha256(salt:pin)". This guards
 * against someone picking up an unlocked phone; it is not encryption of the data.
 */
object Pin {
    const val LENGTH = 4

    fun hash(pin: String, salt: String = newSalt()): String = "$salt:${sha256("$salt:$pin")}"

    fun verify(pin: String, stored: String): Boolean {
        val salt = stored.substringBefore(':', missingDelimiterValue = "")
        if (salt.isEmpty()) return false
        return MessageDigest.isEqual(hash(pin, salt).toByteArray(), stored.toByteArray())
    }

    private fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).toHex()

    private fun ByteArray.toHex(): String {
        val digits = "0123456789abcdef"
        val out = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            out.append(digits[v shr 4]).append(digits[v and 0x0F])
        }
        return out.toString()
    }
}
