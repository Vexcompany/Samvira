package com.vexcompany.samvira.core.logging

/**
 * Pure, testable redaction for log output.
 *
 * This is intentionally conservative: it masks common secret shapes
 * (authorization headers, bearer tokens, key/value credentials, PEM private
 * key blocks) with a placeholder. It is a safety net, not a guarantee — callers
 * must still avoid logging sensitive values in the first place.
 */
object Scrubber {

    private val patterns = listOf(
        // "Authorization: Bearer <token>" style headers.
        Regex("""(?i)authorization\s*[:=]\s*\S+"""),
        // Bare "Bearer <token>" occurrences.
        Regex("""(?i)\bbearer\s+[A-Za-z0-9._~+/=\-]+"""),
        // key=value / key: value credentials.
        Regex(
            """(?i)\b(api[_-]?key|access[_-]?token|refresh[_-]?token|secret|password|token)\b\s*[:=]\s*[^\s,;]+""",
        ),
        // PEM-encoded private keys.
        Regex("""-----BEGIN [A-Z ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z ]*PRIVATE KEY-----"""),
    )

    fun redact(input: String): String =
        patterns.fold(input) { acc, pattern -> pattern.replace(acc, "<redacted>") }
}
