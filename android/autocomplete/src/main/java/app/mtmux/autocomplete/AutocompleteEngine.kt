package app.mtmux.autocomplete

import app.mtmux.core.data.db.HistoryDao
import app.mtmux.core.data.db.SnippetDao
import app.mtmux.core.model.Suggestion
import app.mtmux.core.model.SuggestionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AutocompleteEngine(
    private val snippets: SnippetDao,
    private val history: HistoryDao,
    private val builtins: List<String> = BuiltinCommands.DEFAULT,
) {
    /**
     * @param linePrefix text of the current input line (or last token)
     * @param tuiMode when true, return empty (avoid fighting full-screen TUIs)
     * @param pathHints remote path completions (from compgen); optional
     * @param vars optional template vars for snippets: host, user, session
     */
    suspend fun suggest(
        hostId: String,
        linePrefix: String,
        tuiMode: Boolean,
        limit: Int = 12,
        vars: Map<String, String> = emptyMap(),
        pathHints: List<String> = emptyList(),
    ): List<Suggestion> = withContext(Dispatchers.Default) {
        if (tuiMode || linePrefix.isBlank()) return@withContext emptyList()
        val q = linePrefix.trim().substringAfterLast('\n').trim()
        if (q.isBlank()) return@withContext emptyList()
        val token = q.substringAfterLast(' ')
        val out = LinkedHashMap<String, Suggestion>()

        // Path completions first when user is completing a path-like token
        if (pathHints.isNotEmpty() && looksLikePathToken(token, q)) {
            pathHints.forEach { path ->
                val id = "path_$path"
                out.putIfAbsent(
                    id,
                    Suggestion(
                        id = id,
                        label = "📁 $path",
                        insertion = pathCompletionInsertion(token, path),
                        source = SuggestionSource.PATH,
                    ),
                )
            }
        }

        snippets.all().forEach { s ->
            if (s.name.contains(q, ignoreCase = true) ||
                s.name.contains(token, ignoreCase = true) ||
                s.body.contains(q, ignoreCase = true)
            ) {
                val id = "snip_${s.id}"
                out[id] = Suggestion(
                    id = id,
                    label = "⌘ ${s.name}",
                    insertion = applyVars(s.body, vars),
                    source = SuggestionSource.SNIPPET,
                    runAfterInsert = s.runOnSelect,
                )
            }
        }

        history.match(hostId, token.ifBlank { q }, limit).forEach { h ->
            val id = "hist_${h.id}"
            out.putIfAbsent(
                id,
                Suggestion(
                    id = id,
                    label = "◷ ${h.command}",
                    insertion = h.command,
                    source = SuggestionSource.HISTORY,
                ),
            )
        }

        builtins.filter {
            it.startsWith(token, ignoreCase = true) || it.contains(token, ignoreCase = true)
        }.forEach { cmd ->
            val id = "bin_$cmd"
            out.putIfAbsent(
                id,
                Suggestion(
                    id = id,
                    label = "∗ $cmd",
                    insertion = applyVars(cmd, vars),
                    source = SuggestionSource.BUILTIN,
                ),
            )
        }

        out.values.take(limit)
    }

    suspend fun recordHistory(hostId: String, command: String) {
        val cmd = command.trim()
        if (cmd.isBlank() || cmd.length > 500) return
        history.insert(
            app.mtmux.core.data.db.CommandHistoryEntity(
                hostId = hostId,
                command = cmd,
                usedAtEpochMs = System.currentTimeMillis(),
            ),
        )
        history.trim()
    }

    companion object {
        fun applyVars(body: String, vars: Map<String, String>): String {
            var s = body
            vars.forEach { (k, v) ->
                s = s.replace("{{$k}}", v)
            }
            return s
        }

        /** Whether this token/line should trigger remote path completion. */
        fun looksLikePathToken(token: String, fullLine: String = token): Boolean {
            if (token.isEmpty()) {
                // "cd " with trailing space → complete directories
                val t = fullLine.trimEnd()
                return t == "cd" || t.endsWith(" cd") ||
                    t.endsWith("ls") || t.endsWith(" cat") || t.endsWith(" vim") ||
                    t.endsWith(" nvim") || t.endsWith(" nano")
            }
            return token.startsWith("/") ||
                token.startsWith("./") ||
                token.startsWith("../") ||
                token.startsWith("~/") ||
                token.contains('/') ||
                fullLine.trimStart().startsWith("cd ")
        }

        /**
         * Build remote `compgen` command. Returns null when path completion is not needed.
         * Uses `compgen -f` for files+dirs; `-d` only when line is clearly `cd `.
         */
        fun remoteCompgenCommand(linePrefix: String): String? {
            val q = linePrefix.trim().substringAfterLast('\n')
            val token = q.trim().substringAfterLast(' ')
            if (!looksLikePathToken(token, q.trim())) return null
            val dirsOnly = q.trim().startsWith("cd ") || q.trim() == "cd"
            val prefix = token
            val flag = if (dirsOnly) "-d" else "-f"
            val script =
                "compgen $flag -- ${shQuote(prefix)} 2>/dev/null | head -n 24"
            return "bash -c ${shQuote(script)}"
        }

        fun shQuote(s: String): String = "'" + s.replace("'", "'\"'\"'") + "'"

        fun parseCompgenOutput(raw: String): List<String> =
            raw.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(24)
                .toList()

        /**
         * When user typed `foo/ba`, insertion should be just the suffix after the
         * last partial token, or full path if token empty.
         */
        fun pathCompletionInsertion(token: String, completed: String): String {
            if (token.isEmpty()) return completed
            // Replace current token with full completion: return only the delta
            // if completed extends token; else full path
            return if (completed.startsWith(token)) {
                completed.removePrefix(token)
            } else {
                // different path root — send full and hope user accepts overwrite via shell
                completed
            }
        }

        /**
         * TUI pause heuristics (AND-089).
         * Prefer DEC alt-screen; fall back to known full-screen app markers.
         */
        fun isTuiMode(
            alternateScreen: Boolean,
            plainSnapshot: String,
            lineBuffer: String,
        ): Boolean {
            if (alternateScreen) return true
            // User actively typing a shell line → not TUI for completion purposes
            // (even inside Claude input boxes we still pause if alt-screen was set above)
            val lower = plainSnapshot.lowercase()
            val markers = listOf(
                "╭", "╰", "│", // box drawing common in Claude/Codex
                "claude code",
                "codex",
                "───", // heavy rules
            )
            val denseUi = markers.count { lower.contains(it) || plainSnapshot.contains(it) } >= 2
            if (denseUi && lineBuffer.length < 2) return true
            // classic TUI titles on first lines
            val head = plainSnapshot.lineSequence().take(3).joinToString("\n").lowercase()
            if (head.contains("htop") || head.contains("btop") || head.contains("nvtop")) return true
            return false
        }
    }
}

object BuiltinCommands {
    val DEFAULT = listOf(
        "git status",
        "git diff",
        "git pull",
        "git push",
        "tmux ls",
        "tmux attach -t ",
        "htop",
        "ls -la",
        "cd ",
        "ssh ",
        "claude",
        "codex",
        "systemctl status ",
        "docker ps",
    )
}
