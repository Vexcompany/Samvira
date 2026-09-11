package com.vexcompany.samvira.ui.gallery

import android.app.Activity
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.ViewGroup
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.vexcompany.samvira.domain.media.MediaItem
import com.vexcompany.samvira.domain.media.MediaType
import com.vexcompany.samvira.security.ScreenGuard
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(viewModel: GalleryViewModel) {
    val context = LocalContext.current
    DisposableEffect(context) {
        (context as? Activity)?.let(ScreenGuard::enable)
        onDispose { (context as? Activity)?.let(ScreenGuard::disable) }
    }
    val state by viewModel.uiState.collectAsState()
    when (val currentState = state) {
        GalleryUiState.Loading -> CenteredMessage { CircularProgressIndicator() }
        is GalleryUiState.Error -> CenteredMessage { Text(currentState.message ?: currentState.code, color = MaterialTheme.colorScheme.error) }
        is GalleryUiState.Ready -> {
            Scaffold(
                topBar = {
                    Column {
                        TopAppBar(title = { Text("Gallery", fontWeight = FontWeight.SemiBold) })
                        OutlinedTextField(
                            value = currentState.searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            singleLine = true,
                            label = { Text("Search photos and videos") },
                            placeholder = { Text("Try: photo, video, 2026, 1920x1080") },
                        )
                        ScrollableTabRow(selectedTabIndex = currentState.mode.ordinal) {
                            GalleryMode.entries.forEach { mode ->
                                Tab(
                                    selected = currentState.mode == mode,
                                    onClick = {
                                        viewModel.setMode(mode)
                                        if (mode != GalleryMode.ALBUMS) viewModel.setAlbum(null)
                                    },
                                    text = { Text(mode.label()) },
                                )
                            }
                        }
                    }
                },
            ) { padding -> GalleryContent(currentState, padding, viewModel) }
            currentState.selected?.let { selected -> MediaDetailDialog(currentState, selected, viewModel) }
        }
    }
}

@Composable
private fun GalleryContent(state: GalleryUiState.Ready, padding: PaddingValues, viewModel: GalleryViewModel) {
    val searched = searchMedia(state.items, state.searchQuery)
    if (searched.isEmpty()) {
        CenteredMessage(padding) { Text(if (state.searchQuery.isBlank()) "No photos or videos yet." else "No matching media.") }
        return
    }
    when (state.mode) {
        GalleryMode.GALLERY -> MediaGrid(searched, state, padding, viewModel)
        GalleryMode.TIMELINE -> Timeline(searched, state, padding, viewModel)
        GalleryMode.ALBUMS -> Albums(searched, state, padding, viewModel)
    }
}

@Composable
private fun MediaGrid(items: List<MediaItem>, state: GalleryUiState.Ready, padding: PaddingValues, viewModel: GalleryViewModel) {
    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(items, key = { it.mediaId }) { item -> MediaTile(item, state, viewModel) }
    }
}

