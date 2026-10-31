package app.mtmux.ssh

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Security
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import net.schmizz.sshj.common.SecurityUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider

/**
 * Android ships a **stripped** BouncyCastle as provider `"BC"`, missing modern
 * algorithms that OpenSSH servers negotiate by default.
 *
 * Call [ensure] once at process start (and before every SSH connect).
 *
 * Capability matrix is probed after registration so [MtmuxSshConfig] can
 * disable unavailable KEX / host-key / cipher factories instead of crashing mid-handshake.
 */
object SshCryptoBootstrap {
    @Volatile
    private var ready = false

    @Volatile
    private var capabilities: CryptoCapabilities = CryptoCapabilities()

    fun ensure() {
        if (ready) return
        synchronized(this) {
            if (ready) return
            installFullBouncyCastle()
            capabilities = probeCapabilities()
            ready = true
        }
    }

    fun capabilities(): CryptoCapabilities {
        ensure()
        return capabilities
    }

    fun isX25519Available(): Boolean = capabilities().x25519

    fun isEd25519Available(): Boolean = capabilities().ed25519

    fun isEcdhNistAvailable(): Boolean = capabilities().ecdhNistP256

    private fun installFullBouncyCastle() {
        try {
            // Remove Android's incomplete BC (and any prior insert).
            while (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) != null) {
                Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            }
            Security.insertProviderAt(BouncyCastleProvider(), 1)
            SecurityUtils.setSecurityProvider(BouncyCastleProvider.PROVIDER_NAME)
            SecurityUtils.setRegisterBouncyCastle(true)
        } catch (_: Throwable) {
            // Fall through; probe will report what works via AndroidOpenSSL/Conscrypt.
        }
    }

    private fun probeCapabilities(): CryptoCapabilities {
        val bc = BouncyCastleProvider.PROVIDER_NAME
        return CryptoCapabilities(
            x25519 = canKeyAgreement("X25519", bc) || canKeyAgreement("X25519", null),
            ecdhNistP256 = canKeyAgreement("ECDH", bc) || canKeyAgreement("ECDH", null),
            ed25519 = canKeyFactory("Ed25519", bc) ||
                canKeyFactory("EdDSA", bc) ||
                canSignature("Ed25519", bc) ||
                canSignature("NONEwithEdDSA", null) ||
                // net.i2p.crypto eddsa is used by sshj for pure-Java Ed25519
                runCatching {
                    Class.forName("net.i2p.crypto.eddsa.EdDSASecurityProvider")
                    true
                }.getOrDefault(false),
            rsa = canKeyFactory("RSA", null),
            ecdsa = canKeyFactory("EC", null) || canKeyFactory("EC", bc),
            dsa = canKeyFactory("DSA", null),
            aesGcm = canCipher("AES/GCM/NoPadding", null) || canCipher("AES/GCM/NoPadding", bc),
            aesCtr = canCipher("AES/CTR/NoPadding", null) || canCipher("AES/CTR/NoPadding", bc),
            chacha20Poly1305 = canCipher("ChaCha20-Poly1305", null) ||
                canCipher("ChaCha20-Poly1305", bc) ||
                canCipher("ChaCha20", bc),
            hmacSha2 = canMac("HmacSHA256", null),
            sha256 = canDigest("SHA-256", null),
            providerNames = Security.getProviders().map { it.name },
        )
    }

    private fun canKeyAgreement(alg: String, provider: String?): Boolean = runCatching {
        if (provider != null) KeyAgreement.getInstance(alg, provider)
        else KeyAgreement.getInstance(alg)
        true
    }.getOrDefault(false)

    private fun canKeyFactory(alg: String, provider: String?): Boolean = runCatching {
        if (provider != null) KeyFactory.getInstance(alg, provider)
        else KeyFactory.getInstance(alg)
        true
    }.getOrDefault(false)

    private fun canSignature(alg: String, provider: String?): Boolean = runCatching {
        if (provider != null) Signature.getInstance(alg, provider)
        else Signature.getInstance(alg)
        true
    }.getOrDefault(false)

    private fun canCipher(alg: String, provider: String?): Boolean = runCatching {
        if (provider != null) Cipher.getInstance(alg, provider)
        else Cipher.getInstance(alg)
        true
    }.getOrDefault(false)

    private fun canMac(alg: String, provider: String?): Boolean = runCatching {
        if (provider != null) Mac.getInstance(alg, provider)
        else Mac.getInstance(alg)
        true
    }.getOrDefault(false)

    private fun canDigest(alg: String, provider: String?): Boolean = runCatching {
        if (provider != null) MessageDigest.getInstance(alg, provider)
        else MessageDigest.getInstance(alg)
        true
    }.getOrDefault(false)

    /** Human-readable diagnostic (safe to log — no secrets). */
    fun diagnostics(): String {
        val c = capabilities()
        return buildString {
            appendLine("SSH crypto providers: ${c.providerNames.joinToString()}")
            appendLine("X25519/curve25519 KEX: ${c.x25519}")
            appendLine("ECDH NIST: ${c.ecdhNistP256}")
            appendLine("Ed25519 keys: ${c.ed25519}")
            appendLine("RSA keys: ${c.rsa}")
            appendLine("ECDSA keys: ${c.ecdsa}")
            appendLine("DSA keys: ${c.dsa}")
            appendLine("AES-GCM: ${c.aesGcm} AES-CTR: ${c.aesCtr} ChaCha20-Poly1305: ${c.chacha20Poly1305}")
            appendLine("HMAC-SHA2: ${c.hmacSha2}")
        }
    }
}

data class CryptoCapabilities(
    val x25519: Boolean = false,
    val ecdhNistP256: Boolean = false,
    val ed25519: Boolean = false,
    val rsa: Boolean = false,
    val ecdsa: Boolean = false,
    val dsa: Boolean = false,
    val aesGcm: Boolean = false,
    val aesCtr: Boolean = false,
    val chacha20Poly1305: Boolean = false,
    val hmacSha2: Boolean = false,
    val sha256: Boolean = false,
    val providerNames: List<String> = emptyList(),
)
