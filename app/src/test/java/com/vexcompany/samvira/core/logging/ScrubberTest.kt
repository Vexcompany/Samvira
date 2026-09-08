package com.vexcompany.samvira.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrubberTest {

    @Test
    fun `redacts authorization headers`() {
        val result = Scrubber.redact("Authorization: Bearer abc.def-123")
        assertTrue(result.contains("<redacted>"))
        assertFalse(result.contains("abc.def-123"))
    }

    @Test
    fun `redacts bare bearer tokens`() {
        val result = Scrubber.redact("request failed for Bearer abcdef123456")
        assertTrue(result.contains("<redacted>"))
        assertFalse(result.contains("abcdef123456"))
    }

    @Test
    fun `redacts key value credentials`() {
        val result = Scrubber.redact("api_key=sekrit access_token=zzz password: hunter2")
        assertFalse(result.contains("sekrit"))
        assertFalse(result.contains("zzz"))
        assertFalse(result.contains("hunter2"))
    }

    @Test
    fun `redacts pem private keys`() {
        val pem = """
            -----BEGIN PRIVATE KEY-----
            MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC7
            -----END PRIVATE KEY-----
        """.trimIndent()
        val result = Scrubber.redact("key material follows:\n$pem")
        assertTrue(result.contains("<redacted>"))
        assertFalse(result.contains("MIIEvQIBADAN"))
    }

    @Test
    fun `leaves benign text untouched`() {
        assertEquals("hello world", Scrubber.redact("hello world"))
        assertEquals("loading album 42", Scrubber.redact("loading album 42"))
    }
}
