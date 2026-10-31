package app.mtmux.core.model

/**
 * Terminal **color scheme** (ANSI 0–15 + fg/bg/cursor).
 * Independent of app chrome [AppearanceMode] / [ThemeRole].
 *
 * Colors are ARGB ints (0xAARRGGBB), matching Termux [com.termux.terminal.TerminalColorScheme].
 */
data class TerminalScheme(
    val id: String,
    val displayName: String,
    /** Default text */
    val foreground: Int,
    /** Default background */
    val background: Int,
    val cursor: Int,
    /**
     * ANSI colors 0–15 (normal 0–7, bright 8–15).
     * Length must be 16.
     */
    val ansi: IntArray,
) {
    init {
        require(ansi.size == 16) { "ansi must have 16 colors, got ${ansi.size}" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TerminalScheme) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * Terminal monospace typeface catalog entry.
 * [assetPath] is under app `assets/` (e.g. `fonts/JetBrainsMono-Regular.ttf`);
 * null means system [android.graphics.Typeface.MONOSPACE].
 */
data class TerminalTypeface(
    val id: String,
    val displayName: String,
    val assetPath: String?,
    val licenseNote: String = "OFL / free",
)
