package app.mtmux.core.data.diag

/**
 * Masks hosts, users, session names and screen text before a diagnostic log
 * goes somewhere public (a GitHub issue). Names map to stable placeholders
 * (`h1`, `s2`) so the sequence of events still reads correctly.
 *
 * LogHub uploads are not redacted: only the maintainer can read those.
 */
object DiagRedactor {

    private val preview = Regex("""\bpreview=.*$""")
    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val ipv4 = Regex("""(?<![\d.])(?:\d{1,3}\.){3}\d{1,3}(?![\d.])""")
    // At least five groups or a "::" so log timestamps (23:28:10) never match.
    private val ipv6 = Regex(
        """(?<![0-9A-Fa-f:])(?:[0-9A-Fa-f]{1,4}:){4,7}[0-9A-Fa-f]{1,4}(?![0-9A-Fa-f:])|""" +
            """(?<![0-9A-Fa-f:])[0-9A-Fa-f]{0,4}(?::[0-9A-Fa-f]{0,4})*::[0-9A-Fa-f:]*[0-9A-Fa-f](?![0-9A-Fa-f:])""",
    )
    private val homeDir = Regex("""/(home|Users)/[^/\s]+""")
    private val hostKeys = Regex("""\b(alias|host|hostname|rawHost)=([^\s,\]]+)""")
    private val userKeys = Regex("""\b(user|username)=([^\s,\]]+)""")
    private val sessionKeys = Regex("""\b(session|name)=([^\s,\]]+)""")
    private val sessionList = Regex("""\bnames=\[([^\]]*)]""")
    /** `<host uuid>/<session>` as written by attach / capture lines. */
    private val hostSession = Regex(
        """\b([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/([^\s,\]]+)""",
    )

    fun redact(log: String): String {
        val hosts = Placeholders("h")
        val sessions = Placeholders("s")
        return log.lineSequence().joinToString("\n") { line ->
            var out = preview.replace(line, "preview=<redacted>")
            out = email.replace(out, "<email>")
            out = ipv4.replace(out, "<ip>")
            out = ipv6.replace(out, "<ip6>")
            out = homeDir.replace(out) { "/${it.groupValues[1]}/<user>" }
            out = hostSession.replace(out) { "${it.groupValues[1]}/${sessions.of(it.groupValues[2])}" }
            out = sessionList.replace(out) { m ->
                val names = m.groupValues[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }
                "names=[" + names.joinToString(", ") { sessions.of(it) } + "]"
            }
            out = hostKeys.replace(out) { m ->
                val v = m.groupValues[2]
                if (v.startsWith("<")) m.value else "${m.groupValues[1]}=${hosts.of(v)}"
            }
            out = userKeys.replace(out) { "${it.groupValues[1]}=<user>" }
            out = sessionKeys.replace(out) { "${it.groupValues[1]}=${sessions.of(it.groupValues[2])}" }
            out
        }
    }

    private class Placeholders(private val prefix: String) {
        private val seen = LinkedHashMap<String, String>()
        fun of(value: String): String =
            if (value.startsWith("<")) value else seen.getOrPut(value) { "$prefix${seen.size + 1}" }
    }
}
