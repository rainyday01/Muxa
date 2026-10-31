package app.mtmux.feature.sessions

/**
 * Builds the shell line typed into a new tmux session:
 * optional mkdir/cd plus Claude / Codex / Grok / AGy, with optional
 * full-auto-approve flags after the TUI command.
 */
object NewSessionCommand {

    enum class DirMode { CreateAndEnter, EnterExisting }

    const val FLAG_SKIP_PERMISSIONS = "--dangerously-skip-permissions"
    const val FLAG_YOLO = "--yolo"

    data class Agent(val argv: String, val id: String) {
        companion object {
            val Claude = Agent("claude", "claude")
            val Codex = Agent("codex", "codex")
            val Grok = Agent("grok", "grok")
            val Agy = Agent("agy", "agy")
            val all = listOf(Claude, Codex, Grok, Agy)
        }
    }

    fun skipFlagFor(agentArgv: String): String? = when (agentArgv.trim()) {
        "claude", "agy" -> FLAG_SKIP_PERMISSIONS
        "codex" -> FLAG_YOLO
        else -> null
    }

    fun quotePath(path: String): String {
        val t = path.trim()
        if (t.isEmpty()) return t
        if (t.all { it.isLetterOrDigit() || it in "/._-~" }) return t
        return "'" + t.replace("'", "'\"'\"'") + "'"
    }

    fun compose(
        dir: String,
        mode: DirMode,
        agentArgv: String,
        skipDanger: Boolean = false,
        yolo: Boolean = false,
    ): String {
        val d = dir.trim()
        val parts = ArrayList<String>(3)
        if (d.isNotEmpty()) {
            val q = quotePath(d)
            if (mode == DirMode.CreateAndEnter) parts += "mkdir -p $q"
            parts += "cd $q"
        }
        val a = agentArgv.trim()
        if (a.isNotEmpty()) {
            val flag = when {
                skipDanger && a in setOf("claude", "agy") -> FLAG_SKIP_PERMISSIONS
                yolo && a == "codex" -> FLAG_YOLO
                else -> null
            }
            parts += if (flag != null) "$a $flag" else a
        }
        return parts.joinToString(" && ")
    }

    fun folderBasename(dir: String): String {
        val t = dir.trim().trimEnd('/')
        if (t.isEmpty()) return ""
        return t.substringAfterLast('/').substringAfterLast('\\')
            .removePrefix("~")
            .ifBlank { t }
    }

    /**
     * While [locked] is false, the name follows the directory (or agent) on every
     * keystroke. A previous suggestion must not count as a custom name — otherwise
     * the first character of `claude-one` sticks and the rest never arrives.
     */
    fun suggestedName(
        dir: String,
        agentArgv: String,
        current: String,
        locked: Boolean = false,
    ): String {
        if (locked) return current.ifBlank { "work" }
        val base = folderBasename(dir).ifBlank {
            agentArgv.trim().substringBefore(' ')
        }
        if (base.isBlank()) return current.ifBlank { "work" }
        return base
    }
}
