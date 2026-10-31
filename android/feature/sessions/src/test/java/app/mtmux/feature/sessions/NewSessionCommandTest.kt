package app.mtmux.feature.sessions

import org.junit.Assert.assertEquals
import org.junit.Test

class NewSessionCommandTest {
    @Test
    fun composeAgentOnly() {
        assertEquals(
            "claude",
            NewSessionCommand.compose("", NewSessionCommand.DirMode.CreateAndEnter, "claude"),
        )
    }

    @Test
    fun composeMkdirCdClaude() {
        assertEquals(
            "mkdir -p ~/projects/foo && cd ~/projects/foo && claude",
            NewSessionCommand.compose(
                "~/projects/foo",
                NewSessionCommand.DirMode.CreateAndEnter,
                "claude",
            ),
        )
    }

    @Test
    fun composeCdExistingGrok() {
        assertEquals(
            "cd /opt/work && grok",
            NewSessionCommand.compose(
                "/opt/work",
                NewSessionCommand.DirMode.EnterExisting,
                "grok",
            ),
        )
    }

    @Test
    fun quotesPathsWithSpaces() {
        assertEquals(
            "mkdir -p 'my app' && cd 'my app' && codex",
            NewSessionCommand.compose(
                "my app",
                NewSessionCommand.DirMode.CreateAndEnter,
                "codex",
            ),
        )
    }

    @Test
    fun suggestedNameFromFolder() {
        assertEquals(
            "foo",
            NewSessionCommand.suggestedName("~/projects/foo", "claude", "work"),
        )
    }

    @Test
    fun suggestedNameKeepsCustom() {
        assertEquals(
            "mine",
            NewSessionCommand.suggestedName("~/projects/foo", "claude", "mine", locked = true),
        )
    }

    @Test
    fun suggestedNameFollowsDirectoryWhileTyping() {
        var name = "work"
        val dir = "claude-one"
        for (n in 1..dir.length) {
            name = NewSessionCommand.suggestedName(dir.take(n), "", name)
        }
        assertEquals("claude-one", name)
    }

    @Test
    fun suggestedNameReplacesDefaultAgentWhenFolderGrows() {
        var name = "claude"
        val dir = "/home/alice/claude-one"
        for (n in 1..dir.length) {
            name = NewSessionCommand.suggestedName(dir.take(n), "claude", name)
        }
        assertEquals("claude-one", name)
    }

    @Test
    fun composeAgySkipPermissions() {
        assertEquals(
            "mkdir -p ~/x && cd ~/x && agy --dangerously-skip-permissions",
            NewSessionCommand.compose(
                "~/x",
                NewSessionCommand.DirMode.CreateAndEnter,
                "agy",
                skipDanger = true,
            ),
        )
    }

    @Test
    fun composeCodexYolo() {
        assertEquals(
            "cd ~/x && codex --yolo",
            NewSessionCommand.compose(
                "~/x",
                NewSessionCommand.DirMode.EnterExisting,
                "codex",
                yolo = true,
            ),
        )
    }

    @Test
    fun skipFlagIgnoredForGrok() {
        assertEquals(
            "grok",
            NewSessionCommand.compose("", NewSessionCommand.DirMode.CreateAndEnter, "grok", skipDanger = true, yolo = true),
        )
    }

    @Test
    fun skipFlagForAgents() {
        assertEquals("--dangerously-skip-permissions", NewSessionCommand.skipFlagFor("claude"))
        assertEquals("--dangerously-skip-permissions", NewSessionCommand.skipFlagFor("agy"))
        assertEquals("--yolo", NewSessionCommand.skipFlagFor("codex"))
        assertEquals(null, NewSessionCommand.skipFlagFor("grok"))
    }
}
