package com.vexcompany.samvira.domain.auth

enum class AuthError {
    KEYSTORE,
    INSTALLATION_CONFLICT,
    UNKNOWN_INSTALLATION,
    UNKNOWN_CHALLENGE,
    CHALLENGE_EXPIRED,
    CHALLENGE_REPLAYED,
    CHALLENGE_MISMATCH,
    INVALID_SIGNATURE,
    MALFORMED_CHALLENGE,
    MALFORMED_REQUEST,
    MALFORMED_RESPONSE,
    SESSION_REJECTED,
    STORAGE,
    NETWORK,
    UNKNOWN,
}

sealed interface AuthResult {
    data class Success(val session: Session) : AuthResult
    data class Failure(val error: AuthError) : AuthResult
}
