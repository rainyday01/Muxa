package app.mtmux.core.data.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class GitHubIssueTest {

    private fun report(log: String, note: String = "键盘弹出时文字抖动") = GitHubIssue.Report(
        note = note,
        appVersion = "0.3.17",
        device = "Xiaomi 25098PN5AC",
        osVersion = "17",
        log = log,
    )

    @Test
    fun keepsTheNewestLinesWithinTheUrlLimit() {
        val log = (1..3000).joinToString("\n") { "10-30 23:28:%02d I/pty: line $it".format(it % 60) }
        val url = GitHubIssue.newIssueUrl(report(log))
        assertTrue(url.length <= GitHubIssue.MAX_URL_CHARS)
        assertTrue(url.startsWith("https://github.com/rainyday01/Muxa/issues/new?labels=bug&title="))
        val body = URLDecoder.decode(url.substringAfter("&body="), "UTF-8")
        assertTrue(body.contains("line 3000"))
        assertTrue(!body.contains("line 1\n"))
        assertTrue(body.contains("键盘弹出时文字抖动"))
        assertTrue(!url.contains("+"))
    }

    @Test
    fun titleIsTheFirstLineOfTheNote() {
        assertEquals("Bug report", GitHubIssue.title("  "))
        assertEquals("first", GitHubIssue.title("\nfirst\nsecond"))
    }
}
