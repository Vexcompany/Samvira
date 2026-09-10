package com.vexcompany.samvira.ui.gallery

import kotlin.test.Test
import kotlin.test.assertEquals

class GalleryViewModelStateTest {
    @Test
    fun modeDefaultsToGallery() {
        assertEquals(GalleryMode.GALLERY, GalleryMode.entries.first())
    }

    @Test
    fun galleryModesRemainStable() {
        assertEquals(listOf(GalleryMode.GALLERY, GalleryMode.TIMELINE, GalleryMode.ALBUMS), GalleryMode.entries)
    }
}
