package app.mtmux.ssh

import app.mtmux.core.model.Host
import app.mtmux.core.model.HostResourceText
import app.mtmux.core.model.HostResources
import app.mtmux.core.model.TmuxSession

data class TmuxWindow(
    val session: String,
    val index: Int,
    val name: String,
    val active: Boolean,
    val panes: Int = 1,
)

data class TmuxPane(
    val session: String,
    val windowIndex: Int,
    val paneIndex: Int,
    val title: String,
    val active: Boolean,
)

data class EnvProbe(
    val tmux: Boolean,
    /** `mosh-server` binary present (SSH `command -v`). Not enough for usable Mosh. */
    val moshServer: Boolean,
    val osPretty: String?,
    /** `uname -s`, so macOS is `Darwin` even when os-release says macOS. */
    val uname: String? = null,
    val packageManager: String?,
    val installHint: String?,
    val latencyMs: Long?,
    /**
     * Live UDP path after MOSH CONNECT. null = not tested yet (binary-only probe).
     * false = handshake or UDP failed — see [moshUdpDetail].
     */
    val moshUdp: Boolean? = null,
    val moshUdpPort: Int? = null,
    /** Concrete reason for UDP failure / success. */
    val moshUdpDetail: String? = null,
    val memAvailKb: Long? = null,
    val memTotalKb: Long? = null,
    val diskAvailKb: Long? = null,
    val diskTotalKb: Long? = null,
) {
    /** Packages + UDP path OK → AUTO may prefer Mosh. */
    val moshReady: Boolean get() = moshServer && moshUdp == true

    /** tmux is installed (SSH-only client). */
    val packagesReady: Boolean get() = tmux

    val resources: HostResources
        get() = HostResources(memAvailKb, memTotalKb, diskAvailKb, diskTotalKb)
}

fun Host.applyEnvProbe(env: EnvProbe): Host {
    val now = System.currentTimeMillis()
    val gotStats = env.memAvailKb != null || env.diskAvailKb != null
    return copy(
        tmuxInstalled = env.tmux,
        moshInstalled = null,
        lastLatencyMs = env.latencyMs ?: lastLatencyMs,
        lastCheckedAtEpochMs = now,
        lastMemAvailKb = env.memAvailKb ?: lastMemAvailKb,
        lastMemTotalKb = env.memTotalKb ?: lastMemTotalKb,
        lastDiskAvailKb = env.diskAvailKb ?: lastDiskAvailKb,
        lastDiskTotalKb = env.diskTotalKb ?: lastDiskTotalKb,
        lastStatsAtEpochMs = if (gotStats) now else lastStatsAtEpochMs,
    )
}

/**
 * Control-plane helpers — always over SSH exec (not Mosh).
 *
 * All tmux commands go through multi-socket discovery so sessions created by
 * interactive SSH tools (Termius, desktop) and by the app share one view.
 *
 * One server per user. New sessions use `$TMUX_TMPDIR` when the login shell
 * set it, otherwise `/tmp`. They do not use the per-SSH `$TMPDIR` macOS puts
 * under `/var/folders`, which a later `tmux ls` on the computer cannot see.
 * Existing sockets, including those private directories, are still listed.
 */
class TmuxControl(private val connections: ConnectionManager) {

    suspend fun listSessions(hostId: String): Result<List<TmuxSession>> {
        val out = connections.exec(hostId, listSessionsCmd())
        return out.map { text -> parseListSessions(hostId, text) }
    }

    suspend fun listWindows(
        hostId: String,
        session: String,
        socketPath: String? = null,
    ): Result<List<TmuxWindow>> {
        val out = connections.exec(
            hostId,
            withSessionSocket(session, socketPath) { t ->
                "$t list-windows -t ${shellQuote(session)} " +
                    "-F '#{window_index}|#{window_name}|#{window_active}|#{window_panes}' 2>/dev/null || true"
            },
        )
        return out.map { text ->
            text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { line ->
                val p = line.split('|')
                if (p.isEmpty()) null
                else TmuxWindow(
                    session = session,
                    index = p[0].toIntOrNull() ?: 0,
                    name = p.getOrNull(1) ?: "",
                    active = p.getOrNull(2) == "1",
                    panes = p.getOrNull(3)?.toIntOrNull() ?: 1,
                )
            }.toList()
        }
    }

