package com.vexcompany.samvira.ui.gallery

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vexcompany.samvira.domain.media.MediaItem
import com.vexcompany.samvira.domain.media.MediaType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(viewModel: GalleryViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (state) {
        GalleryUiState.Loading -> CenteredMessage { CircularProgressIndicator() }
        is GalleryUiState.Error -> CenteredMessage { Text(state.message ?: state.code, color = MaterialTheme.colorScheme.error) }
        is GalleryUiState.Ready -> {
            Scaffold(
                topBar = {
                    Column {
                        TopAppBar(title = { Text("Gallery", fontWeight = FontWeight.SemiBold) })
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            singleLine = true,
                            label = { Text("Search photos and videos") },
                        )
                        ScrollableTabRow(selectedTabIndex = state.mode.ordinal) {
                            GalleryMode.entries.forEach { mode ->
                                Tab(
                                    selected = state.mode == mode,
                                    onClick = { viewModel.setMode(mode) },
                                    text = { Text(mode.label()) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                GalleryContent(state, padding, viewModel)
            }
            state.selected?.let { selected ->
                MediaDetailDialog(state, selected, viewModel)
            }
        }
    }
}

@Composable
private fun GalleryContent(
    state: GalleryUiState.Ready,
    padding: PaddingValues,
    viewModel: GalleryViewModel,
) {
    val query = state.searchQuery.trim().lowercase(Locale.ROOT)
    val filtered = state.items.filter { item ->
        query.isEmpty() || item.mediaId.lowercase(Locale.ROOT).contains(query) || item.mimeType.lowercase(Locale.ROOT).contains(query)
    }

    if (filtered.isEmpty()) {
        CenteredMessage(padding) { Text(if (query.isEmpty()) "No photos or videos yet." else "No matching media.") }
        return
    }

    when (state.mode) {
        GalleryMode.GALLERY -> MediaGrid(filtered, state, padding, viewModel)
        GalleryMode.TIMELINE -> Timeline(filtered, state, padding, viewModel)
        GalleryMode.ALBUMS -> Albums(filtered, state, padding, viewModel)
    }
}

@Composable
private fun MediaGrid(items: List<MediaItem>, state: GalleryUiState.Ready, padding: PaddingValues, viewModel: GalleryViewModel) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(items, key = { it.mediaId }) { item -> MediaTile(item, state, viewModel) }
    }
}

@Composable
private fun Timeline(items: List<MediaItem>, state: GalleryUiState.Ready, padding: PaddingValues, viewModel: GalleryViewModel) {
    val groups = items.groupBy { dayLabel(it.createdAtEpochMs) }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(8.dp)) {
        groups.forEach { (day, dayItems) ->
            item(key = "header-$day") {
                Text(day, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(8.dp))
            }
            item(key = "grid-$day") { MediaGrid(dayItems, state, PaddingValues(0.dp), viewModel) }
        }
    }
}

@Composable
private fun Albums(items: List<MediaItem>, state: GalleryUiState.Ready, padding: PaddingValues, viewModel: GalleryViewModel) {
    val albums = items.groupBy { monthLabel(it.createdAtEpochMs) }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        albums.forEach { (month, monthItems) ->
            item(key = "album-$month") {
                Column {
                    Text(month, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
                    Text("${monthItems.size} items", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp))
                    MediaGrid(monthItems.take(9), state, PaddingValues(0.dp), viewModel)
                }
            }
        }
    }
}

@Composable
private fun MediaTile(item: MediaItem, state: GalleryUiState.Ready, viewModel: GalleryViewModel) {
    androidx.compose.runtime.LaunchedEffect(item.mediaId) { viewModel.loadThumbnail(item.mediaId) }
    val bitmap = state.thumbnails[item.mediaId]?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { viewModel.openMedia(item) },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (item.mediaId in state.loadingThumbnails) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(if (item.type == MediaType.VIDEO) "VIDEO" else "PHOTO", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MediaDetailDialog(state: GalleryUiState.Ready, item: MediaItem, viewModel: GalleryViewModel) {
    val bytes = state.selectedContent
    val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    AlertDialog(
        onDismissRequest = viewModel::closeMedia,
        confirmButton = {},
        title = { Text(if (item.type == MediaType.VIDEO) "Video" else "Photo") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    state.loadingContent -> CircularProgressIndicator()
                    bitmap != null -> Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Fit)
                    item.type == MediaType.VIDEO && state.thumbnails[item.mediaId] != null -> {
                        val thumb = BitmapFactory.decodeByteArray(state.thumbnails[item.mediaId], 0, state.thumbnails[item.mediaId]!!.size)?.asImageBitmap()
                        if (thumb != null) Image(thumb, contentDescription = "Video preview", modifier = Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Fit)
                        Text("Video preview", modifier = Modifier.padding(top = 8.dp))
                    }
                    else -> Text("Media preview is unavailable.")
                }
                Text(item.mimeType, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
            }
        },
    )
}

@Composable
private fun CenteredMessage(padding: PaddingValues = PaddingValues(0.dp), content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { content() }
}

private fun GalleryMode.label(): String = when (this) {
    GalleryMode.GALLERY -> "Gallery"
    GalleryMode.TIMELINE -> "Timeline"
    GalleryMode.ALBUMS -> "Albums"
}

private fun dayLabel(epochMs: Long): String = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date(epochMs))
private fun monthLabel(epochMs: Long): String = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(epochMs))
