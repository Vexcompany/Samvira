package com.vexcompany.samvira.core.logging

/**
 * Pure, testable redaction for log output.
 *
 * This is intentionally conservative: it masks common secret shapes
 * (authorization headers, bearer tokens, key/value credentials, URL userinfo,
 * PEM private key blocks) with a placeholder. It is a safety net, not a
 * guarantee — callers must still avoid logging sensitive values in the first
 * place.
 */
object Scrubber {

    private data class Rule(val pattern: Regex, val replacement: String)

    private val rules = listOf(
        // Authorization headers: redact both scheme and credential.
        Rule(Regex("""(?i)authorization\s*[:=]\s*[^\r\n]*"""), "<redacted>"),
        // Bare Bearer <token> occurrences.
        Rule(Regex("""(?i)\bbearer\s+[A-Za-z0-9._~+/=\-]+"""), "<redacted>"),
        // Common key/value credentials. Stop before URL/query delimiters.
        Rule(
            Regex(
                """(?i)\b(api[_-]?key|access[_-]?token|refresh[_-]?token|client[_-]?secret|session[_-]?(id|token)?|credential(s)?|secret|password|token)\b\s*[:=]\s*[^\s,;&?#]+""",
            ),
            "<redacted>",
        ),
        // URL userinfo: scheme://user:password@host.
        Rule(Regex("""(?i)(://)[^/\s@:]+:[^/\s@]*@"""), "$1<redacted>@"),
        // PEM-encoded private keys.
        Rule(
            Regex("""-----BEGIN [A-Z ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z ]*PRIVATE KEY-----"""),
            "<redacted>",
        ),
    )

    fun redact(input: String): String =
        rules.fold(input) { acc, rule -> rule.pattern.replace(acc, rule.replacement) }

    /**
     * Returns a copy of [throwable] with its message redacted and stack trace
     * preserved. The cause chain is deliberately dropped because it may carry
     * sensitive text and is rarely useful at this foundation logging boundary.
     */
    fun sanitizeThrowable(throwable: Throwable?): Throwable? {
        if (throwable == null) return null
        val sanitized = Throwable(redact(throwable.message ?: throwable.javaClass.simpleName))
        sanitized.stackTrace = throwable.stackTrace
        return sanitized
    }
}
