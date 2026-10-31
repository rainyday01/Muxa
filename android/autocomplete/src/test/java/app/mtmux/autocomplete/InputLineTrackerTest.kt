package app.mtmux.autocomplete

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputLineTrackerTest {
    private fun InputLineTracker.type(s: String) = feed(s.toByteArray())

    @Test
    fun typingAndBackspace() {
        val t = InputLineTracker()
        t.type("git sx")
        t.feed(byteArrayOf(0x7F))
        assertEquals("git s", t.line)
    }

    @Test
    fun enterSubmitsAndClears() {
        val t = InputLineTracker()
        t.type("ls -la")
        assertEquals("ls -la", t.type("\r"))
        assertEquals("", t.line)
        assertNull(t.type("\r"))
    }

    @Test
    fun cursorMovementLosesTrackUntilSubmit() {
        val t = InputLineTracker()
        t.type("echo hi")
        t.type("\u001B[D")
        t.type("x")
        assertEquals("", t.line)
        assertNull(t.type("\r"))
        t.type("pwd")
        assertEquals("pwd", t.line)
    }

    @Test
    fun ctrlUClearsLostState() {
        val t = InputLineTracker()
        t.type("abc\u0001")
        assertEquals("", t.line)
        t.type("\u0015cd ")
        assertEquals("cd ", t.line)
    }

    @Test
    fun bracketedPasteAcrossWrites() {
        val t = InputLineTracker()
        t.type("cat ")
        t.type("\u001B[200~")
        t.type("/etc/hosts")
        t.type("\u001B[201~")
        assertEquals("cat /etc/hosts", t.line)
    }

    @Test
    fun utf8SplitAcrossWrites() {
        val t = InputLineTracker()
        val bytes = "echo 你好".toByteArray()
        t.feed(bytes.copyOfRange(0, bytes.size - 1))
        t.feed(bytes.copyOfRange(bytes.size - 1, bytes.size))
        assertEquals("echo 你好", t.line)
        t.feed(byteArrayOf(0x7F))
        assertEquals("echo 你", t.line)
    }
}
