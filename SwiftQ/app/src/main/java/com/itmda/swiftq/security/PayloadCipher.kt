package com.itmda.swiftq.security

/**
 * Encrypts anything personal (phone numbers, the outbox payload) before it reaches SQLite.
 * Decrypting must return exactly the bytes that went in, so a retry can resend the same body.
 */
interface PayloadCipher {
    /** Returns Base64(iv + ciphertext), safe to store in a TEXT column. */
    fun encrypt(plain: ByteArray): String

    fun decrypt(stored: String): ByteArray
}

fun PayloadCipher.encryptText(plain: String): String = encrypt(plain.toByteArray(Charsets.UTF_8))

fun PayloadCipher.decryptText(stored: String): String = decrypt(stored).toString(Charsets.UTF_8)
