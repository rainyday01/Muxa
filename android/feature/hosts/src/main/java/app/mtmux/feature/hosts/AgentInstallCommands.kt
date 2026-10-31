package app.mtmux.feature.hosts

/**
 * Official Linux/macOS installers for the agents Muxa can start.
 * Guidance only: the app copies the line and never runs it.
 */
object AgentInstallCommands {
    data class Tool(val id: String, val command: String)

    val all: List<Tool> = listOf(
        Tool("codex", "curl -fsSL https://chatgpt.com/codex/install.sh | sh"),
        Tool("claude", "curl -fsSL https://claude.ai/install.sh | bash"),
        Tool("agy", "curl -fsSL https://antigravity.google/cli/install.sh | bash"),
        Tool("grok", "curl -fsSL https://x.ai/cli/install.sh | bash"),
    )
}
