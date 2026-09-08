package com.vexcompany.samvira.security.keystore

import java.util.Base64

/** Pure PEM (RFC 7468) encoding for public keys. */
object Pem {

    private const val LINE_LENGTH = 64

    /** Encodes DER SubjectPublicKeyInfo bytes as a PUBLIC KEY PEM block. */
    fun encodePublicKey(der: ByteArray): String = encode("PUBLIC KEY", der)

    fun encode(type: String, der: ByteArray): String {
        require(type.isNotBlank()) { "PEM type must not be blank" }
        val body = Base64.getEncoder()
            .encodeToString(der)
            .chunked(LINE_LENGTH)
            .joinToString("\n")
        return buildString {
            append("-----BEGIN ").append(type).append("-----\n")
            append(body).append("\n")
            append("-----END ").append(type).append("-----\n")
        }
    }
}
