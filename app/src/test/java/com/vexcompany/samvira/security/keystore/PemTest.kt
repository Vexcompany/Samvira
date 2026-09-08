package com.vexcompany.samvira.security.keystore

import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PemTest {

    @Test
    fun `encoded public key round trips to original der bytes`() {
        val der = ByteArray(65) { index -> (index * 7).toByte() }
        val pem = Pem.encodePublicKey(der)

        assertTrue(pem.startsWith("-----BEGIN PUBLIC KEY-----\n"))
        assertTrue(pem.endsWith("\n-----END PUBLIC KEY-----\n"))

        val body = pem
            .removePrefix("-----BEGIN PUBLIC KEY-----\n")
            .removeSuffix("\n-----END PUBLIC KEY-----\n")
            .replace("\n", "")
        assertArrayEquals(der, Base64.getDecoder().decode(body))
    }

    @Test
    fun `base64 body uses standard 64 character lines`() {
        val der = ByteArray(200) { index -> (index % 251).toByte() }
        val pem = Pem.encodePublicKey(der)
        val body = pem
            .substringAfter("-----BEGIN PUBLIC KEY-----\n")
            .substringBefore("\n-----END PUBLIC KEY-----")
        val lines = body.split("\n")

        assertEquals(true, lines.size > 1)
        assertTrue(lines.dropLast(1).all { it.length == 64 })
    }
}
