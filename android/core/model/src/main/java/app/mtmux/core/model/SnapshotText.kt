package app.mtmux.core.model

/**
 * Session-list snapshot: the **exit-time visible conversation**, not a
 * density contest against 80 lines of tmux history.
 *
 * Cards used to store `capture-pane -S -80` and then [String.take] 400
 * from the **start** — so the list showed old history / TUI chrome, not
 * what the user was looking at when they left.
 */
object SnapshotText {
    /** Visible card height is ~1/3 of the old 6-line preview. */
    const val CARD_LINES = 2
    const val CARD_CHARS = 160
    const val STORE_CHARS = 2000

    /**
     * Last [maxLines] meaningful lines (then [maxChars] from the tail).
     * Box-drawing-only / blank chrome is dropped.
     */
    fun forCard(
        raw: String?,
        maxLines: Int = CARD_LINES,
        maxChars: Int = CARD_CHARS,
    ): String? {
        if (raw.isNullOrBlank()) return null
        val lines = raw.lines()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() && !isChromeOnly(it) }
        if (lines.isEmpty()) return null
        var text = lines.takeLast(maxLines).joinToString("\n").trim()
        if (text.length > maxChars) {
            val cut = text.takeLast(maxChars)
            text = cut.substringAfter('\n', cut).trim()
        }
        return text.takeIf { it.isNotBlank() }
    }

    fun isUseful(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val n = text.lines().count { it.isNotBlank() && !isChromeOnly(it) }
        return text.trim().length >= 24 && n >= 1
    }

    /**
     * Background recapture may refresh card **text**, but the corner chip is
     * last-used time. Keep a previous stamp; only first fill uses [nowEpochMs].
     */
    fun stampForBackgroundCapture(previousUpdatedAtEpochMs: Long?, nowEpochMs: Long): Long {
        val prev = previousUpdatedAtEpochMs ?: return nowEpochMs
        return if (prev > 0L) prev else nowEpochMs
    }

    /**
     * Recency for the session-card chip / recent sort.
     * Prefer the later of last user open and a user-driven snapshot (leave /
     * explicit refresh). A background recapture must not pass a newer stamp.
     */
    fun recencyEpochMs(lastOpenedEpochMs: Long?, snapshotUpdatedAtEpochMs: Long?): Long? {
        val opened = lastOpenedEpochMs?.takeIf { it > 0L }
        val snap = snapshotUpdatedAtEpochMs?.takeIf { it > 0L }
        return when {
            opened != null && snap != null -> maxOf(opened, snap)
            opened != null -> opened
            else -> snap
        }
    }

    private fun isChromeOnly(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty()) return true
        return t.all { ch ->
            ch.isWhitespace() || ch in BOX
        }
    }

    private val BOX: Set<Char> = (
        "─━│┃┄┅┆┇┈┉┊┋┌┍┎┏┐┑┒┓└┕┖┗┘┙┚┛├┝┞┟┠┡┢┣┤┥┦┧┨┩┪┫" +
            "┬┭┮┯┰┱┲┳┴┵┶┷┸┹┺┻┼┽┾┿╀╁╂╃╄╅╆╇╈╉╊╋═║╒╓╔╕╖╗╘╙╚╛╜╝" +
            "╞╟╠╡╢╣╤╥╦╧╨╩╪╫╬╭╮╯╰╱╲╳╴╵╶╷╸╹╺╻╼╽╾╿" +
            "▀▁▂▃▄▅▆▇█▉▊▋▌▍▎▏▐░▒▓▔▕▖▗▘▙▚▛▜▝▞▟■□▪▫●○"
        ).toSet()
}
