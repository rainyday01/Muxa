package app.mtmux.ssh

import android.content.Context
import app.mtmux.core.model.locale.AppLocale
import java.io.IOException
import java.io.StringReader
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.keyprovider.KeyFormat
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.keyprovider.KeyProviderUtil
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource

/**
 * Loads private keys in common formats with clearer errors for Android users.
 *
 * Supported (via sshj + BC + eddsa):
 * - OpenSSH private key v1 (`-----BEGIN OPENSSH PRIVATE KEY-----`) — RSA / ECDSA / Ed25519
 * - PKCS#8 PEM (`-----BEGIN PRIVATE KEY-----` / `ENCRYPTED PRIVATE KEY`)
 * - Classic PEM RSA/EC/DSA (`-----BEGIN RSA PRIVATE KEY-----` etc.)
 * - PuTTY .ppk (if pasted as text)
 *
 * Not supported yet: FIDO/sk-* hardware keys, PKCS#11, agent forwarding.
 */
object KeyMaterialLoader {

    fun load(
        client: SSHClient,
        privateKeyPem: String,
        passphrase: String?,
        appContext: Context,
    ): KeyProvider {
        val normalized = normalizePem(privateKeyPem)
        if (normalized.isBlank()) {
            throw KeyLoadException(AppLocale.getString(appContext, R.string.key_empty))
        }

        val format = detectFormat(normalized)
        val finder = passphraseFinder(passphrase)

        return try {
            when {
                // Encrypted OpenSSH often needs PasswordFinder path
                finder != null -> client.loadKeys(normalized, null as String?, finder)
                else -> client.loadKeys(normalized)
            }
        } catch (e: Exception) {
            // Second try: empty public key + passphrase char[]
            try {
                if (passphrase != null) {
                    client.loadKeys(normalized, passphrase.toCharArray())
                } else {
                    throw e
                }
            } catch (e2: Exception) {
                throw KeyLoadException(friendlyKeyError(appContext, format, e2, passphrase != null), e2)
            }
        }.also {
            // Touch the key early so format/type errors surface before auth.
            try {
                it.private
                it.public
            } catch (e: Exception) {
                throw KeyLoadException(friendlyKeyError(appContext, format, e, passphrase != null), e)
            }
        }
    }

    fun detectFormat(pem: String): KeyFormat = try {
        KeyProviderUtil.detectKeyFileFormat(StringReader(pem), true)
    } catch (_: Exception) {
        KeyFormat.Unknown
    }

    fun describeKeyHeader(pem: String): String {
        val line = pem.lineSequence().firstOrNull { it.contains("BEGIN") }?.trim().orEmpty()
        return line.ifBlank { "(unknown key header)" }
    }

    private fun normalizePem(raw: String): String {
        var s = raw.trim().replace("\r\n", "\n").replace('\r', '\n')
        // Some paste sources insert zero-width spaces
        s = s.replace("\u200B", "").replace("\uFEFF", "")
        // Ensure trailing newline (OpenSSH parsers are picky)
        if (s.isNotEmpty() && !s.endsWith("\n")) s += "\n"
        return s
    }

    private fun passphraseFinder(passphrase: String?): PasswordFinder? {
        if (passphrase.isNullOrEmpty()) return null
        return object : PasswordFinder {
            override fun reqPassword(resource: Resource<*>?): CharArray = passphrase.toCharArray()
            override fun shouldRetry(resource: Resource<*>?): Boolean = false
        }
    }

    private fun friendlyKeyError(
        appContext: Context,
        format: KeyFormat,
        e: Exception,
        hadPassphrase: Boolean,
    ): String {
        val msg = generateSequence<Throwable>(e) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" | ")
        val lower = msg.lowercase()
        fun s(id: Int, vararg args: Any) = AppLocale.getString(appContext, id, *args)

        return when {
            lower.contains("passphrase") || lower.contains("password") ||
                lower.contains("encrypted") || lower.contains("bad decrypt") ||
                lower.contains("cannot decrypt") ->
                if (hadPassphrase) s(R.string.key_bad_passphrase)
                else s(R.string.key_need_passphrase)

            lower.contains("ed25519") || lower.contains("eddsa") ->
                s(R.string.key_ed25519)

            lower.contains("ecdsa") || lower.contains("ec key") ->
                s(R.string.key_ecdsa)

            lower.contains("dsa") || lower.contains("dss") ->
                s(R.string.key_dsa)

            lower.contains("rsa") && lower.contains("too short") ->
                s(R.string.key_rsa_short)

            format == KeyFormat.Unknown ->
                s(R.string.key_unknown_format)

            lower.contains("x25519") || lower.contains("no such algorithm") ->
                s(R.string.key_missing_algo, msg)

            else -> s(
                R.string.key_load_fail,
                format.toString(),
                e.message ?: msg.ifBlank { e.javaClass.simpleName },
            )
        }
    }
}

class KeyLoadException(message: String, cause: Throwable? = null) : IOException(message, cause)
