package app.mtmux.core.data.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import kotlin.random.Random

class LogHubClientTest {
    @Test
    fun cooldownCountsDownAndIgnoresClockJumps() {
        val t = 1_000_000L
        assertEquals(60, LogHubClient.cooldownSeconds(t, t))
        assertEquals(1, LogHubClient.cooldownSeconds(t, t + 59_500))
        assertEquals(0, LogHubClient.cooldownSeconds(t, t + 60_000))
        assertEquals(0, LogHubClient.cooldownSeconds(0L, t))
        assertEquals(0, LogHubClient.cooldownSeconds(t + 300_000, t))
    }

    @Test
    fun onlyTransientFailuresRetry() {
        assertTrue(LogHubClient.isRetryable(LogHubHttpException(502, "")))
        assertTrue(LogHubClient.isRetryable(LogHubHttpException(503, "disk guard")))
        assertTrue(LogHubClient.isRetryable(LogHubHttpException(500, "storage put failed")))
        assertTrue(LogHubClient.isRetryable(SocketTimeoutException()))
        assertFalse(LogHubClient.isRetryable(LogHubHttpException(429, "")))
        assertFalse(LogHubClient.isRetryable(LogHubHttpException(400, "invalid multipart form")))
        assertFalse(LogHubClient.isRetryable(java.io.IOException("reset")))
    }

    @Test
    fun oversizedPackageKeepsNewestLogAndFits() {
        // Random text barely compresses, so the zip really is over the limit at first.
        val rnd = Random(7)
        val log = buildString { repeat(200_000) { append('a' + rnd.nextInt(26)) } } + "NEWEST"
        val limit = 50_000
        val zip = LogHubClient.buildZipWithinLimit(log, "d", "1", "n", maxBytes = limit)
        assertTrue(zip.size <= limit)
        val text = java.util.zip.ZipInputStream(zip.inputStream()).use { z ->
            generateSequence { z.nextEntry }.first { it.name == "app.log" }
            z.readBytes().decodeToString()
        }
        assertTrue(text.endsWith("NEWEST"))
    }
}
