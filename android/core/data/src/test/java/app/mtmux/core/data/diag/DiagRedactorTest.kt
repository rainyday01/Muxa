package app.mtmux.core.data.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagRedactorTest {

    private val log = """
        10-30 23:28:12.098 I/ssh: connect start alias=prod host=203.0.113.7:22 user=alice transport=SSH rawHost=203.0.113.7
        10-30 23:28:13.810 I/pty: attachTmux hostId=caffac72-769d-48f3-88fd-5ef7589872d8 session=secret-proj socket=/run/user/1000/tmux-1000/default
        10-30 23:29:28.014 I/tmux: listSessions ok alias=prod count=2 names=[secret-proj, other]
        10-30 23:29:28.684 I/tmux: capture ok caffac72-769d-48f3-88fd-5ef7589872d8/other bytes=26 keepAge=false
        10-30 23:31:55.517 I/attach: snapshot saved on leave session=secret-proj local=759 preview=git push alice@example.com
        10-30 23:31:56.000 I/ssh: key /home/alice/.ssh/id_ed25519 via 2001:db8:0:0:0:0:2:1
        10-30 23:29:39.808 I/pty: termux size WINCH 87x36 (was 87x66)
    """.trimIndent()

    @Test
    fun masksIdentifiersButKeepsTheEventStream() {
        val out = DiagRedactor.redact(log)
        listOf("203.0.113.7", "alice", "prod", "secret-proj", "other", "example.com", "2001:db8").forEach {
            assertFalse("leaked $it:\n$out", out.contains(it))
        }
        assertTrue(out.contains("alias=h1 host=<ip>:22 user=<user>"))
        assertTrue(out.contains("session=s1"))
        assertTrue(out.contains("names=[s1, s2]"))
        assertTrue(out.contains("caffac72-769d-48f3-88fd-5ef7589872d8/s2"))
        assertTrue(out.contains("preview=<redacted>"))
        assertTrue(out.contains("/home/<user>/.ssh"))
        // Timestamps and sizes survive.
        assertTrue(out.contains("10-30 23:29:39.808 I/pty: termux size WINCH 87x36 (was 87x66)"))
        assertEquals(log.lines().size, out.lines().size)
    }
}
