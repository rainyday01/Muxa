package app.mtmux.core.data.export

import app.mtmux.core.data.db.SnippetEntity
import app.mtmux.core.model.Host
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigExportTest {
    private val host = Host(id = "h1", alias = "box", hostname = "203.0.113.5", port = 2222, username = "alice")
    private val snippet = SnippetEntity(id = "s1", name = "logs", body = "tail -f /var/log/syslog")

    @Test
    fun roundTripWithoutSecrets() {
        val json = ConfigExport.exportJson(ConfigExport.Bundle(listOf(host), listOf(snippet)))
        val blob = ConfigExport.encrypt(json, "correct horse".toCharArray())
        val back = ConfigExport.parseImport(ConfigExport.decrypt(blob, "correct horse".toCharArray()))
        assertEquals(listOf(host), back.hosts)
        assertEquals(listOf(snippet), back.snippets)
        assertTrue(back.secrets.isEmpty())
    }

    @Test
    fun roundTripWithSecrets() {
        val sec = ConfigExport.HostSecrets(password = "pw", privateKeyPem = "-----BEGIN KEY-----", passphrase = "pp")
        val json = ConfigExport.exportJson(ConfigExport.Bundle(listOf(host), emptyList(), mapOf("h1" to sec)))
        val back = ConfigExport.parseImport(json)
        assertEquals(sec, back.secrets["h1"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyPasswordIsRejectedNotCrashing() {
        ConfigExport.encrypt("{}", CharArray(0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun shortPasswordIsRejected() {
        ConfigExport.encrypt("{}", "short".toCharArray())
    }

    @Test(expected = ConfigExport.WrongPasswordException::class)
    fun wrongPassword() {
        val blob = ConfigExport.encrypt("{}", "password-one".toCharArray())
        ConfigExport.decrypt(blob, "password-two".toCharArray())
    }

    @Test
    fun saltAndIvDifferEachTime() {
        val a = ConfigExport.encrypt("{}", "password-one".toCharArray())
        val b = ConfigExport.encrypt("{}", "password-one".toCharArray())
        assertNotEquals(a, b)
    }

    @Test
    fun pastedBlobWithLineBreaksStillDecrypts() {
        val blob = ConfigExport.encrypt("{\"a\":1}", "password-one".toCharArray())
        val wrapped = "\n" + blob.replace("\",\"", "\",\n\"").replace("\"ct\":\"", "\"ct\":\" ") + "\n"
        assertEquals("{\"a\":1}", ConfigExport.decrypt(wrapped, "password-one".toCharArray()))
    }
}
