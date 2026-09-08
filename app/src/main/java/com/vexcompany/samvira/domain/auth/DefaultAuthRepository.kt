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
import kotlinx.coroutines.CancellationException

class DefaultAuthRepository(
    private val identityRepository: InstallationIdentityRepository,
    private val keyStore: InstallKeyStore,
    private val remoteClient: RemoteClient,
    private val sessionStore: SessionStore,
    private val clock: () -> Long = System::currentTimeMillis,
) : AuthRepository {

    override suspend fun establishSession(): AuthResult {
        val identity = try { identityRepository.currentIdentity() }
        catch (e: Exception) { return AuthResult.Failure(AuthError.KEYSTORE) }

        when (val registration = remoteClient.register(RegisterRequest(identity.installationId, identity.publicKeyPem))) {
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
        if (challenge.expires_at_epoch_ms <= clock()) return AuthResult.Failure(AuthError.CHALLENGE_EXPIRED)
        val signature = try { keyStore.sign(nonce) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { return AuthResult.Failure(AuthError.KEYSTORE) }
        val verification = when (val result = remoteClient.verify(VerifyRequest(identity.installationId, challenge.challenge_id, b64url(signature)))) {
            is ApiResult.NetworkError -> return AuthResult.Failure(AuthError.NETWORK)
            is ApiResult.ApiError -> return AuthResult.Failure(mapApiError(result.code))
            is ApiResult.Success -> result.value
        }
        val session = Session(SessionToken(verification.session_token), identity.installationId, verification.session_expires_at_epoch_ms)
        try {
            sessionStore.save(SessionRecord(verification.session_token, identity.installationId, verification.session_expires_at_epoch_ms))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return AuthResult.Failure(AuthError.STORAGE)
        }
        return AuthResult.Success(session)
    }

    override suspend fun currentSession(): SessionState {
        val record = try { sessionStore.load() } catch (_: Exception) { return SessionState.None }
            ?: return SessionState.None
        if (record.expiresAtEpochMs <= clock()) {
            try { sessionStore.clear() } catch (_: Exception) {}
            return SessionState.None
        }
        return SessionState.Active(Session(SessionToken(record.token), record.installationId, record.expiresAtEpochMs))
    }

    override suspend fun signOut() {
        val record = try { sessionStore.load() } catch (_: Exception) { null }
        if (record != null) {
            try { remoteClient.revokeSession(record.token) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
        try { sessionStore.clear() } catch (_: Exception) {}
    }

    private fun decodeNonce(challenge: ChallengeResponse): ByteArray? = try {
        Base64.getUrlDecoder().decode(challenge.nonce_b64).takeIf { it.isNotEmpty() }
    } catch (_: IllegalArgumentException) { null }
    private fun b64url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

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
        "REGISTRATION_CAPACITY_REACHED" -> AuthError.NETWORK
        "RATE_LIMITED" -> AuthError.NETWORK
        "SESSION_INVALID", "SESSION_EXPIRED", "SESSION_REVOKED" -> AuthError.SESSION_REJECTED
        else -> AuthError.UNKNOWN
    }
}
