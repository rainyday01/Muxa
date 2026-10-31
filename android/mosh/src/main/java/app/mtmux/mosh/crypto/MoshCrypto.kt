package app.mtmux.mosh.crypto

import java.nio.ByteBuffer
import java.util.Base64
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.OCBBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter

/**
 * Mosh session crypto: AES-128-OCB, 12-byte nonce, 16-byte tag.
 * Wire format matches mosh `Session::encrypt/decrypt`:
 *   8-byte nonce suffix || ciphertext||tag
 */
class MoshCrypto(private val key: ByteArray) {
    init {
        require(key.size == 16) { "mosh key must be 16 bytes" }
    }

    /** Encode plaintext with full 12-byte nonce; return wire bytes (8 + ct+tag). */
    fun encrypt(nonceVal: Long, plaintext: ByteArray): ByteArray {
        val nonce = nonce12(nonceVal)
        val cipher = ocb(forEncryption = true)
        cipher.init(true, AEADParameters(KeyParameter(key), 128, nonce, null))
        val out = ByteArray(cipher.getOutputSize(plaintext.size))
        var n = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
        n += cipher.doFinal(out, n)
        // wire: last 8 of nonce + ciphertext||tag
        val wire = ByteArray(8 + n)
        System.arraycopy(nonce, 4, wire, 0, 8)
        System.arraycopy(out, 0, wire, 8, n)
        return wire
    }

    fun decrypt(wire: ByteArray): Pair<Long, ByteArray> {
        require(wire.size >= 24) { "ciphertext too short" }
        val nonce = ByteArray(12)
        System.arraycopy(wire, 0, nonce, 4, 8)
        val nonceVal = ByteBuffer.wrap(nonce, 4, 8).long
        val ct = wire.copyOfRange(8, wire.size)
        val cipher = ocb(forEncryption = false)
        cipher.init(false, AEADParameters(KeyParameter(key), 128, nonce, null))
        val out = ByteArray(cipher.getOutputSize(ct.size))
        var n = cipher.processBytes(ct, 0, ct.size, out, 0)
        n += cipher.doFinal(out, n)
        return nonceVal to out.copyOf(n)
    }

    companion object {
        /** Mosh printable key is 22-char base64 without padding (==). */
        fun keyFromPrintable(printable: String): ByteArray {
            require(printable.length == 22) { "Key must be 22 letters long" }
            val decoded = Base64.getDecoder().decode(printable + "==")
            require(decoded.size == 16) { "Key must be 16 octets" }
            return decoded
        }

        fun nonce12(val64: Long): ByteArray {
            val buf = ByteBuffer.allocate(12)
            buf.putInt(0)
            buf.putLong(val64)
            return buf.array()
        }

        private fun ocb(forEncryption: Boolean): OCBBlockCipher {
            // two AES engines required by BC OCB
            return OCBBlockCipher(AESEngine.newInstance(), AESEngine.newInstance())
        }
    }
}
