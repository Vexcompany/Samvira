package com.vexcompany.samvira.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vexcompany.samvira.domain.identity.InstallationIdentityRepository
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI state for the foundation home screen. */
data class HomeUiState(
    val isLoading: Boolean = true,
    val identityProvisioned: Boolean = false,
    val installationId: String? = null,
    val publicKeyFingerprint: String? = null,
    val error: String? = null,
)

class HomeViewModel(
    private val identityRepository: InstallationIdentityRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val identity = identityRepository.currentIdentity()
                _uiState.value = HomeUiState(
                    isLoading = false,
                    identityProvisioned = true,
                    installationId = identity.installationId,
                    publicKeyFingerprint = fingerprint(identity.publicKeyPem),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Do not surface raw Keystore, filesystem, or platform
                // exception text. It may contain implementation details or
                // sensitive request/path material.
                _uiState.value = HomeUiState(
                    isLoading = false,
                    error = "Identity unavailable. Please restart SAMVIRA.",
                )
            }
        }
    }

    private fun fingerprint(pem: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(pem.toByteArray())
        return digest.take(8).joinToString("") { byte ->
            "%02X".format(byte.toInt() and 0xFF)
        }
    }
}
