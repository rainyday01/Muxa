package app.mtmux.core.ui.terminal

import android.content.Context
import android.graphics.Typeface
import app.mtmux.core.model.TerminalScheme
import app.mtmux.core.model.TerminalTypeface
import java.util.concurrent.ConcurrentHashMap

/** ARGB from #RRGGBB or #AARRGGBB. */
internal fun hex(s: String): Int {
    val t = s.removePrefix("#")
    val v = t.toLong(16)
    return if (t.length <= 6) (0xFF000000 or v).toInt() else v.toInt()
}

/**
 * Top typefaces for terminals — mix of Termius defaults + community favorites.
 * All assets are free/OFL (or system mono).
 *
 * Research notes (Termius list + popularity):
 * 1. Source Code Pro — Termius default
 * 2. JetBrains Mono — most popular coding mono 2020s
 * 3. Fira Code — ligatures / VS Code crowd
 * 4. Cascadia Code/Mono — Windows Terminal default
 * 5. DejaVu Sans Mono — classic Linux terminal
 * 6. Ubuntu Mono — Ubuntu / Termius list
 * 7. Hack — derivative of DejaVu, powerline-friendly
 * 8. IBM Plex Mono — IBM design, readable
 * 9. Roboto Mono — Android / Material ecosystem
 * 10. Inconsolata — long-standing monospaced classic (Meslo-class alternative)
 */
object BuiltinTypefaces {
    val all: List<TerminalTypeface> = listOf(
        // Monospace only — system UI CJK faces (YaHei etc.) already act as fallback
        // for 中文 in mono fonts; a dedicated 微软雅黑 entry caused cell-metric bugs.
        TerminalTypeface("jetbrains_mono", "JetBrains Mono", "fonts/JetBrainsMono-Regular.ttf", "OFL"),
        TerminalTypeface("source_code_pro", "Source Code Pro Medium", "fonts/SourceCodePro-Medium.ttf", "OFL (Adobe)"),
        TerminalTypeface("fira_code", "Fira Code", "fonts/FiraCode-Regular.ttf", "OFL"),
        TerminalTypeface("cascadia_mono", "Cascadia Mono", "fonts/CascadiaMono-Regular.ttf", "OFL (Microsoft)"),
        TerminalTypeface("dejavu_sans_mono", "DejaVu Sans Mono", "fonts/DejaVuSansMono.ttf", "Bitstream / free"),
        TerminalTypeface("ubuntu_mono", "Ubuntu Mono", "fonts/UbuntuMono-Regular.ttf", "UFL"),
        TerminalTypeface("hack", "Hack", "fonts/Hack-Regular.ttf", "MIT"),
        TerminalTypeface("ibm_plex_mono", "IBM Plex Mono", "fonts/IBMPlexMono-Regular.ttf", "OFL"),
        TerminalTypeface("roboto_mono", "Roboto Mono", "fonts/RobotoMono-Regular.ttf", "Apache 2.0"),
        TerminalTypeface("inconsolata", "Inconsolata", "fonts/Inconsolata-Regular.ttf", "OFL"),
    )

    const val DEFAULT_ID = "jetbrains_mono"

    /** Map removed / unknown ids (e.g. legacy microsoft_yahei) to a catalog entry. */
    fun byId(id: String): TerminalTypeface {
        if (id == "microsoft_yahei") return all.first { it.id == DEFAULT_ID }
        return all.firstOrNull { it.id == id } ?: all.first { it.id == DEFAULT_ID }
    }
}

/**
 * Top 10 terminal schemes: Termius catalog staples + most-starred community palettes
 * (Dracula / Nord / Solarized / Gruvbox / Kanagawa / Everforest / Night Owl / Flexoki).
 *
 * Palettes: ANSI 0–7 normal, 8–15 bright; plus fg/bg/cursor.
 * Hex from official project docs / iTerm2-Color-Schemes ports.
 */
