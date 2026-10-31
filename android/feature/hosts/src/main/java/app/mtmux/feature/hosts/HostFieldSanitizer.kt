package app.mtmux.feature.hosts

import android.content.Context
import app.mtmux.core.model.locale.AppLocale
import app.mtmux.core.ui.R

/**
 * Host field cleanup for common mobile paste mistakes
 * (user@host, multiline, password glued to IP, hidden newlines).
 */
object HostFieldSanitizer {

    enum class Hint {
        PasswordFromLine2,
        ExtraLinesIgnored,
        PasswordFromUserPass,
        Username,
        Port,
        PasswordGlued,
        PasswordAfterSpace,
    }

    data class ParsedHost(
        val host: String,
        val username: String? = null,
        val port: Int? = null,
        /** Password extracted from paste if present. */
        val password: String? = null,
        val hints: List<Pair<Hint, String?>> = emptyList(),
    ) {
        fun localizedNote(ctx: Context): String? {
            if (hints.isEmpty()) return null
            val loc = AppLocale.wrap(ctx)
            val sep = loc.getString(R.string.sanitizer_join)
            return hints.joinToString(sep) { (h, arg) ->
                when (h) {
                    Hint.PasswordFromLine2 -> loc.getString(R.string.sanitizer_password_line2)
                    Hint.ExtraLinesIgnored -> loc.getString(R.string.sanitizer_extra_lines)
                    Hint.PasswordFromUserPass -> loc.getString(R.string.sanitizer_password_userpass)
                    Hint.Username -> loc.getString(R.string.sanitizer_username, arg.orEmpty())
                    Hint.Port -> loc.getString(R.string.sanitizer_port, arg?.toIntOrNull() ?: 0)
                    Hint.PasswordGlued -> loc.getString(R.string.sanitizer_password_glued)
                    Hint.PasswordAfterSpace -> loc.getString(R.string.sanitizer_password_space)
                }
            }
        }
    }

    /**
     * Normalize pasted blob into host / optional user / port / password.
     *
     * Handles:
     * - `ubuntu@1.2.3.4`
     * - `1.2.3.4:22`
     * - `1.2.3.4Secret~` (password glued to IP)
     * - Multiline: line1 host, line2 password (TextField often hides line2)
     * - Invisible whitespace / CR / zero-width chars
     */
    fun parseHostname(raw: String): ParsedHost {
        // Strip invisible / BOM / zero-width
        var s = raw
            .replace("\uFEFF", "")
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
            .replace("\u00A0", " ")
        s = s.replace("\r\n", "\n").replace('\r', '\n').trim()

        var user: String? = null
        var password: String? = null
        var port: Int? = null
        val notes = mutableListOf<Pair<Hint, String?>>()

        // Multiline paste: first non-empty line = host-ish, second = password often
        val lines = s.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size >= 2) {
            s = lines[0]
            val line2 = lines[1]
            // Second line is password unless it clearly looks like another host/IP
            val line2IsHost = isPlausibleHost(line2) &&
                (line2.contains('.') || Regex("""^\d{1,3}(?:\.\d{1,3}){3}$""").matches(line2))
            if (!line2IsHost && line2.length >= 3) {
                password = line2
                notes += Hint.PasswordFromLine2 to null
            }
            if (lines.size > 2) notes += Hint.ExtraLinesIgnored to null
        }

        s = s.removePrefix("ssh://").substringBefore('/').trim()

        // user:pass@host or user@host
        if ('@' in s) {
            val left = s.substringBeforeLast('@')
            val right = s.substringAfterLast('@')
            if (':' in left && !left.startsWith("[")) {
                user = left.substringBefore(':').ifBlank { null }
                val passPart = left.substringAfter(':', "")
                if (passPart.isNotEmpty()) {
                    password = password ?: passPart
                    notes += Hint.PasswordFromUserPass to null
                }
            } else {
                user = left.ifBlank { null }
            }
            s = right
            if (user != null) notes += Hint.Username to user
        }

        // [ipv6]:port — skip for now; handle host:port for ipv4/hostname
        if (s.count { it == ':' } == 1 && s.substringAfterLast(':').all { it.isDigit() }) {
            port = s.substringAfterLast(':').toIntOrNull()
            s = s.substringBeforeLast(':')
            if (port != null) notes += Hint.Port to port.toString()
        }

        // IPv4 + glued password without separator: 1.2.3.4Secret
        val ipv4Bleed = Regex("""^(\d{1,3}(?:\.\d{1,3}){3})([^.\d][\S]*)$""").matchEntire(s)
        if (ipv4Bleed != null) {
            val ip = ipv4Bleed.groupValues[1]
            val rest = ipv4Bleed.groupValues[2]
            if (!rest.startsWith(".")) {
                s = ip
                password = password ?: rest
                notes += Hint.PasswordGlued to null
            }
        }

        // Final: only keep first whitespace-separated token (host field must be single token)
        if (s.any { it.isWhitespace() }) {
            val parts = s.split(Regex("\\s+")).filter { it.isNotEmpty() }
            s = parts.firstOrNull().orEmpty()
            if (parts.size > 1 && password == null && !isPlausibleHost(parts[1])) {
                password = parts.drop(1).joinToString(" ")
                notes += Hint.PasswordAfterSpace to null
            }
        }

        s = s.trim()
        return ParsedHost(
            host = s,
            username = user,
            port = port,
            password = password,
            hints = notes,
        )
    }

    fun isPlausibleHost(host: String): Boolean {
        val h = host.trim()
        if (h.isBlank() || h.length > 253) return false
        // No whitespace / control
        if (h.any { it.isWhitespace() || it.code < 32 }) return false
        if (h.any { it in " !@#$%^&*()+=[]{}|\\;\"'<>?,/" }) return false
        // IPv4
        if (Regex("""^\d{1,3}(?:\.\d{1,3}){3}$""").matches(h)) {
            return h.split('.').all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }
        }
        // Hostname labels
        if (!Regex("""^[A-Za-z0-9]([A-Za-z0-9._-]*[A-Za-z0-9])?$""").matches(h) &&
            !Regex("""^[A-Za-z0-9._-]+$""").matches(h)
        ) {
            return false
        }
        if (h.startsWith('.') || h.endsWith('.') || h.startsWith('-')) return false
        return true
    }
}
