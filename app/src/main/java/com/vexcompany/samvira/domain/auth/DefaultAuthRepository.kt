package com.vexcompany.samvira.domain.auth

import com.vexcompany.samvira.data.auth.SessionRecord
import com.vexcompany.samvira.data.auth.SessionStore
import com.vexcompany.samvira.data.remote.ApiResult
import com.vexcompany.samvira.data.remote.ChallengeResponse
import com.vexcompany.samvira.data.remote.RegisterRequest
import com.vexcompany.samvira.data.remote.RemoteClient
import com.vexcompany.samvira.data.remote.VerifyRequest
import com.vexcompany.samvira.domain.identity.InstallationIdentityRepository
import com.vexcompany.samvira.security.keystore.InstallKeyStore
import java.util.Base64

/**
 * Orchestrates the register → challenge → sign → verify flow.
 *
 * The private key never leaves the Keystore: the repository receives only the
 * nonce bytes, calls [InstallKeyStore.sign], and forwards the signature. The
 * session token is held as a [SessionToken] whose string form is redacted.
 */
class DefaultAuthRepository(
    private val identityRepository: InstallationIdentityRepository,
    private val keyStore: InstallKeyStore,
    private val remoteClient: RemoteClient,
    private val sessionStore: SessionStore,
    private val clock: () -> Long = System::currentTimeMillis,
) : AuthRepository {

    override suspend fun establishSession(): AuthResult {
        val identity = try {
            identityRepository.currentIdentity()
        } catch (e: Exception) {
            return AuthResult.Failure(AuthError.KEYSTORE)
        }

        when (
            val registration = remoteClient.register(
                RegisterRequest(
                    installation_id = identity.installationId,
                    public_key_pem = identity.publicKeyPem,
                ),
            )
        ) {
            is ApiResult.NetworkError -> return AuthResult.Failure(AuthError.NETWORK)
            is ApiResult.ApiError -> return AuthResult.Failure(mapApiError(registration.code))
            is ApiResult.Success -> Unit
        }

        val challenge = when (val result = remoteClient.requestChallenge(identity.installationId)) {
            is ApiResult.NetworkError -> return AuthResult.Failure(AuthError.NETWORK)
            is ApiResult.ApiError -> return AuthResult.Failure(mapApiError(result.code))
            is ApiResult.Success -> result.value
        }

        val nonce = decodeNonce(challenge) ?: return AuthResult.Failure(AuthError.MALFORMED_CHALLENGE)
        if (challenge.expires_at_epoch_ms <= clock()) {
            return AuthResult.Failure(AuthError.CHALLENGE_EXPIRED)
        }

        val signature = try {
            keyStore.sign(nonce)
        } catch (e: Exception) {
            return AuthResult.Failure(AuthError.KEYSTORE)
        }

        val verification = when (
            val result = remoteClient.verify(
                VerifyRequest(
                    installation_id = identity.installationId,
                    challenge_id = challenge.challenge_id,
                    signature_b64 = b64url(signature),
                ),
            )
        ) {
            is ApiResult.NetworkError -> return AuthResult.Failure(AuthError.NETWORK)
            is ApiResult.ApiError -> return AuthResult.Failure(mapApiError(result.code))
            is ApiResult.Success -> result.value
        }

        val session = Session(
            token = SessionToken(verification.session_token),
            installationId = identity.installationId,
            expiresAtEpochMs = verification.session_expires_at_epoch_ms,
        )
        sessionStore.save(
            SessionRecord(
                token = verification.session_token,
                installationId = identity.installationId,
                expiresAtEpochMs = verification.session_expires_at_epoch_ms,
            ),
        )
        return AuthResult.Success(session)
    }

    override suspend fun currentSession(): SessionState {
        val record = sessionStore.load() ?: return SessionState.None
        if (record.expiresAtEpochMs <= clock()) {
            sessionStore.clear()
            return SessionState.None
        }
        return SessionState.Active(
            Session(
                token = SessionToken(record.token),
                installationId = record.installationId,
                expiresAtEpochMs = record.expiresAtEpochMs,
            ),
        )
    }

    override suspend fun signOut() {
        val record = sessionStore.load()
        if (record != null) {
            remoteClient.revokeSession(record.token)
        }
        sessionStore.clear()
    }

    private fun decodeNonce(challenge: ChallengeResponse): ByteArray? = try {
        val nonce = Base64.getUrlDecoder().decode(challenge.nonce_b64)
        if (nonce.isEmpty()) null else nonce
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun b64url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun mapApiError(code: String): AuthError = when (code) {
        "INSTALLATION_CONFLICT" -> AuthError.INSTALLATION_CONFLICT
        "UNKNOWN_INSTALLATION" -> AuthError.UNKNOWN_INSTALLATION
        "UNKNOWN_CHALLENGE" -> AuthError.UNKNOWN_CHALLENGE
        "CHALLENGE_EXPIRED" -> AuthError.CHALLENGE_EXPIRED
        "CHALLENGE_REPLAYED" -> AuthError.CHALLENGE_REPLAYED
        "CHALLENGE_MISMATCH" -> AuthError.CHALLENGE_MISMATCH
        "INVALID_SIGNATURE" -> AuthError.INVALID_SIGNATURE
        "MALFORMED_REQUEST" -> AuthError.MALFORMED_REQUEST
        "MALFORMED_RESPONSE" -> AuthError.MALFORMED_RESPONSE
        "SESSION_INVALID", "SESSION_EXPIRED", "SESSION_REVOKED" -> AuthError.SESSION_REJECTED
        else -> AuthError.UNKNOWN
    }
}
