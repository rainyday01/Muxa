package app.mtmux.ssh

import java.io.File
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType

/**
 * Persists accepted host keys (AND-022).
 * Line format: `host|port|type|base64Key|fingerprint`
 */
class KnownHostsStore(private val file: File) {

    private val accepted = ConcurrentHashMap<String, HostKeyRecord>()

    init {
        load()
    }

    fun check(hostname: String, port: Int, key: PublicKey): HostKeyCheck {
        val type = KeyType.fromKey(key).toString()
        val keyBytes = key.encoded
        val fp = openSSHFingerprint(key)
        val existing = accepted[keyOf(hostname, port)]
        return when {
            existing == null -> HostKeyCheck.Unknown(hostname, port, type, fp, keyBytes, key)
            existing.keyBase64 != Base64.getEncoder().encodeToString(keyBytes) ->
                HostKeyCheck.Mismatch(
                    hostname = hostname,
                    port = port,
                    expectedFingerprint = existing.fingerprintSha256,
                    actualFingerprint = fp,
                    keyType = type,
                    keyBytes = keyBytes,
                    key = key,
                )
            else -> HostKeyCheck.Ok
        }
    }

    fun accept(
        hostname: String,
        port: Int,
        keyType: String,
        keyBytes: ByteArray,
        fingerprint: String? = null,
    ) {
        val rec = HostKeyRecord(
            hostname = hostname,
            port = port,
            keyType = keyType,
            keyBase64 = Base64.getEncoder().encodeToString(keyBytes),
            fingerprintSha256 = fingerprint ?: fingerprintSha256(keyBytes),
        )
        accepted[keyOf(hostname, port)] = rec
        persist()
    }

    fun accept(check: HostKeyCheck.Unknown) {
        accept(check.hostname, check.port, check.keyType, check.keyBytes, check.fingerprint)
    }

    fun acceptMismatchReplace(check: HostKeyCheck.Mismatch) {
        accept(
            check.hostname,
            check.port,
            check.keyType,
            check.keyBytes,
            check.actualFingerprint,
        )
    }

    fun remove(hostname: String, port: Int) {
        accepted.remove(keyOf(hostname, port))
        persist()
    }

    fun all(): List<HostKeyRecord> = accepted.values.sortedBy { it.hostname }

    private fun load() {
        if (!file.exists()) return
        file.readLines().forEach { line ->
            val p = line.split('|')
            if (p.size >= 4) {
                val rec = HostKeyRecord(
                    hostname = p[0],
                    port = p[1].toIntOrNull() ?: 22,
                    keyType = p[2],
                    keyBase64 = p[3],
                    fingerprintSha256 = p.getOrNull(4).orEmpty(),
                )
                accepted[keyOf(rec.hostname, rec.port)] = rec
            }
        }
    }

    private fun persist() {
        file.parentFile?.mkdirs()
        file.writeText(
            accepted.values.joinToString("\n") {
                listOf(it.hostname, it.port, it.keyType, it.keyBase64, it.fingerprintSha256)
                    .joinToString("|")
            },
        )
    }

    companion object {
        fun keyOf(hostname: String, port: Int) = "$hostname:$port"

        fun fingerprintSha256(keyBytes: ByteArray): String {
            val dig = MessageDigest.getInstance("SHA-256").digest(keyBytes)
            val encoded = Base64.getEncoder().withoutPadding().encodeToString(dig)
            return "SHA256:$encoded"
        }

        /**
         * OpenSSH SHA256 fingerprints hash the SSH wire blob, not the X.509
         * SPKI encoding from [PublicKey.getEncoded].
         */
        fun openSSHFingerprint(key: PublicKey): String {
            val blob = try {
                Buffer.PlainBuffer().putPublicKey(key).compactData
            } catch (_: Exception) {
                key.encoded
            }
            return fingerprintSha256(blob)
        }
    }
}

data class HostKeyRecord(
    val hostname: String,
    val port: Int,
    val keyType: String,
    val keyBase64: String,
    val fingerprintSha256: String,
)

sealed class HostKeyCheck {
    data object Ok : HostKeyCheck()

    data class Unknown(
        val hostname: String,
        val port: Int,
        val keyType: String,
        val fingerprint: String,
        val keyBytes: ByteArray,
        val key: PublicKey,
    ) : HostKeyCheck()

    data class Mismatch(
        val hostname: String,
        val port: Int,
        val expectedFingerprint: String,
        val actualFingerprint: String,
        val keyType: String,
        val keyBytes: ByteArray,
        val key: PublicKey,
    ) : HostKeyCheck()
}

class HostKeyNeedsConfirmation(val check: HostKeyCheck) : Exception(
    when (check) {
        is HostKeyCheck.Unknown -> "Unknown host key: ${check.fingerprint}"
        is HostKeyCheck.Mismatch -> "Host key mismatch: ${check.actualFingerprint}"
        HostKeyCheck.Ok -> "host key ok"
    },
)
