package app.mtmux.ssh

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * A probe that could not run must fail, not report "tmux missing": the session list
 * used to blank itself (and mark the host tmux-less) when a silent link failed the probe.
 */
class ProbeEnvironmentFailureTest {

    private class FakeEngine(private val reply: Result<String>) : SshEngine {
        override suspend fun connect(params: SshConnectParams): Result<Unit> = Result.success(Unit)
        override suspend fun disconnect(hostId: String) = Unit
        override suspend fun exec(hostId: String, command: String): Result<String> = reply
    }

    /** Connected manager: a non-sshj engine is reached through reconnect with saved params. */
    private suspend fun control(reply: Result<String>): TmuxControl {
        val cm = ConnectionManager(FakeEngine(reply))
        cm.connect(SshConnectParams("h", "example.invalid", 22, "u"))
        return TmuxControl(cm)
    }

    @Test
    fun execFailureThrowsInsteadOfTmuxMissing() = runBlocking {
        try {
            control(Result.failure(IOException("SSH exec timed out"))).probeEnvironment("h")
            fail("expected IOException")
        } catch (_: IOException) {
        }
    }

    @Test
    fun foreignOutputThrows() = runBlocking {
        try {
            control(Result.success("bash: warning: something\n")).probeEnvironment("h")
            fail("expected IOException")
        } catch (_: IOException) {
        }
    }

    @Test
    fun realProbeStillReportsTmux() = runBlocking {
        assertTrue(control(Result.success("TMUX=/usr/bin/tmux\nUNAME=Linux\nOS=Ubuntu\nPM=apt\n")).probeEnvironment("h").tmux)
        assertFalse(control(Result.success("TMUX=\nUNAME=Linux\nOS=Ubuntu\nPM=apt\n")).probeEnvironment("h").tmux)
    }
}
