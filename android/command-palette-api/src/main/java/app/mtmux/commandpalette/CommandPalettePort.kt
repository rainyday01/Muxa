package app.mtmux.commandpalette

/**
 * Reserved command-palette API. v0.3.1: NoOp only, no ⌘K UI.
 * Session management remains on the session list.
 */
interface CommandPalettePort {
    fun isFeatureEnabled(): Boolean = false

    fun actions(): List<PaletteAction> = emptyList()
}

data class PaletteAction(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val keywords: List<String> = emptyList(),
)

class NoOpCommandPalette : CommandPalettePort {
    override fun isFeatureEnabled(): Boolean = false

    override fun actions(): List<PaletteAction> = emptyList()
}