object BuiltinSchemes {
    val all: List<TerminalScheme> = listOf(
        // 0) Pulse Mono — UI proposal A black/gray (default for Glass Sequoia B chrome)
        scheme(
            id = "pulse_mono",
            name = "Pulse Mono",
            fg = "#E4E4E7",
            bg = "#050506",
            cursor = "#FAFAFA",
            ansi = listOf(
                "#09090B", "#F87171", "#A1A1AA", "#A1A1AA",
                "#A1A1AA", "#A1A1AA", "#A1A1AA", "#E4E4E7",
                "#52525B", "#FCA5A5", "#D4D4D8", "#D4D4D8",
                "#D4D4D8", "#D4D4D8", "#D4D4D8", "#FAFAFA",
            ),
        ),
        // 1) Termius Dark — green phosphor (kept for settings picker)
        scheme(
            id = "termius_dark",
            name = "Termius Dark",
            fg = "#7DCF7A",
            bg = "#0B1410",
            cursor = "#7DCF7A",
            ansi = listOf(
                "#0B1410", "#E06C75", "#7DCF7A", "#E5C07B",
                "#61AFEF", "#C678DD", "#56B6C2", "#A8B5A0",
                "#4A5C50", "#E06C75", "#98E096", "#E5C07B",
                "#61AFEF", "#C678DD", "#56B6C2", "#D4E8D0",
            ),
        ),
        // 2) Termius Light
        scheme(
            id = "termius_light",
            name = "Termius Light",
            fg = "#1C1C1E",
            bg = "#F5F5F7",
            cursor = "#007AFF",
            ansi = listOf(
                "#1C1C1E", "#D70015", "#248A3D", "#9A6700",
                "#0040DD", "#8944AB", "#0071A4", "#8E8E93",
                "#636366", "#FF453A", "#30D158", "#FFD60A",
                "#0A84FF", "#BF5AF2", "#64D2FF", "#1C1C1E",
            ),
        ),
        // 3) Dracula — most ported dark theme
        scheme(
            id = "dracula",
            name = "Dracula",
            fg = "#F8F8F2",
            bg = "#282A36",
            cursor = "#F8F8F2",
            ansi = listOf(
                "#21222C", "#FF5555", "#50FA7B", "#F1FA8C",
                "#BD93F9", "#FF79C6", "#8BE9FD", "#F8F8F2",
                "#6272A4", "#FF6E6E", "#69FF94", "#FFFFA5",
                "#D6ACFF", "#FF92DF", "#A4FFFF", "#FFFFFF",
            ),
        ),
        // 4) Nord
        scheme(
            id = "nord",
            name = "Nord",
            fg = "#D8DEE9",
            bg = "#2E3440",
            cursor = "#D8DEE9",
            ansi = listOf(
                "#3B4252", "#BF616A", "#A3BE8C", "#EBCB8B",
                "#81A1C1", "#B48EAD", "#88C0D0", "#E5E9F0",
                "#4C566A", "#BF616A", "#A3BE8C", "#EBCB8B",
                "#81A1C1", "#B48EAD", "#8FBCBB", "#ECEFF4",
            ),
        ),
        // 5) Solarized Dark
        scheme(
            id = "solarized_dark",
            name = "Solarized Dark",
            fg = "#839496",
            bg = "#002B36",
            cursor = "#839496",
            ansi = listOf(
                "#073642", "#DC322F", "#859900", "#B58900",
                "#268BD2", "#D33682", "#2AA198", "#EEE8D5",
                "#002B36", "#CB4B16", "#586E75", "#657B83",
                "#839496", "#6C71C4", "#93A1A1", "#FDF6E3",
            ),
        ),
        // 6) Gruvbox Dark
        scheme(
            id = "gruvbox_dark",
            name = "Gruvbox Dark",
            fg = "#EBDBB2",
            bg = "#282828",
            cursor = "#EBDBB2",
            ansi = listOf(
                "#282828", "#CC241D", "#98971A", "#D79921",
                "#458588", "#B16286", "#689D6A", "#A89984",
                "#928374", "#FB4934", "#B8BB26", "#FABD2F",
                "#83A598", "#D3869B", "#8EC07C", "#EBDBB2",
            ),
        ),
        // 7) Kanagawa Wave (Termius + neovim hit)
        scheme(
            id = "kanagawa_wave",
            name = "Kanagawa Wave",
            fg = "#DCD7BA",
            bg = "#1F1F28",
            cursor = "#C8C093",
            ansi = listOf(
                "#090618", "#C34043", "#76946A", "#C0A36E",
                "#7E9CD8", "#957FB8", "#6A9589", "#C8C093",
                "#727169", "#E82424", "#98BB6C", "#E6C384",
                "#7FB4CA", "#938AA9", "#7AA89F", "#DCD7BA",
            ),
        ),
        // 8) Everforest Dark
        scheme(
            id = "everforest_dark",
            name = "Everforest Dark",
            fg = "#D3C6AA",
            bg = "#2D353B",
            cursor = "#D3C6AA",
            ansi = listOf(
                "#343F44", "#E67E80", "#A7C080", "#DBBC7F",
                "#7FBBB3", "#D699B6", "#83C092", "#D3C6AA",
                "#5C6A72", "#E67E80", "#A7C080", "#DBBC7F",
                "#7FBBB3", "#D699B6", "#83C092", "#D3C6AA",
            ),
        ),
        // 9) Night Owl
        scheme(
            id = "night_owl",
            name = "Night Owl",
            fg = "#D6DEEB",
            bg = "#011627",
            cursor = "#80A4C2",
            ansi = listOf(
                "#011627", "#EF5350", "#22DA6E", "#ADDB67",
                "#82AAFF", "#C792EA", "#21C7A8", "#FFFFFF",
                "#575656", "#EF5350", "#22DA6E", "#FFEB95",
                "#82AAFF", "#C792EA", "#7FDBCA", "#FFFFFF",
            ),
        ),
        // 10) Flexoki Dark (Termius catalog)
        scheme(
            id = "flexoki_dark",
            name = "Flexoki Dark",
            fg = "#CECDC3",
            bg = "#100F0F",
            cursor = "#CECDC3",
            ansi = listOf(
                "#100F0F", "#D14D41", "#879A39", "#D0A215",
                "#4385BE", "#CE5D97", "#3AA99F", "#878580",
                "#575653", "#D14D41", "#879A39", "#D0A215",
                "#4385BE", "#CE5D97", "#3AA99F", "#CECDC3",
            ),
        ),
    )

