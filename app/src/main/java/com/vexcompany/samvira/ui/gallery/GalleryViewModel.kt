package com.vexcompany.samvira.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vexcompany.samvira.data.auth.SessionStore
import com.vexcompany.samvira.domain.media.MediaItem
import com.vexcompany.samvira.domain.media.MediaRepository
import com.vexcompany.samvira.domain.media.MediaResult
import com.vexcompany.samvira.domain.org.OrganizationSelection
import com.vexcompany.samvira.domain.org.OrganizationSelectionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface GalleryUiState {
    data object Loading : GalleryUiState
    data class Ready(val items: List<MediaItem>, val refreshing: Boolean = false) : GalleryUiState
    data class Error(val code: String, val message: String?) : GalleryUiState
}

class GalleryViewModel(
    private val mediaRepository: MediaRepository,
    private val sessionStore: SessionStore,
    private val organizationSelection: OrganizationSelectionStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.Loading)
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        val refreshing = _uiState.value is GalleryUiState.Ready
        _uiState.update { if (refreshing && it is GalleryUiState.Ready) it.copy(refreshing = true) else GalleryUiState.Loading }
        viewModelScope.launch {
            val session = sessionStore.load()
            val organizationId = (organizationSelection.selection.value as? OrganizationSelection.Selected)?.organization?.id
            if (session == null || organizationId.isNullOrBlank()) {
                _uiState.value = GalleryUiState.Error("NO_CONTEXT", "Sign in and select an organization to view media.")
                return@launch
            }
            when (val result = mediaRepository.listMedia(session.token, organizationId)) {
                is MediaResult.Success -> _uiState.value = GalleryUiState.Ready(result.value)
                is MediaResult.Failure -> _uiState.value = GalleryUiState.Error(result.code, result.message)
            }
        }
    }
}
