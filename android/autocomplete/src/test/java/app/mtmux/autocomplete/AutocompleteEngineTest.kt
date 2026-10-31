package app.mtmux.autocomplete

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutocompleteEngineTest {
    @Test
    fun looksLikePath() {
        assertTrue(AutocompleteEngine.looksLikePathToken("/etc"))
        assertTrue(AutocompleteEngine.looksLikePathToken("./bin"))
        assertTrue(AutocompleteEngine.looksLikePathToken("src/", "ls src/"))
        assertTrue(AutocompleteEngine.looksLikePathToken("", "cd "))
        assertFalse(AutocompleteEngine.looksLikePathToken("status", "git status"))
    }

    @Test
    fun remoteCompgenQuoted() {
        val cmd = AutocompleteEngine.remoteCompgenCommand("cd /et")
        assertNotNull(cmd)
        assertTrue(cmd!!.contains("compgen -d"))
        assertTrue(cmd.contains("/et"))
        assertNull(AutocompleteEngine.remoteCompgenCommand("git st"))
    }

    @Test
    fun pathInsertionDelta() {
        assertEquals("ice", AutocompleteEngine.pathCompletionInsertion("/home/al", "/home/alice"))
        assertEquals("/tmp", AutocompleteEngine.pathCompletionInsertion("", "/tmp"))
    }

    @Test
    fun parseCompgen() {
        val list = AutocompleteEngine.parseCompgenOutput("/etc\n/etc/hosts\n\n/etc\n")
        assertEquals(listOf("/etc", "/etc/hosts"), list)
    }

    @Test
    fun tuiModeAltScreen() {
        assertTrue(AutocompleteEngine.isTuiMode(true, "", "x"))
        assertFalse(AutocompleteEngine.isTuiMode(false, "user@host:~$ ", "ls"))
    }

    /** AND-131 / AND-085: snippet body variables {{host}} {{user}} {{session}} */
    @Test
    fun snippetApplyVars() {
        val out = AutocompleteEngine.applyVars(
            "ssh {{user}}@{{host}} -t 'tmux a -t {{session}}'",
            mapOf("user" to "ubuntu", "host" to "49.1.2.3", "session" to "cliproxy"),
        )
        assertEquals("ssh ubuntu@49.1.2.3 -t 'tmux a -t cliproxy'", out)
        assertEquals("plain", AutocompleteEngine.applyVars("plain", emptyMap()))
    }
}
