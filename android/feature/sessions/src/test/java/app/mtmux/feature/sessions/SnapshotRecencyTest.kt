package app.mtmux.feature.sessions

import app.mtmux.core.model.SnapshotText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnapshotRecencyTest {
    @Test
    fun backgroundCaptureKeepsPreviousStamp() {
        assertEquals(1_000L, SnapshotText.stampForBackgroundCapture(1_000L, 9_000L))
    }

    @Test
    fun backgroundCaptureFirstFillUsesNow() {
        assertEquals(9_000L, SnapshotText.stampForBackgroundCapture(null, 9_000L))
        assertEquals(9_000L, SnapshotText.stampForBackgroundCapture(0L, 9_000L))
    }

    @Test
    fun recencyPrefersLaterOfOpenAndSnapshot() {
        assertEquals(50L, SnapshotText.recencyEpochMs(50L, 20L))
        assertEquals(80L, SnapshotText.recencyEpochMs(50L, 80L))
        assertEquals(50L, SnapshotText.recencyEpochMs(50L, null))
        assertEquals(20L, SnapshotText.recencyEpochMs(null, 20L))
        assertNull(SnapshotText.recencyEpochMs(null, null))
        assertNull(SnapshotText.recencyEpochMs(0L, 0L))
    }

    @Test
    fun backgroundRecaptureDoesNotWinChipOverLastOpen() {
        val lastOpened = 1_000L
        val afterBackground = SnapshotText.stampForBackgroundCapture(lastOpened, 9_000L)
        assertEquals(
            lastOpened,
            SnapshotText.recencyEpochMs(lastOpened, afterBackground),
        )
    }

    @Test
    fun snapshotAgeJustNowUnderOneMinute() {
        val now = 1_700_000_000_000L
        assertEquals(
            "now",
            snapshotAgeLabel("none", "now", "min", "hr", "day", now - 30_000L, now),
        )
        assertEquals(
            "min",
            snapshotAgeLabel("none", "now", "min", "hr", "day", now - 5 * 60_000L, now),
        )
        assertEquals(
            "hr",
            snapshotAgeLabel("none", "now", "min", "hr", "day", now - 3 * 3_600_000L, now),
        )
        assertEquals(
            "day",
            snapshotAgeLabel("none", "now", "min", "hr", "day", now - 2 * 86_400_000L, now),
        )
        assertEquals(
            "none",
            snapshotAgeLabel("none", "now", "min", "hr", "day", null, now),
        )
    }
}
