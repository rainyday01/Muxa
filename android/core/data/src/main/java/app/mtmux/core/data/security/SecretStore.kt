package app.mtmux.core.data.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import java.util.UUID

/**
 * Stores SSH private keys and passwords via EncryptedSharedPreferences backed by Android Keystore.
 * Never log returned values.
 */
class SecretStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "mtmux_secrets",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun putPrivateKeyPem(alias: String = newAlias(), pem: String): String {
        prefs.edit().putString(keyAlias(alias), pem).apply()
        return alias
    }

    fun getPrivateKeyPem(alias: String): String? = prefs.getString(keyAlias(alias), null)

    fun putPassword(hostId: String, password: CharArray) {
        // CharArray → String only at boundary; prefer clearing caller array after.
        prefs.edit().putString(passKey(hostId), String(password)).apply()
    }

    fun getPassword(hostId: String): String? = prefs.getString(passKey(hostId), null)

    fun putKeyPassphrase(hostId: String, passphrase: CharArray) {
        prefs.edit().putString(passphraseKey(hostId), String(passphrase)).apply()
    }

    fun getKeyPassphrase(hostId: String): String? = prefs.getString(passphraseKey(hostId), null)

    fun deleteHostSecrets(hostId: String, keystoreAlias: String?) {
        prefs.edit()
            .remove(passKey(hostId))
            .remove(passphraseKey(hostId))
            .apply {
                if (keystoreAlias != null) remove(keyAlias(keystoreAlias))
            }
            .apply()
    }

    /** Wipes every Muxa EncryptedSharedPreferences secret (keys, passwords, passphrases). */
    fun deleteAllSecrets() {
        prefs.edit().clear().apply()
    }

    private fun keyAlias(alias: String) = "key_$alias"
    private fun passKey(hostId: String) = "pass_$hostId"
    private fun passphraseKey(hostId: String) = "kpp_$hostId"

    companion object {
        fun newAlias(): String = "k_" + UUID.randomUUID().toString().replace("-", "").take(16)

        /** Sanity: Keystore available on device. */
        fun isKeystoreAvailable(): Boolean = runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            true
        }.getOrDefault(false)
    }
}
