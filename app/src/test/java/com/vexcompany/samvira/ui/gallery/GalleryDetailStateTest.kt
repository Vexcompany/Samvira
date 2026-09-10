package com.vexcompany.samvira.ui.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GalleryDetailStateTest {
    @Test
    fun readyStateStartsWithoutDetailError() {
        val state = GalleryUiState.Ready(items = emptyList())

        assertNull(state.selected)
        assertNull(state.selectedGrant)
        assertNull(state.selectedContent)
        assertNull(state.selectedError)
        assertEquals(false, state.loadingContent)
    }

    @Test
    fun detailErrorIsIndependentFromMediaSelection() {
        val state = GalleryUiState.Ready(
            items = emptyList(),
            selectedError = "Media access is unavailable.",
        )

        assertNull(state.selected)
        assertEquals("Media access is unavailable.", state.selectedError)
    }
}
