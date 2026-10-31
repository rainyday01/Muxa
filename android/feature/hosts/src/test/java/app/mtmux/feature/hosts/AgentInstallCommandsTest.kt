package app.mtmux.feature.hosts

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentInstallCommandsTest {
    @Test
    fun officialLinuxMacInstallers() {
        val byId = AgentInstallCommands.all.associate { it.id to it.command }
        assertEquals(
            listOf("codex", "claude", "agy", "grok"),
            AgentInstallCommands.all.map { it.id },
        )
        assertEquals("curl -fsSL https://chatgpt.com/codex/install.sh | sh", byId["codex"])
        assertEquals("curl -fsSL https://claude.ai/install.sh | bash", byId["claude"])
        assertEquals("curl -fsSL https://antigravity.google/cli/install.sh | bash", byId["agy"])
        assertEquals("curl -fsSL https://x.ai/cli/install.sh | bash", byId["grok"])
    }
}
