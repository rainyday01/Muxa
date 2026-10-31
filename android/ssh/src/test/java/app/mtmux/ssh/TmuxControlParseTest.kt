package app.mtmux.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmuxControlParseTest {
    @Test
    fun parseStructured() {
        val text = "claude-main|2|1\ncodex|1|0\n"
        val list = TmuxControl.parseListSessions("h1", text)
        assertEquals(2, list.size)
        assertEquals("claude-main", list[0].name)
        assertEquals(2, list[0].windows)
        assertTrue(list[0].attached)
        assertNull(list[0].socketPath)
    }

    @Test
    fun parseWithSocketPath() {
        val text = "claude-main|2|1|/tmp/tmux-1000/default\ncodex|1|0|/run/user/1000/tmux-1000/default\n"
        val list = TmuxControl.parseListSessions("h1", text)
        assertEquals(2, list.size)
        assertEquals("/tmp/tmux-1000/default", list[0].socketPath)
        assertEquals("/run/user/1000/tmux-1000/default", list[1].socketPath)
    }

    @Test
    fun parseDedupesByNamePreferAttached() {
        val text =
            "work|1|0|/tmp/tmux-0/default\nwork|2|1|/tmp/a/tmux-0/default\n"
        val list = TmuxControl.parseListSessions("h1", text)
        assertEquals(1, list.size)
        assertEquals(2, list[0].windows)
        assertTrue(list[0].attached)
        assertEquals("/tmp/a/tmux-0/default", list[0].socketPath)
    }

    @Test
    fun parseLegacy() {
        val text = "work: 3 windows (created ...)\n"
        val list = TmuxControl.parseListSessions("h1", text)
        assertEquals(1, list.size)
        assertEquals("work", list[0].name)
    }

    @Test
    fun attachOrCreateCmdIncludesSocketScan() {
        val cmd = TmuxControl.attachOrCreateCmd("claude", "/tmp/tmux-0/default")
        assertTrue(cmd.contains("attach-session"))
        assertTrue(cmd.contains("_mtmux_find_session"))
        assertTrue(cmd.contains("/tmp/tmux-0/default"))
        assertTrue(cmd.contains("_mtmux_canonical_base"))
        assertTrue(cmd.contains("unset TMPDIR"))
        assertTrue(TmuxControl.listSessionsCmd().contains("/var/folders"))
        assertTrue(TmuxControl.DESKTOP_TAKEOVER_SNIPPET.contains("\${TMUX_TMPDIR:-/tmp}") ||
            TmuxControl.DESKTOP_TAKEOVER_SNIPPET.contains("TMUX_TMPDIR:-/tmp"))
        assertTrue(TmuxControl.isDarwin("macOS", null))
        assertTrue(TmuxControl.isDarwin(null, "Darwin"))
        org.junit.Assert.assertFalse(TmuxControl.isDarwin("Ubuntu", "Linux"))
    }

    @Test
    fun parseHostResourcesKv() {
        val raw = """
            TMUX=/usr/bin/tmux
            MEM_AVAIL_KB=1258291
            MEM_TOTAL_KB=16384000
            DISK_AVAIL_KB=50331648
            DISK_TOTAL_KB=104857600
        """.trimIndent()
        val res = app.mtmux.core.model.HostResourceText.parseKv(raw)
        assertEquals(1258291L, res.memAvailKb)
        assertEquals(50331648L, res.diskAvailKb)
        assertEquals("1.2G", app.mtmux.core.model.HostResourceText.formatKb(1258291L))
        assertEquals("48G", app.mtmux.core.model.HostResourceText.formatKb(50331648L))
        assertEquals("512M", app.mtmux.core.model.HostResourceText.formatKb(512L * 1024L))
    }

    @Test
    fun resourceLowThresholds() {
        val t = app.mtmux.core.model.HostResourceText
        assertTrue(t.memLow(128L * 1024L, 16L * 1024L * 1024L))
        assertTrue(t.diskLow(1L * 1024L * 1024L, 100L * 1024L * 1024L))
        org.junit.Assert.assertFalse(t.memLow(2L * 1024L * 1024L, 16L * 1024L * 1024L))
    }
}