    /** Default pairs with Glass Sequoia chrome (proposal B) + Pulse Mono terminal (proposal A). */
    const val DEFAULT_ID = "pulse_mono"

    fun byId(id: String): TerminalScheme =
        all.firstOrNull { it.id == id } ?: all.first()

    private fun scheme(
        id: String,
        name: String,
        fg: String,
        bg: String,
        cursor: String,
        ansi: List<String>,
    ) = TerminalScheme(
        id = id,
        displayName = name,
        foreground = hex(fg),
        background = hex(bg),
        cursor = hex(cursor),
        ansi = ansi.map { hex(it) }.toIntArray(),
    )
}

/** Cached Typeface loads from assets / system. */
object TerminalTypefaceCache {
    private val cache = ConcurrentHashMap<String, Typeface>()

    fun get(context: Context, typefaceId: String): Typeface {
        // Resolve legacy / unknown ids before cache key (microsoft_yahei → jetbrains_mono).
        val spec = BuiltinTypefaces.byId(typefaceId)
        val cacheKey = spec.id
        return cache.getOrPut(cacheKey) {
            val path = spec.assetPath
            if (path.isNullOrBlank()) {
                Typeface.MONOSPACE
            } else {
                runCatching {
                    Typeface.createFromAsset(context.applicationContext.assets, path)
                }.getOrElse { Typeface.MONOSPACE }
            }
        }
    }
}

/** Sample man-page text for live preview (Termius-style). */
val TERMINAL_PREVIEW_LINES: List<String> = listOf(
    "RM(1)                    User Commands                    RM(1)",
    "",
    "NAME",
    "       rm - remove files or directories",
    "",
    "SYNOPSIS",
    "       rm [OPTION]... FILE...",
    "",
    "DESCRIPTION",
    "       This manual page documents the GNU version of rm. rm",
    "       removes each specified file. By default, it does not",
    "       remove directories.",
    "",
    "       If a file is unwritable, the standard input is a tty,",
    "       and the -f or --force option is not given, rm prompts",
    "       the user whether to remove the file.",
    "",
    "OPTIONS",
    "       Remove (unlink) the FILE(s).",
    "",
    "       -f, --force",
    "              ignore nonexistent files, never prompt",
    "",
    "       -i, --interactive",
    "              prompt before every removal",
    "",
    "       -r, -R, --recursive",
    "              remove directories and their contents recursively",
    "",
    "       -v, --verbose",
    "              explain what is being done",
)
