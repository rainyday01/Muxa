package app.mtmux.core.data.export

import app.mtmux.core.data.db.SnippetEntity
import app.mtmux.core.model.Host
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * Password-protected export of hosts and snippets (AND-107 / AND-091).
 * Passwords and private keys are included only when the user opts in; the
 * whole bundle is AES-GCM encrypted with a PBKDF2 key from the export password.
 */
object ConfigExport {

    const val MIN_PASSWORD_LENGTH = 8
    private const val PBKDF2_ROUNDS = 120_000

    /** Secrets for one host; null fields are absent. */
    data class HostSecrets(
        val password: String? = null,
        val privateKeyPem: String? = null,
        val passphrase: String? = null,
    ) {
        val isEmpty: Boolean get() = password == null && privateKeyPem == null && passphrase == null
    }

    data class Bundle(
        val hosts: List<Host>,
        val snippets: List<SnippetEntity>,
        /** Host id → secrets, only for exports made with secrets included. */
        val secrets: Map<String, HostSecrets> = emptyMap(),
    )

    class WrongPasswordException : Exception("wrong password or damaged data")

    private val random = SecureRandom()

    fun exportJson(bundle: Bundle): String {
        val root = JSONObject()
        root.put("version", 2)
        root.put("app", "muxa")
        val hostsArr = JSONArray()
        bundle.hosts.forEach { h ->
            val o = JSONObject()
            o.put("id", h.id)
            o.put("alias", h.alias)
            o.put("hostname", h.hostname)
            o.put("port", h.port)
            o.put("username", h.username)
            o.put("transport", h.transport.name)
            bundle.secrets[h.id]?.takeIf { !it.isEmpty }?.let { sec ->
                sec.password?.let { o.put("password", it) }
                sec.privateKeyPem?.let { o.put("privateKeyPem", it) }
                sec.passphrase?.let { o.put("keyPassphrase", it) }
            }
            hostsArr.put(o)
        }
        root.put("hosts", hostsArr)
        val snArr = JSONArray()
        bundle.snippets.forEach { s ->
            snArr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("body", s.body)
                    .put("tags", s.tagsCsv)
                    .put("runOnSelect", s.runOnSelect),
            )
        }
        root.put("snippets", snArr)
        return root.toString(2)
    }

    fun encrypt(plaintext: String, password: CharArray): String {
        require(password.size >= MIN_PASSWORD_LENGTH) { "password too short" }
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val b64 = Base64.getEncoder()
        val pack = JSONObject()
        pack.put("v", 1)
        pack.put("app", "muxa")
        pack.put("salt", b64.encodeToString(salt))
        pack.put("iv", b64.encodeToString(iv))
        pack.put("ct", b64.encodeToString(ct))
        return pack.toString()
    }

    /** @throws WrongPasswordException when the password does not open [blob]. */
    fun decrypt(blob: String, password: CharArray): String {
        require(password.isNotEmpty()) { "password empty" }
        val pack = JSONObject(blob.trim())
        val salt = decodeB64(pack.getString("salt"))
        val iv = decodeB64(pack.getString("iv"))
        val ct = decodeB64(pack.getString("ct"))
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val plain = try {
            cipher.doFinal(ct)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        }
        return String(plain, StandardCharsets.UTF_8)
    }

    fun parseImport(json: String): Bundle {
        val root = JSONObject(json)
        val hosts = mutableListOf<Host>()
        val secrets = mutableMapOf<String, HostSecrets>()
        val hostsArr = root.optJSONArray("hosts") ?: JSONArray()
        for (i in 0 until hostsArr.length()) {
            val o = hostsArr.getJSONObject(i)
            val id = o.optString("id").ifBlank { java.util.UUID.randomUUID().toString() }
            hosts += Host(
                id = id,
                alias = o.getString("alias"),
                hostname = o.getString("hostname"),
                port = o.optInt("port", 22),
                username = o.getString("username"),
            )
            val sec = HostSecrets(
                password = o.optStringOrNull("password"),
                privateKeyPem = o.optStringOrNull("privateKeyPem"),
                passphrase = o.optStringOrNull("keyPassphrase"),
            )
            if (!sec.isEmpty) secrets[id] = sec
        }
        val snippets = mutableListOf<SnippetEntity>()
        val snArr = root.optJSONArray("snippets") ?: JSONArray()
        for (i in 0 until snArr.length()) {
            val o = snArr.getJSONObject(i)
            snippets += SnippetEntity(
                id = o.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                name = o.getString("name"),
                body = o.getString("body"),
                tagsCsv = o.optString("tags", ""),
                runOnSelect = o.optBoolean("runOnSelect", false),
            )
        }
        return Bundle(hosts, snippets, secrets)
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key).takeIf { it.isNotEmpty() } else null

    /** Pasted blobs may pick up line breaks or spaces. */
    private fun decodeB64(s: String): ByteArray = Base64.getDecoder().decode(s.filterNot { it.isWhitespace() })

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, PBKDF2_ROUNDS, 256)
        try {
            return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
