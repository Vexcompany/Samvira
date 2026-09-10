package com.vexcompany.samvira.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vexcompany.samvira.data.auth.SessionStore
import com.vexcompany.samvira.domain.media.MediaItem
import com.vexcompany.samvira.domain.media.MediaRepository
import com.vexcompany.samvira.domain.media.MediaResult
import com.vexcompany.samvira.domain.media.MediaType
import com.vexcompany.samvira.domain.media.MediaViewGrant
import com.vexcompany.samvira.domain.org.OrganizationSelection
import com.vexcompany.samvira.domain.org.OrganizationSelectionStore
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface GalleryUiState {
    data object Loading : GalleryUiState
    data class Ready(
        val items: List<MediaItem>,
        val thumbnails: Map<String, ByteArray> = emptyMap(),
        val loadingThumbnails: Set<String> = emptySet(),
        val selected: MediaItem? = null,
        val selectedGrant: MediaViewGrant? = null,
        val selectedContent: ByteArray? = null,
        val loadingContent: Boolean = false,
        val searchQuery: String = "",
        val mode: GalleryMode = GalleryMode.GALLERY,
        val selectedAlbumId: String? = null,
        val refreshing: Boolean = false,
    ) : GalleryUiState
    data class Error(val code: String, val message: String?) : GalleryUiState
}

enum class GalleryMode { GALLERY, TIMELINE, ALBUMS }

class GalleryViewModel(
    private val mediaRepository: MediaRepository,
    private val sessionStore: SessionStore,
    private val organizationSelection: OrganizationSelectionStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.Loading)
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()
    private val thumbnailJobs = ConcurrentHashMap<String, Job>()

    init { refresh() }

    fun refresh() {
        val refreshing = _uiState.value is GalleryUiState.Ready
        _uiState.update { current ->
            if (refreshing && current is GalleryUiState.Ready) current.copy(refreshing = true)
            else GalleryUiState.Loading
        }
        viewModelScope.launch {
            val session = sessionStore.load()
            val organizationId = (organizationSelection.selection.value as? OrganizationSelection.Selected)?.organization?.id
            if (session == null || organizationId.isNullOrBlank()) {
                _uiState.value = GalleryUiState.Error("NO_CONTEXT", "Sign in and select an organization to view media.")
                return@launch
            }
            when (val result = mediaRepository.listMedia(session.token, organizationId)) {
                is MediaResult.Success -> {
                    val old = _uiState.value as? GalleryUiState.Ready
                    val items = result.value.sortedByDescending { it.createdAtEpochMs }
                    _uiState.value = GalleryUiState.Ready(
                        items = items,
                        thumbnails = old?.thumbnails?.filterKeys { id -> items.any { it.mediaId == id } } ?: emptyMap(),
                        searchQuery = old?.searchQuery.orEmpty(),
                        mode = old?.mode ?: GalleryMode.GALLERY,
                        selectedAlbumId = old?.selectedAlbumId?.takeIf { id -> albumContains(id, items) },
                        refreshing = false,
                    )
                }
                is MediaResult.Failure -> {
                    val old = _uiState.value as? GalleryUiState.Ready
                    if (old != null) {
                        _uiState.value = old.copy(refreshing = false)
                    } else {
                        _uiState.value = GalleryUiState.Error(result.code, result.message)
                    }
                }
            }
        }
    }

    fun setMode(mode: GalleryMode) = _uiState.updateReady {
        it.copy(mode = mode, selectedAlbumId = if (mode == GalleryMode.ALBUMS) it.selectedAlbumId else null)
    }

    fun setSearchQuery(query: String) = _uiState.updateReady { state ->
        val selectedAlbumId = state.selectedAlbumId?.takeIf { albumContains(it, searchMedia(state.items, query)) }
        state.copy(searchQuery = query, selectedAlbumId = selectedAlbumId)
    }

    fun setAlbum(albumId: String?) = _uiState.updateReady { it.copy(selectedAlbumId = albumId) }

    fun loadThumbnail(mediaId: String) {
        val state = _uiState.value as? GalleryUiState.Ready ?: return
        if (state.thumbnails.containsKey(mediaId) || thumbnailJobs.containsKey(mediaId)) return
        thumbnailJobs[mediaId] = viewModelScope.launch {
            try {
                val session = sessionStore.load()
                val organizationId = (organizationSelection.selection.value as? OrganizationSelection.Selected)?.organization?.id
                if (session == null || organizationId.isNullOrBlank()) return@launch
                _uiState.updateReady { it.copy(loadingThumbnails = it.loadingThumbnails + mediaId) }
                when (val result = mediaRepository.fetchThumbnail(session.token, organizationId, mediaId)) {
                    is MediaResult.Success -> _uiState.updateReady { it.copy(thumbnails = it.thumbnails + (mediaId to result.value)) }
                    is MediaResult.Failure -> Unit
                }
            } finally {
                _uiState.updateReady { it.copy(loadingThumbnails = it.loadingThumbnails - mediaId) }
                thumbnailJobs.remove(mediaId)
            }
        }
    }

    fun openMedia(item: MediaItem) {
        _uiState.updateReady { it.copy(selected = item, selectedGrant = null, selectedContent = null, loadingContent = true) }
        viewModelScope.launch {
            val session = sessionStore.load()
            val organizationId = (organizationSelection.selection.value as? OrganizationSelection.Selected)?.organization?.id
            if (session == null || organizationId.isNullOrBlank()) {
                _uiState.updateReady { it.copy(loadingContent = false) }
                return@launch
            }
            when (val grant = mediaRepository.requestView(session.token, organizationId, item.mediaId)) {
                is MediaResult.Success -> {
                    _uiState.updateReady { it.copy(selectedGrant = grant.value) }
                    if (item.type == MediaType.VIDEO) {
                        _uiState.updateReady { it.copy(loadingContent = false) }
                    } else {
                        when (val content = mediaRepository.fetchContent(session.token, organizationId, item.mediaId, grant.value.accessToken)) {
                            is MediaResult.Success -> _uiState.updateReady { it.copy(selectedContent = content.value, loadingContent = false) }
                            is MediaResult.Failure -> _uiState.updateReady { it.copy(loadingContent = false) }
                        }
                    }
                }
                is MediaResult.Failure -> _uiState.updateReady { it.copy(loadingContent = false) }
            }
        }
    }

    fun closeMedia() = _uiState.updateReady { it.copy(selected = null, selectedGrant = null, selectedContent = null, loadingContent = false) }

    private fun albumContains(albumId: String, items: List<MediaItem>): Boolean = when {
        albumId == ALBUM_PHOTOS -> items.any { it.type == MediaType.PHOTO }
        albumId == ALBUM_VIDEOS -> items.any { it.type == MediaType.VIDEO }
        albumId.startsWith(ALBUM_MONTH_PREFIX) -> items.any { monthKey(it.createdAtEpochMs) == albumId.removePrefix(ALBUM_MONTH_PREFIX) }
        else -> false
    }

    private fun monthKey(epochMs: Long): String =
        java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.ROOT).format(java.util.Date(epochMs))

    private inline fun MutableStateFlow<GalleryUiState>.updateReady(transform: (GalleryUiState.Ready) -> GalleryUiState.Ready) =
        update { current -> if (current is GalleryUiState.Ready) transform(current) else current }

    companion object {
        const val ALBUM_PHOTOS = "photos"
        const val ALBUM_VIDEOS = "videos"
        const val ALBUM_MONTH_PREFIX = "month:"
    }
}
