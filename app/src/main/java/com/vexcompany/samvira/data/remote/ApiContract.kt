package com.vexcompany.samvira.data.remote

/** Versioned client/server contract. */
object ApiContract {
    const val VERSION_PREFIX = "/api/v1"
    const val REGISTER = "$VERSION_PREFIX/installations/register"
    const val CHALLENGE = "$VERSION_PREFIX/installations/challenge"
    const val VERIFY = "$VERSION_PREFIX/installations/verify"
    const val REVOKE_SESSION = "$VERSION_PREFIX/session/revoke"
    const val ORGANIZATIONS = "$VERSION_PREFIX/organizations"
    const val MEDIA = "$VERSION_PREFIX/media"

    fun organizationContext(organizationId: String): String =
        "$ORGANIZATIONS/${encodePathSegment(organizationId)}/context"

    fun media(organizationId: String): String =
        "$MEDIA?organization_id=${encodeQueryValue(organizationId)}"

    fun mediaView(mediaId: String): String =
        "$MEDIA/${encodePathSegment(mediaId)}/view"

    const val HEADER_AUTHORIZATION = "Authorization"
    const val HEADER_CONTENT_TYPE = "Content-Type"
    const val HEADER_ORGANIZATION = "X-Organization-Id"
    const val MEDIA_TYPE_JSON = "application/json"
    fun bearer(token: String): String = "Bearer $token"

    /** Percent-encodes one URL path segment without Android framework dependencies. */
    private fun encodePathSegment(value: String): String = percentEncode(value)

    /** Percent-encodes a query parameter value. */
    private fun encodeQueryValue(value: String): String = percentEncode(value)

    private fun percentEncode(value: String): String {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val out = StringBuilder(bytes.size)
        for (byte in bytes) {
            val unsigned = byte.toInt() and 0xff
            val isUnreserved =
                unsigned in 'a'.code..'z'.code ||
                    unsigned in 'A'.code..'Z'.code ||
                    unsigned in '0'.code..'9'.code ||
                    unsigned == '-'.code ||
                    unsigned == '.'.code ||
                    unsigned == '_'.code ||
                    unsigned == '~'.code
            if (isUnreserved) out.append(unsigned.toChar())
            else {
                out.append('%')
                out.append(HEX[unsigned ushr 4])
                out.append(HEX[unsigned and 0x0f])
            }
        }
        return out.toString()
    }

    private const val HEX = "0123456789ABCDEF"
}
