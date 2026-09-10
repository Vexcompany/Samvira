package com.vexcompany.samvira.ui.gallery

import com.vexcompany.samvira.domain.media.MediaItem
import com.vexcompany.samvira.domain.media.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSearchTest {
    private val items = listOf(
        MediaItem("photo-2026-01", "org", MediaType.PHOTO, "image/jpeg", 1920, 1080, null, 1767225600000L, null),
        MediaItem("video-2026-02", "org", MediaType.VIDEO, "video/mp4", 1280, 720, 125000L, 1769904000000L, null),
    )

    @Test
    fun emptyQueryReturnsAllItems() {
        assertEquals(items, searchMedia(items, "   "))
    }

    @Test
    fun searchesAcrossTypeAndMimeMetadata() {
        assertEquals(listOf(items[0]), searchMedia(items, "photo jpeg"))
        assertEquals(listOf(items[1]), searchMedia(items, "video mp4"))
    }

    @Test
    fun searchesAcrossDimensionsAndMediaId() {
        assertEquals(listOf(items[0]), searchMedia(items, "1920x1080"))
        assertEquals(listOf(items[1]), searchMedia(items, "video-2026-02"))
    }

    @Test
    fun allTokensMustMatchTheSameMediaItem() {
        assertTrue(searchMedia(items, "photo mp4").isEmpty())
    }
}