    suspend fun listPanes(
        hostId: String,
        session: String,
        windowIndex: Int,
        socketPath: String? = null,
    ): Result<List<TmuxPane>> {
        val target = "$session:$windowIndex"
        val out = connections.exec(
            hostId,
            withSessionSocket(session, socketPath) { t ->
                "$t list-panes -t ${shellQuote(target)} " +
                    "-F '#{pane_index}|#{pane_title}|#{pane_active}' 2>/dev/null || true"
            },
        )
        return out.map { text ->
            text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { line ->
                val p = line.split('|')
                if (p.isEmpty()) null
                else TmuxPane(
                    session = session,
                    windowIndex = windowIndex,
                    paneIndex = p[0].toIntOrNull() ?: 0,
                    title = p.getOrNull(1) ?: "",
                    active = p.getOrNull(2) == "1",
                )
            }.toList()
        }
    }

    suspend fun probeTmux(hostId: String): Boolean {
        val r = connections.exec(hostId, "command -v tmux")
        return r.getOrNull()?.isNotBlank() == true
    }

    suspend fun probeMoshServer(hostId: String): Boolean {
        val r = connections.exec(hostId, "command -v mosh-server")
        return r.getOrNull()?.isNotBlank() == true
    }

    /** Full environment probe: tmux, mosh, OS, package manager, latency (AND-029/030). */
    suspend fun probeEnvironment(hostId: String): EnvProbe {
        val script = """
            echo TMUX=$(command -v tmux || true)
            echo UNAME=$(uname -s)
            if [ -f /etc/os-release ]; then . /etc/os-release; echo OS=${'$'}{PRETTY_NAME:-${'$'}ID}; else echo OS=$(uname -s); fi
            if command -v apt-get >/dev/null 2>&1; then echo PM=apt
            elif command -v dnf >/dev/null 2>&1; then echo PM=dnf
            elif command -v yum >/dev/null 2>&1; then echo PM=yum
            elif command -v pacman >/dev/null 2>&1; then echo PM=pacman
            elif command -v apk >/dev/null 2>&1; then echo PM=apk
            elif command -v brew >/dev/null 2>&1; then echo PM=brew
            else echo PM=unknown; fi
            $HOST_RESOURCE_SCRIPT
        """.trimIndent()
        val t0 = System.nanoTime()
        val raw = connections.exec(hostId, script).getOrDefault("")
        val latency = (System.nanoTime() - t0) / 1_000_000L
        val map = raw.lineSequence().mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) null else line.substring(0, i) to line.substring(i + 1).trim()
        }.toMap()
        val pm = map["PM"]?.takeIf { it.isNotBlank() && it != "unknown" }
        val tmuxOk = !map["TMUX"].isNullOrBlank()
        val moshOk = false
        val hint = when {
            tmuxOk -> null
            else -> installCommand(pm, needTmux = true, needMosh = false)
        }
        val res = HostResourceText.parseKv(raw)
        return EnvProbe(
            tmux = tmuxOk,
            moshServer = moshOk,
            osPretty = map["OS"],
            uname = map["UNAME"],
            packageManager = pm,
            installHint = hint,
            latencyMs = latency,
            memAvailKb = res.memAvailKb,
            memTotalKb = res.memTotalKb,
            diskAvailKb = res.diskAvailKb,
            diskTotalKb = res.diskTotalKb,
        )
    }

    /** Cheap mem/disk sample over SSH exec (after attach / refresh). */
    suspend fun probeHostResources(hostId: String): HostResources {
        val raw = connections.exec(hostId, HOST_RESOURCE_SCRIPT).getOrDefault("")
        return HostResourceText.parseKv(raw)
    }

    suspend fun capturePane(hostId: String, target: String, socketPath: String? = null): Result<String> {
        val sessionName = target.substringBefore(':')
        val cmd = withSessionSocket(sessionName, socketPath) { t ->
            // Visible pane only. -S -80 used to dump 80 lines of history; the
            // list card then took the *start* of that dump (old/random text).
            "$t capture-pane -p -t ${shellQuote(target)} 2>/dev/null || true"
        }
        return connections.exec(hostId, cmd)
    }

    /**
     * Detached create-or-ensure on the preferred shared server.
     * Never creates a private second server when one already exists.
     */
    suspend fun newSessionOrAttach(hostId: String, name: String): Result<String> {
        val nameQ = shellQuote(name)
        val cmd = """
$SOCKET_HELPER
_sock=${'$'}(_mtmux_find_session $nameQ)
if [ -n "${'$'}_sock" ]; then
  tmux -S "${'$'}_sock" new-session -A -s $nameQ -d 2>/dev/null
  echo OK
  exit 0
fi
_pref=${'$'}(_mtmux_canonical_base)
export TMUX_TMPDIR="${'$'}_pref"
unset TMPDIR
mkdir -p "${'$'}TMUX_TMPDIR" 2>/dev/null || true
tmux new-session -A -s $nameQ -d 2>/dev/null
echo OK
""".trimIndent()
        return connections.exec(hostId, cmd)
    }

    suspend fun renameSession(
        hostId: String,
        oldName: String,
        newName: String,
        socketPath: String? = null,
    ): Result<String> {
        val cmd = withSessionSocket(oldName, socketPath) { t ->
            "$t rename-session -t ${shellQuote(oldName)} ${shellQuote(newName)} && echo OK"
        }
        return connections.exec(hostId, cmd)
    }

    suspend fun killSession(hostId: String, name: String, socketPath: String? = null): Result<String> {
        val cmd = withSessionSocket(name, socketPath) { t ->
            "$t kill-session -t ${shellQuote(name)} && echo OK"
        }
        return connections.exec(hostId, cmd)
    }

    companion object {
        /**
         * MemAvailable + df -Pk / — portable on Linux VPS (busybox df ok).
         */
        val HOST_RESOURCE_SCRIPT: String = """
            if [ -r /proc/meminfo ]; then
              MEM_AVAIL_KB=${'$'}(awk '/MemAvailable:/ {print ${'$'}2}' /proc/meminfo)
              MEM_TOTAL_KB=${'$'}(awk '/MemTotal:/ {print ${'$'}2}' /proc/meminfo)
              if [ -z "${'$'}MEM_AVAIL_KB" ]; then
                MEM_AVAIL_KB=${'$'}(awk '/MemFree:/ {f=${'$'}2} /Buffers:/ {b=${'$'}2} /^Cached:/ {c=${'$'}2} END {print f+b+c}' /proc/meminfo)
              fi
              echo MEM_AVAIL_KB=${'$'}MEM_AVAIL_KB
              echo MEM_TOTAL_KB=${'$'}MEM_TOTAL_KB
            fi
            df -Pk / 2>/dev/null | awk 'NR==2 {print "DISK_AVAIL_KB="${'$'}4; print "DISK_TOTAL_KB="${'$'}2}'
        """.trimIndent()

        /**
         * Multiline shell helpers inlined into remote commands.
         * Keep real newlines (do NOT flatten to `;`) — function bodies break otherwise.
         */
        val SOCKET_HELPER: String = """
_mtmux_socks() {
  _uid=${'$'}(id -u)
  {
    for _base in ${'$'}{TMUX_TMPDIR:-} ${'$'}{TMPDIR:-} /tmp ${'$'}{XDG_RUNTIME_DIR:-} /run/user/${'$'}_uid; do
      [ -n "${'$'}_base" ] || continue
      _dir="${'$'}_base/tmux-${'$'}_uid"
      [ -d "${'$'}_dir" ] || continue
      for _s in "${'$'}_dir"/*; do
        [ -S "${'$'}_s" ] && printf '%s\n' "${'$'}_s"
      done
    done
    if command -v find >/dev/null 2>&1; then
      find /tmp /run/user/${'$'}_uid -maxdepth 3 -type s -path "*/tmux-${'$'}_uid/*" 2>/dev/null
      if [ "${'$'}(uname -s)" = Darwin ]; then
        find /var/folders -maxdepth 6 -type s -path "*/tmux-${'$'}_uid/*" 2>/dev/null
      fi
    fi
  } | awk 'NF && !seen[${'$'}0]++'
}
_mtmux_find_session() {
  _n="${'$'}1"
  for _s in ${'$'}(_mtmux_socks); do
    if tmux -S "${'$'}_s" has-session -t "${'$'}_n" 2>/dev/null; then
      printf '%s\n' "${'$'}_s"
      return 0
    fi
  done
  return 1
}
_mtmux_canonical_base() {
  # A login-wide TMUX_TMPDIR is the shared server. Do not use this SSH's
  # TMPDIR: macOS gives each login a private /var/folders directory.
  if [ -n "${'$'}{TMUX_TMPDIR:-}" ]; then printf '%s\n' "${'$'}TMUX_TMPDIR"; return 0; fi
  printf '%s\n' /tmp
}
""".trimIndent()

        fun tmuxBin(socketPath: String?): String =
            if (socketPath.isNullOrBlank()) "tmux" else "tmux -S ${shellQuote(socketPath)}"

        /**
         * Run [body] with a tmux client bound to [socketPath], or auto-discover
         * the socket that holds [sessionName] when path is unknown.
         */
        fun withSessionSocket(
            sessionName: String,
            socketPath: String?,
            body: (tmuxClient: String) -> String,
        ): String {
            if (!socketPath.isNullOrBlank()) {
                return "$SOCKET_HELPER\n${body(tmuxBin(socketPath))}\n"
            }
            return """
$SOCKET_HELPER
_sock=${'$'}(_mtmux_find_session ${shellQuote(sessionName)})
if [ -n "${'$'}_sock" ]; then
  ${body("tmux -S \"${'$'}_sock\"")}
else
  ${body("tmux")}
fi
""".trimIndent()
        }

        fun listSessionsCmd(): String = """
$SOCKET_HELPER
_mtmux_socks | while read -r _s; do
  tmux -S "${'$'}_s" list-sessions -F "#{session_name}|#{session_windows}|#{session_attached}|${'$'}_s" 2>/dev/null || true
done
if [ -z "${'$'}(_mtmux_socks | head -n1)" ]; then
  tmux list-sessions -F "#{session_name}|#{session_windows}|#{session_attached}|" 2>/dev/null || true
fi
""".trimIndent()

        /**
         * Single remote command for PTY exec: find session across sockets and attach.
         * Only creates a new session if the name truly does not exist anywhere.
         * New sessions use the canonical server (`TMUX_TMPDIR` or `/tmp`).
         */
        fun attachOrCreateCmd(sessionName: String, knownSocket: String? = null): String {
            val nameQ = shellQuote(sessionName)
            if (!knownSocket.isNullOrBlank()) {
                val sockQ = shellQuote(knownSocket)
                return """
$SOCKET_HELPER
if tmux -S $sockQ has-session -t $nameQ 2>/dev/null; then
  exec tmux -S $sockQ attach-session -d -t $nameQ
fi
_sock=${'$'}(_mtmux_find_session $nameQ)
if [ -n "${'$'}_sock" ]; then
  exec tmux -S "${'$'}_sock" attach-session -d -t $nameQ
fi
_pref=${'$'}(_mtmux_canonical_base)
export TMUX_TMPDIR="${'$'}_pref"
unset TMPDIR
mkdir -p "${'$'}TMUX_TMPDIR" 2>/dev/null || true
exec tmux new-session -A -s $nameQ
""".trimIndent()
            }
            return """
$SOCKET_HELPER
_sock=${'$'}(_mtmux_find_session $nameQ)
if [ -n "${'$'}_sock" ]; then
  exec tmux -S "${'$'}_sock" attach-session -d -t $nameQ
fi
_pref=${'$'}(_mtmux_canonical_base)
export TMUX_TMPDIR="${'$'}_pref"
unset TMPDIR
mkdir -p "${'$'}TMUX_TMPDIR" 2>/dev/null || true
exec tmux new-session -A -s $nameQ
""".trimIndent()
        }

        /** Shell line so a Mac Terminal and SSH `tmux ls` use Muxa's server. */
        const val DESKTOP_TAKEOVER_SNIPPET: String =
            "# Muxa: one tmux server for Terminal, SSH, and the app.\n" +
                "export TMUX_TMPDIR=\"\${TMUX_TMPDIR:-/tmp}\""

        fun isDarwin(osPretty: String?, uname: String?): Boolean {
            if (uname.equals("Darwin", ignoreCase = true)) return true
            val os = osPretty?.lowercase() ?: return false
            return os.contains("darwin") || os.contains("mac")
        }

        fun installCommand(pm: String?, needTmux: Boolean, needMosh: Boolean): String {
            val pkgs = buildList {
                if (needTmux) add("tmux")
                if (needMosh) add("mosh")
            }.joinToString(" ")
            if (pkgs.isBlank()) return ""
            return when (pm) {
                "apt" -> "sudo apt-get update && sudo apt-get install -y $pkgs"
                "dnf" -> "sudo dnf install -y $pkgs"
                "yum" -> "sudo yum install -y $pkgs"
                "pacman" -> "sudo pacman -S --noconfirm $pkgs"
                "apk" -> "sudo apk add $pkgs"
                "brew" -> "brew install $pkgs"
                else -> "# install with the system package manager: $pkgs"
            }
        }

        /**
         * Parses `name|windows|attached|socket` lines.
         * Dedupes by session name (first socket wins; prefer attached if later).
         */
        fun parseListSessions(hostId: String, text: String): List<TmuxSession> {
            if (text.isBlank()) return emptyList()
            val byName = LinkedHashMap<String, TmuxSession>()
            text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { line ->
                    val session = if ('|' in line) {
                        val p = line.split('|')
                        TmuxSession(
                            hostId = hostId,
                            name = p[0],
                            windows = p.getOrNull(1)?.toIntOrNull() ?: 1,
                            attached = p.getOrNull(2) == "1",
                            socketPath = p.getOrNull(3)?.takeIf { it.isNotBlank() },
                        )
                    } else {
                        val name = line.substringBefore(':').trim()
                        if (name.isEmpty()) return@forEach
                        TmuxSession(hostId = hostId, name = name)
                    }
                    val prev = byName[session.name]
                    if (prev == null) {
                        byName[session.name] = session
                    } else if (!prev.attached && session.attached) {
                        byName[session.name] = session
                    }
                }
            return byName.values.toList()
        }

        fun shellQuote(s: String): String =
            "'" + s.replace("'", "'\"'\"'") + "'"

        /**
         * Pane scroll meta: `alternate_on history_size pane_height`.
         * Inline CLIs (AGy) keep history on the main pane; tmux-client 1049h is not the app's alt-screen.
         */
        fun captureHistoryCmd(sessionName: String, socketPath: String?): String {
            val q = shellQuote(sessionName)
            return withSessionSocket(sessionName, socketPath) { t ->
                "$t capture-pane -p -S - -t $q 2>/dev/null || true"
            }
        }

        fun paneScrollMetaCmd(sessionName: String, socketPath: String?): String {
            val q = shellQuote(sessionName)
            return withSessionSocket(sessionName, socketPath) { t ->
                "$t display-message -p -t $q '#{alternate_on} #{history_size} #{pane_height}' 2>/dev/null || true"
            }
        }

        /**
         * Completion probe for an attached pane. tmux keeps the outer terminal on
         * the alternate screen, so the pane is asked instead: prints `ALT=0|1`,
         * then (shell panes only) runs [compgenCmd] in the pane's directory.
         */
        fun paneCompletionCmd(sessionName: String, socketPath: String?, compgenCmd: String?): String {
            val q = shellQuote(sessionName)
            return withSessionSocket(sessionName, socketPath) { t ->
                buildString {
                    append("_i=$($t display-message -p -t $q '#{alternate_on}|#{pane_current_path}' 2>/dev/null); ")
                    append("echo \"ALT=${'$'}{_i%%|*}\"")
                    if (compgenCmd != null) {
                        append("; [ \"${'$'}{_i%%|*}\" = 1 ] || (cd \"${'$'}{_i#*|}\" 2>/dev/null; $compgenCmd)")
                    }
                }
            }
        }

        fun scrollHistoryCmd(
            sessionName: String,
            socketPath: String?,
            up: Boolean,
            lines: Int,
        ): String {
            val q = shellQuote(sessionName)
            val n = lines.coerceIn(1, 80)
            val dir = if (up) "scroll-up" else "scroll-down"
            return withSessionSocket(sessionName, socketPath) { t ->
                """
$t copy-mode -t $q 2>/dev/null || true
$t send-keys -t $q -X -N $n $dir 2>/dev/null || true
""".trimIndent()
            }
        }

        fun cancelCopyModeCmd(sessionName: String, socketPath: String?): String {
            val q = shellQuote(sessionName)
            return withSessionSocket(sessionName, socketPath) { t ->
                "$t send-keys -t $q -X cancel 2>/dev/null || true"
            }
        }
    }
}
