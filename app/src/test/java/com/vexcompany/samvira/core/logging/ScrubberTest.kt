package com.vexcompany.samvira.core.logging

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `redacts basic authorization headers`() {
        val result = Scrubber.redact("Authorization: Basic dXNlcjpwYXNzd29yZA==")
        assertTrue(result.contains("<redacted>"))
        assertFalse(result.contains("dXNlcjpwYXNzd29yZA=="))
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
    fun `redacts client secret and session identifiers`() {
        val result = Scrubber.redact("client_secret=abcd1234 session_id=deadbeef credential=top")
        assertFalse(result.contains("abcd1234"))
        assertFalse(result.contains("deadbeef"))
        assertFalse(result.contains("top"))
    }

    @Test
    fun `redacts query credentials without swallowing the rest of url`() {
        val result = Scrubber.redact("https://example.com/photos?token=TOPSECRET&page=2")
        assertFalse(result.contains("TOPSECRET"))
        assertTrue(result.contains("<redacted>"))
        assertTrue(result.contains("&page=2"))
    }

    @Test
    fun `redacts url userinfo while preserving host`() {
        val result = Scrubber.redact("https://alice:s3cr3t@example.com/gallery")
        assertEquals("https://<redacted>@example.com/gallery", result)
        assertFalse(result.contains("s3cr3t"))
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

    @Test
    fun `does not over redact ordinary words`() {
        val input = "tokenization of credentials-list requires authorization review"
        assertEquals(input, Scrubber.redact(input))
    }

    @Test
    fun `does not redact url host or port`() {
        val input = "https://example.com:8080/path"
        assertEquals(input, Scrubber.redact(input))
    }

    @Test
    fun `sanitizeThrowable redacts message and preserves stack trace`() {
        val original = RuntimeException("auth failed token=SECRET")
        val sanitized = Scrubber.sanitizeThrowable(original)
        assertTrue(sanitized != null)
        assertTrue(sanitized!!.message!!.contains("<redacted>"))
        assertFalse(sanitized.message!!.contains("SECRET"))
        assertArrayEquals(original.stackTrace, sanitized.stackTrace)
    }

    @Test
    fun `sanitizeThrowable returns null for null`() {
        assertNull(Scrubber.sanitizeThrowable(null))
    }
}
