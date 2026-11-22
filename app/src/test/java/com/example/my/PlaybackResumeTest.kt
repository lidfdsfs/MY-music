package com.example.my

import com.example.my.data.music.clampResumePosition
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackResumeTest {

    @Test
    fun zeroSavedPositionStartsFromZero() {
        assertEquals(0L, clampResumePosition(0L, 180_000L))
    }

    @Test
    fun normalSavedPositionIsKept() {
        assertEquals(62_000L, clampResumePosition(62_000L, 180_000L))
    }

    @Test
    fun unknownDurationKeepsSavedPosition() {
        assertEquals(62_000L, clampResumePosition(62_000L, 0L))
    }

    @Test
    fun positionNearEndIsClampedBeforeCompletion() {
        assertEquals(179_000L, clampResumePosition(180_000L, 180_000L))
    }

    @Test
    fun veryShortTrackNeverGoesNegative() {
        assertEquals(0L, clampResumePosition(3_000L, 500L))
    }
}
