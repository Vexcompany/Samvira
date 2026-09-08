package com.vexcompany.samvira.data.remote

import android.net.Uri

/** Versioned client/server contract. */
object ApiContract {
    const val VERSION_PREFIX = "/api/v1"
    const val REGISTER = "$VERSION_PREFIX/installations/register"
    const val CHALLENGE = "$VERSION_PREFIX/installations/challenge"
    const val VERIFY = "$VERSION_PREFIX/installations/verify"
    const val REVOKE_SESSION = "$VERSION_PREFIX/session/revoke"
    const val ORGANIZATIONS = "$VERSION_PREFIX/organizations"

    fun organizationContext(organizationId: String): String =
        "$ORGANIZATIONS/${Uri.encode(organizationId)}/context"

    const val HEADER_AUTHORIZATION = "Authorization"
    const val HEADER_CONTENT_TYPE = "Content-Type"
    const val HEADER_ORGANIZATION = "X-Organization-Id"
    const val MEDIA_TYPE_JSON = "application/json"
    fun bearer(token: String): String = "Bearer $token"
}
