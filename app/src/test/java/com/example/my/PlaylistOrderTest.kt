package com.example.my.data.music

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistOrderTest {

    @Test
    fun middleTrackGoesToPreviousInList() {
        assertEquals(2, previousTrackIndex(currentIndex = 3, size = 8))
    }

    @Test
    fun firstTrackWrapsToLast() {
        assertEquals(7, previousTrackIndex(currentIndex = 0, size = 8))
    }

    @Test
    fun currentMissingStartsAtFirst() {
        assertEquals(0, previousTrackIndex(currentIndex = -1, size = 8))
    }

    @Test
    fun emptyPlaylistHasNoPrevious() {
        assertEquals(-1, previousTrackIndex(currentIndex = 2, size = 0))
    }
}
