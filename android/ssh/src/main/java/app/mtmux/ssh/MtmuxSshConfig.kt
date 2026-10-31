package app.mtmux.ssh

import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.common.Factory

/**
 * Builds an sshj [DefaultConfig] tailored to the device's crypto capabilities.
 *
 * Filters KEX / host-key / cipher factories that would crash mid-handshake on
 * Android's incomplete JCE (e.g. missing X25519). Keeps a broad, modern set
 * that matches typical OpenSSH servers.
 */
object MtmuxSshConfig {

    fun create(
        forceExcludeCurve25519: Boolean = false,
        forceExcludeChacha: Boolean = false,
    ): DefaultConfig {
        SshCryptoBootstrap.ensure()
        val caps = SshCryptoBootstrap.capabilities()
        val config = DefaultConfig()

        // --- Key exchange ---
        var kex = config.keyExchangeFactories.toList()
        if (forceExcludeCurve25519 || !caps.x25519) {
            kex = kex.filterNot { namedMatches(it, "curve25519", "z25519", "x25519") }
        }
        if (!caps.ecdhNistP256) {
            kex = kex.filterNot { namedMatches(it, "ecdh-sha2-nistp") }
        }
        if (kex.isNotEmpty()) {
            config.keyExchangeFactories = kex
        }

        // --- Host / public-key algorithms ---
        var keyAlgs = config.keyAlgorithms.toList()
        if (!caps.ed25519) {
            keyAlgs = keyAlgs.filterNot { namedMatches(it, "ed25519", "eddsa") }
        }
        if (!caps.ecdsa) {
            keyAlgs = keyAlgs.filterNot { namedMatches(it, "ecdsa") }
        }
        if (!caps.dsa) {
            keyAlgs = keyAlgs.filterNot { namedMatches(it, "ssh-dss", "-dss", "dsa") }
        }
        if (keyAlgs.isNotEmpty()) {
            config.keyAlgorithms = keyAlgs
        }

        // --- Ciphers ---
        var ciphers = config.cipherFactories.toList()
        if (forceExcludeChacha || !caps.chacha20Poly1305) {
            ciphers = ciphers.filterNot { namedMatches(it, "chacha20") }
        }
        if (!caps.aesGcm) {
            ciphers = ciphers.filterNot { namedMatches(it, "gcm") }
        }
        val strong = ciphers.filter {
            val n = factoryName(it)
            n.contains("aes") || n.contains("chacha")
        }
        when {
            strong.isNotEmpty() -> config.cipherFactories = strong
            ciphers.isNotEmpty() -> config.cipherFactories = ciphers
        }

        return config
    }

    private fun factoryName(factory: Factory.Named<*>): String = factory.name.lowercase()

    private fun namedMatches(factory: Factory.Named<*>, vararg needles: String): Boolean {
        val n = factory.name.lowercase()
        return needles.any { n.contains(it.lowercase()) }
    }
}
