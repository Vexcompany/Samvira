package com.vexcompany.samvira.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vexcompany.samvira.domain.auth.AuthError
import com.vexcompany.samvira.domain.auth.AuthRepository
import com.vexcompany.samvira.domain.auth.AuthResult
import com.vexcompany.samvira.domain.auth.SessionState
import com.vexcompany.samvira.domain.identity.InstallationIdentityRepository
import com.vexcompany.samvira.domain.org.Organization
import com.vexcompany.samvira.domain.org.OrganizationContextResult
import com.vexcompany.samvira.domain.org.OrganizationError
import com.vexcompany.samvira.domain.org.OrganizationRepository
import com.vexcompany.samvira.domain.org.OrganizationSelection
import com.vexcompany.samvira.domain.org.OrganizationSelectionStore
import com.vexcompany.samvira.domain.org.OrganizationsResult
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SessionUi {
    data object Disconnected : SessionUi
    data class Active(val expiresAtEpochMs: Long) : SessionUi
    data class Failed(val reason: AuthError) : SessionUi
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val identityProvisioned: Boolean = false,
    val installationId: String? = null,
    val publicKeyFingerprint: String? = null,
    val error: String? = null,
    val sessionState: SessionUi = SessionUi.Disconnected,
    val connecting: Boolean = false,
    val organizations: List<Organization> = emptyList(),
    val organizationsLoading: Boolean = false,
    val organizationsError: OrganizationError? = null,
    val selection: OrganizationSelection = OrganizationSelection.None,
    val contextLoading: Boolean = false,
    val contextError: OrganizationError? = null,
)

class HomeViewModel(
    private val identityRepository: InstallationIdentityRepository,
    private val authRepository: AuthRepository,
    private val organizationRepository: OrganizationRepository,
    private val organizationSelection: OrganizationSelectionStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var sessionGeneration = 0L

    init {
        viewModelScope.launch { loadIdentity() }
        viewModelScope.launch { refreshSession() }
    }

    fun connect() {
        viewModelScope.launch {
            val generation = ++sessionGeneration
            _uiState.update { it.copy(connecting = true, sessionState = SessionUi.Disconnected) }
            when (val result = authRepository.establishSession()) {
                is AuthResult.Success -> {
                    if (generation != sessionGeneration) return@launch
                    _uiState.update { it.copy(connecting = false, sessionState = SessionUi.Active(result.session.expiresAtEpochMs)) }
                    loadOrganizations(generation)
                }
                is AuthResult.Failure -> if (generation == sessionGeneration) {
                    _uiState.update { it.copy(connecting = false, sessionState = SessionUi.Failed(result.error)) }
                }
            }
        }
    }

    fun signOut() {
        sessionGeneration++
        viewModelScope.launch {
            authRepository.signOut()
            organizationSelection.clear()
            _uiState.update { it.copy(sessionState = SessionUi.Disconnected, organizations = emptyList(), selection = OrganizationSelection.None, organizationsError = null, contextError = null, connecting = false, contextLoading = false) }
        }
    }

    fun selectOrganization(organization: Organization) {
        val generation = sessionGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(contextLoading = true, contextError = null) }
            when (val result = organizationRepository.organizationContext(organization.id)) {
                is OrganizationContextResult.Success -> if (generation == sessionGeneration) {
                    organizationSelection.select(result.organization)
                    _uiState.update { it.copy(contextLoading = false, selection = OrganizationSelection.Selected(result.organization)) }
                }
                is OrganizationContextResult.Failure -> if (generation == sessionGeneration) {
                    organizationSelection.clear()
                    if (result.error == OrganizationError.SESSION_REJECTED) {
                        authRepository.signOut()
                        sessionGeneration++
                        _uiState.update { it.copy(contextLoading = false, contextError = null, selection = OrganizationSelection.None, sessionState = SessionUi.Disconnected, organizations = emptyList()) }
                    } else {
                        _uiState.update { it.copy(contextLoading = false, contextError = result.error, selection = OrganizationSelection.None) }
                    }
                }
            }
        }
    }

    fun clearSelection() {
        organizationSelection.clear()
        _uiState.update { it.copy(selection = OrganizationSelection.None, contextError = null) }
    }

    private suspend fun refreshSession() {
        when (val state = authRepository.currentSession()) {
            is SessionState.Active -> {
                val generation = sessionGeneration
                _uiState.update { it.copy(sessionState = SessionUi.Active(state.session.expiresAtEpochMs)) }
                loadOrganizations(generation)
            }
            SessionState.None -> Unit
        }
    }

    private suspend fun loadOrganizations(generation: Long = sessionGeneration) {
        if (generation != sessionGeneration) return
        _uiState.update { it.copy(organizationsLoading = true, organizationsError = null) }
        when (val result = organizationRepository.listOrganizations()) {
            is OrganizationsResult.Success -> if (generation == sessionGeneration) {
                _uiState.update { it.copy(organizationsLoading = false, organizations = result.organizations) }
            }
            is OrganizationsResult.Failure -> if (generation == sessionGeneration) {
                if (result.error == OrganizationError.SESSION_REJECTED) {
                    authRepository.signOut()
                    organizationSelection.clear()
                    sessionGeneration++
                    _uiState.update { it.copy(organizationsLoading = false, organizationsError = null, organizations = emptyList(), selection = OrganizationSelection.None, sessionState = SessionUi.Disconnected) }
                } else {
                    _uiState.update { it.copy(organizationsLoading = false, organizationsError = result.error, organizations = emptyList()) }
                }
            }
        }
    }

    private suspend fun loadIdentity() {
        try {
            val identity = identityRepository.currentIdentity()
            _uiState.update { it.copy(isLoading = false, identityProvisioned = true, installationId = identity.installationId, publicKeyFingerprint = fingerprint(identity.publicKeyPem)) }
        } catch (e: CancellationException) { throw e }
        catch (_: Throwable) { _uiState.update { it.copy(isLoading = false, error = "Identity unavailable. Please restart SAMVIRA.") } }
    }

    private fun fingerprint(pem: String): String = MessageDigest.getInstance("SHA-256").digest(pem.toByteArray()).take(8).joinToString("") { "%02X".format(it.toInt() and 0xFF) }
}