@Composable
private fun Timeline(items: List<MediaItem>, state: GalleryUiState.Ready, padding: PaddingValues, viewModel: GalleryViewModel) {
    val groups = items.groupBy { dayKey(it.createdAtEpochMs) }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        groups.forEach { (day, dayItems) ->
            androidx.compose.foundation.lazy.stickyHeader(key = "timeline-header-$day") { TimelineHeader(dayItems.first().createdAtEpochMs, dayItems.size) }
            item(key = "timeline-media-$day") {
                Column(modifier = Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    dayItems.chunked(3).forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            rowItems.forEach { item -> MediaTile(item, state, viewModel, Modifier.weight(1f)) }
                            repeat(3 - rowItems.size) { Box(Modifier.weight(1f).aspectRatio(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineHeader(epochMs: Long, count: Int) {
    Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(dayLabel(epochMs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("$count ${if (count == 1) "item" else "items"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class Album(val id: String, val title: String, val subtitle: String, val items: List<MediaItem>)

@Composable
private fun Albums(items: List<MediaItem>, state: GalleryUiState.Ready, padding: PaddingValues, viewModel: GalleryViewModel) {
    val albums = buildAlbums(items)
    val selectedId = state.selectedAlbumId
    if (selectedId != null) {
        val album = albums.firstOrNull { it.id == selectedId }
        if (album != null) { AlbumDetail(album, state, padding, viewModel); return }
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(albums, key = { it.id }) { album -> AlbumCard(album, state, viewModel) }
    }
}

@Composable
private fun AlbumCard(album: Album, state: GalleryUiState.Ready, viewModel: GalleryViewModel) {
    Column(modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant).clickable { viewModel.setAlbum(album.id) }.padding(10.dp)) {
        MediaRows(album.items.take(4), state, viewModel)
        Text(album.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
        Text(album.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun AlbumDetail(album: Album, state: GalleryUiState.Ready, padding: PaddingValues, viewModel: GalleryViewModel) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(modifier = Modifier.fillMaxWidth().clickable { viewModel.setAlbum(null) }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹ Albums", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("  ${album.items.size} ${if (album.items.size == 1) "item" else "items"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MediaGrid(album.items, state, PaddingValues(0.dp), viewModel)
    }
}

private fun buildAlbums(items: List<MediaItem>): List<Album> {
    val albums = mutableListOf<Album>()
    val photos = items.filter { it.type == MediaType.PHOTO }
    val videos = items.filter { it.type == MediaType.VIDEO }
    if (photos.isNotEmpty()) albums += Album(GalleryViewModel.ALBUM_PHOTOS, "Photos", "${photos.size} photos", photos)
    if (videos.isNotEmpty()) albums += Album(GalleryViewModel.ALBUM_VIDEOS, "Videos", "${videos.size} videos", videos)
    items.groupBy { monthKey(it.createdAtEpochMs) }.toSortedMap(compareByDescending { it }).forEach { (month, monthItems) ->
        albums += Album(GalleryViewModel.ALBUM_MONTH_PREFIX + month, monthLabel(monthItems.first().createdAtEpochMs), "${monthItems.size} ${if (monthItems.size == 1) "item" else "items"}", monthItems)
    }
    return albums
}

@Composable
private fun MediaRows(items: List<MediaItem>, state: GalleryUiState.Ready, viewModel: GalleryViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.chunked(3).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                rowItems.forEach { item -> MediaTile(item, state, viewModel, Modifier.weight(1f)) }
                repeat(3 - rowItems.size) { Box(Modifier.weight(1f).aspectRatio(1f)) }
            }
        }
    }
}

@Composable
private fun MediaTile(item: MediaItem, state: GalleryUiState.Ready, viewModel: GalleryViewModel, modifier: Modifier = Modifier) {
    LaunchedEffect(item.mediaId) { viewModel.loadThumbnail(item.mediaId) }
    val bitmap = state.thumbnails[item.mediaId]?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    Box(modifier = modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant).clickable { viewModel.openMedia(item) }, contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else if (item.mediaId in state.loadingThumbnails) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        else Text(if (item.type == MediaType.VIDEO) "VIDEO" else "PHOTO", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
@OptIn(UnstableApi::class)
private fun MediaDetailDialog(state: GalleryUiState.Ready, item: MediaItem, viewModel: GalleryViewModel) {
    val bytes = state.selectedContent
    val bitmap = bytes?.takeIf { item.type == MediaType.PHOTO }?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    val thumbnailBytes = state.thumbnails[item.mediaId]
    val thumbnail = thumbnailBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    AlertDialog(
        onDismissRequest = viewModel::closeMedia,
        confirmButton = { TextButton(onClick = viewModel::closeMedia) { Text("Close") } },
        title = { Text(if (item.type == MediaType.VIDEO) "Video details" else "Photo details") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    state.loadingContent -> CircularProgressIndicator()
                    item.type == MediaType.VIDEO && bytes != null -> VideoPlayer(bytes, item.mediaId)
                    bitmap != null -> Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Fit)
                    item.type == MediaType.VIDEO && thumbnail != null -> {
                        Image(thumbnail, contentDescription = "Video preview", modifier = Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Fit)
                        Text("Video preview", modifier = Modifier.padding(top = 8.dp))
                    }
                    state.selectedError != null -> Text(state.selectedError, color = MaterialTheme.colorScheme.error)
                    else -> Text("Media preview is unavailable.")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                DetailRow("Type", if (item.type == MediaType.VIDEO) "Video" else "Photo")
                DetailRow("Format", item.mimeType)
                DetailRow("Captured", detailDateLabel(item.createdAtEpochMs))
                item.width?.let { width -> item.height?.let { height -> DetailRow("Dimensions", "$width × $height") } }
                item.durationMs?.let { DetailRow("Duration", formatDuration(it)) }
                DetailRow("Media ID", item.mediaId)
            }
        },
    )
}

@Composable
@OptIn(UnstableApi::class)
private fun VideoPlayer(bytes: ByteArray, mediaId: String) {
    val context = LocalContext.current
    val player = remember(bytes, mediaId) {
        ExoPlayer.Builder(context).build().apply {
            val dataSourceFactory = DataSource.Factory { ByteArrayDataSource(bytes) }
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(PlayerMediaItem.fromUri(Uri.parse("samvira://media/$mediaId")))
            setMediaSource(mediaSource)
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = true
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        },
        update = { it.player = player },
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp))
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private fun detailDateLabel(epochMs: Long): String = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(epochMs))

@Composable
private fun CenteredMessage(padding: PaddingValues = PaddingValues(0.dp), content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { content() }
}

private fun GalleryMode.label(): String = when (this) {
    GalleryMode.GALLERY -> "Gallery"
    GalleryMode.TIMELINE -> "Timeline"
    GalleryMode.ALBUMS -> "Albums"
}

private fun dayKey(epochMs: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(epochMs))

private fun dayLabel(epochMs: Long): String {
    val date = Calendar.getInstance().apply { timeInMillis = epochMs }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        isSameDay(date, today) -> "Today"
        isSameDay(date, yesterday) -> "Yesterday"
        else -> SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(date.time)
    }
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean = a.get(Calendar.ERA) == b.get(Calendar.ERA) && a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private fun monthKey(epochMs: Long): String = SimpleDateFormat("yyyy-MM", Locale.ROOT).format(Date(epochMs))

private fun monthLabel(epochMs: Long): String = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(epochMs))
