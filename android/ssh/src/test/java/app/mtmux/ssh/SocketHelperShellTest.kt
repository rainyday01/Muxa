package app.mtmux.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs [TmuxControl.SOCKET_HELPER] through real shells, the way SSH exec does
 * with the user's login shell. Skipped where a shell or python3 is missing.
 */
class SocketHelperShellTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun has(bin: String): Boolean =
        runCatching {
            ProcessBuilder("sh", "-c", "command -v $bin >/dev/null").start().waitFor() == 0
        }.getOrDefault(false)

    private fun uid(): String =
        ProcessBuilder("id", "-u").start().inputStream.bufferedReader().readText().trim()

    /** Bind a unix socket file at [path] (the helper only checks `-S`). */
    private fun makeSocket(path: File) {
        val p = ProcessBuilder(
            "python3", "-c",
            "import socket,sys; s=socket.socket(socket.AF_UNIX); s.bind(sys.argv[1])",
            path.absolutePath,
        ).start()
        assertEquals(0, p.waitFor())
    }

    private fun run(shell: String, script: String, env: Map<String, String>): String {
        val pb = ProcessBuilder(shell, "-c", script)
        pb.environment().remove("TMUX")
        pb.environment().remove("TMUX_TMPDIR")
        pb.environment().putAll(env)
        val p = pb.start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor(10, TimeUnit.SECONDS)
        return out
    }

    @Test
    fun emptySocketDirDoesNotHideLaterServers() {
        assumeTrue(has("python3"))
        val id = uid()
        // TMUX_TMPDIR is scanned first: an empty tmux-$uid there is what a
        // server leaves behind after it exits.
        val empty = tmp.newFolder("empty")
        File(empty, "tmux-$id").mkdirs()
        val live = tmp.newFolder("live")
        val dir = File(live, "tmux-$id").apply { mkdirs() }
        val sock = File(dir, "default")
        makeSocket(sock)

        val script = TmuxControl.SOCKET_HELPER + "\n_mtmux_socks\n"
        val env = mapOf("TMUX_TMPDIR" to empty.absolutePath, "TMPDIR" to live.absolutePath)
        for (shell in listOf("zsh", "bash", "dash", "sh")) {
            if (!has(shell)) continue
            val out = run(shell, script, env)
            assertTrue("$shell lost the socket: <$out>", out.lines().contains(sock.absolutePath))
        }
    }
}
