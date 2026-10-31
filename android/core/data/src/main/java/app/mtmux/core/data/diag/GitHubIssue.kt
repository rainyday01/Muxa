package app.mtmux.core.data.diag

import java.net.URLEncoder

/**
 * Prefilled "new issue" links for the public repository. GitHub cannot take a
 * file through the URL, so the issue carries the tail of the redacted log and
 * asks for the full exported zip as an attachment.
 */
object GitHubIssue {
    const val REPO = "rainyday01/Muxa"
    const val ISSUES_URL = "https://github.com/$REPO/issues"
    const val NEW_ISSUE_URL = "$ISSUES_URL/new"

    /** GitHub rejects very long query strings; stay well under its limit. */
    const val MAX_URL_CHARS = 7_000

    data class Report(
        val note: String,
        val appVersion: String,
        val device: String,
        val osVersion: String,
        /** Already redacted. */
        val log: String,
    )

    fun title(note: String): String {
        val first = note.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        return if (first.isEmpty()) "Bug report" else first.take(80)
    }

    /** Issue body with as many trailing log lines as fit in [MAX_URL_CHARS]. */
    fun newIssueUrl(report: Report): String {
        val lines = report.log.trimEnd().lines()
        var keep = lines.size
        while (true) {
            val url = urlFor(report, lines.takeLast(keep))
            if (url.length <= MAX_URL_CHARS || keep == 0) return url
            // Shrink fast while far over, then line by line.
            keep = if (url.length > MAX_URL_CHARS * 2) keep / 2 else keep - 1
        }
    }

    private fun urlFor(report: Report, logLines: List<String>): String {
        val body = buildString {
            appendLine("### What happened")
            appendLine(report.note.ifBlank { "_(please describe)_" })
            appendLine()
            appendLine("### Environment")
            appendLine("- Muxa ${report.appVersion}")
            appendLine("- ${report.device} · Android ${report.osVersion}")
            appendLine()
            appendLine("### Diagnostic log (tail, redacted)")
            appendLine("<details><summary>${logLines.size} lines</summary>")
            appendLine()
            appendLine("```text")
            logLines.forEach { appendLine(it) }
            appendLine("```")
            appendLine("</details>")
            appendLine()
            append("Full log: attach the exported `muxa-diag-*.zip` (Settings → Diagnostics → Export log).")
        }
        return "$NEW_ISSUE_URL?labels=bug&title=${encode(title(report.note))}&body=${encode(body)}"
    }

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
