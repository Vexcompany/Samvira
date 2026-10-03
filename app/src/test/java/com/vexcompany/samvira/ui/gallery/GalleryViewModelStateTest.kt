package com.vexcompany.samvira.ui.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryViewModelStateTest {
    @Test
    fun modeDefaultsToGallery() {
        assertEquals(GalleryMode.GALLERY, GalleryMode.entries.first())
    }

    @Test
    fun galleryModesRemainStable() {
        assertEquals(
            listOf(GalleryMode.GALLERY, GalleryMode.TIMELINE, GalleryMode.ALBUMS),
            GalleryMode.entries,
        )
    }
}
