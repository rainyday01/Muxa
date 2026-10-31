package app.mtmux.core.data.export

import android.util.Base64
import app.mtmux.core.data.db.HostEntity
import app.mtmux.core.data.db.SnippetEntity
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject

/**
 * Password-protected export of hosts metadata + snippets (AND-107 / AND-091).
 * Private keys are NOT included unless explicitly provided in [keyBlobs].
 */
object ConfigExport {

    fun exportJson(
        hosts: List<HostEntity>,
        snippets: List<SnippetEntity>,
        keyBlobs: Map<String, String> = emptyMap(),
        includeKeys: Boolean = false,
    ): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("app", "muxa")
        val hostsArr = JSONArray()
        hosts.forEach { h ->
            val o = JSONObject()
            o.put("id", h.id)
            o.put("alias", h.alias)
            o.put("hostname", h.hostname)
            o.put("port", h.port)
            o.put("username", h.username)
            o.put("transport", h.transport)
            if (includeKeys) {
                keyBlobs[h.id]?.let { o.put("privateKeyPem", it) }
            }
            hostsArr.put(o)
        }
        root.put("hosts", hostsArr)
        val snArr = JSONArray()
        snippets.forEach { s ->
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
        val salt = Random.Default.nextBytes(16)
        val iv = Random.Default.nextBytes(12)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val pack = JSONObject()
        pack.put("v", 1)
        pack.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
        pack.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
        pack.put("ct", Base64.encodeToString(ct, Base64.NO_WRAP))
        return pack.toString()
    }

    fun decrypt(blob: String, password: CharArray): String {
        val pack = JSONObject(blob)
        val salt = Base64.decode(pack.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(pack.getString("iv"), Base64.NO_WRAP)
        val ct = Base64.decode(pack.getString("ct"), Base64.NO_WRAP)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ct), StandardCharsets.UTF_8)
    }

    fun parseImport(json: String): Pair<List<HostEntity>, List<SnippetEntity>> {
        val root = JSONObject(json)
        val hosts = mutableListOf<HostEntity>()
        val hostsArr = root.optJSONArray("hosts") ?: JSONArray()
        for (i in 0 until hostsArr.length()) {
            val o = hostsArr.getJSONObject(i)
            hosts += HostEntity(
                id = o.optString("id", java.util.UUID.randomUUID().toString()),
                alias = o.getString("alias"),
                hostname = o.getString("hostname"),
                port = o.optInt("port", 22),
                username = o.getString("username"),
                transport = o.optString("transport", "AUTO"),
            )
        }
        val snippets = mutableListOf<SnippetEntity>()
        val snArr = root.optJSONArray("snippets") ?: JSONArray()
        for (i in 0 until snArr.length()) {
            val o = snArr.getJSONObject(i)
            snippets += SnippetEntity(
                id = o.optString("id", java.util.UUID.randomUUID().toString()),
                name = o.getString("name"),
                body = o.getString("body"),
                tagsCsv = o.optString("tags", ""),
                runOnSelect = o.optBoolean("runOnSelect", false),
            )
        }
        return hosts to snippets
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, 120_000, 256)
        val bytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }
}
