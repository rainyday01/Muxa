package app.mtmux.core.data.repo

import app.mtmux.core.data.db.HostDao
import app.mtmux.core.data.db.HostEntity
import app.mtmux.core.data.security.SecretStore
import app.mtmux.core.model.Host
import app.mtmux.core.model.HostResources
import app.mtmux.core.model.TransportPreference
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HostRepository(
    private val dao: HostDao,
    private val secrets: SecretStore,
) {
    fun observeHosts(): Flow<List<Host>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun get(id: String): Host? = dao.get(id)?.toModel()

    suspend fun upsert(
        host: Host,
        privateKeyPem: String? = null,
        password: CharArray? = null,
        keyPassphrase: CharArray? = null,
    ): Host {
        val id = host.id.ifBlank { UUID.randomUUID().toString() }
        val existing = dao.get(id)
        var keyAlias = existing?.keystoreAlias
        if (privateKeyPem != null) {
            keyAlias = secrets.putPrivateKeyPem(keyAlias ?: SecretStore.newAlias(), privateKeyPem)
        }
        if (password != null) {
            secrets.putPassword(id, password)
            password.fill('\u0000')
        }
        if (keyPassphrase != null) {
            secrets.putKeyPassphrase(id, keyPassphrase)
            keyPassphrase.fill('\u0000')
        }
        val entity = HostEntity(
            id = id,
            alias = host.alias,
            hostname = host.hostname,
            port = host.port,
            username = host.username,
            transport = TransportPreference.SSH.name,
            keystoreAlias = keyAlias,
            // Keep flag when editing transport-only (password not re-sent).
            usesPassword = password != null ||
                existing?.usesPassword == true ||
                host.hasPassword,
            tmuxInstalled = host.tmuxInstalled,
            moshInstalled = host.moshInstalled,
            lastLatencyMs = host.lastLatencyMs,
            lastCheckedAtEpochMs = host.lastCheckedAtEpochMs,
            lastMemAvailKb = host.lastMemAvailKb,
            lastMemTotalKb = host.lastMemTotalKb,
            lastDiskAvailKb = host.lastDiskAvailKb,
            lastDiskTotalKb = host.lastDiskTotalKb,
            lastStatsAtEpochMs = host.lastStatsAtEpochMs,
        )
        dao.upsert(entity)
        return entity.toModel()
    }

    /**
     * Adds or updates a host from a config import. An existing host keeps its
     * probe data and saved secrets unless the import carries new ones.
     */
    suspend fun importHost(
        host: Host,
        password: String? = null,
        privateKeyPem: String? = null,
        keyPassphrase: String? = null,
    ) {
        val merged = get(host.id)?.copy(
            alias = host.alias,
            hostname = host.hostname,
            port = host.port,
            username = host.username,
        ) ?: host
        upsert(
            host = merged,
            privateKeyPem = privateKeyPem,
            password = password?.toCharArray(),
            keyPassphrase = keyPassphrase?.toCharArray(),
        )
    }

    suspend fun delete(id: String) {
        val existing = dao.get(id)
        if (existing != null) {
            secrets.deleteHostSecrets(id, existing.keystoreAlias)
            dao.delete(id)
        }
    }

    /** Removes every saved host and every EncryptedSharedPreferences secret. */
    suspend fun deleteAllData() {
        for (row in dao.all()) {
            secrets.deleteHostSecrets(row.id, row.keystoreAlias)
            dao.delete(row.id)
        }
        secrets.deleteAllSecrets()
    }

    suspend fun privateKeyPem(hostId: String): String? {
        val alias = dao.get(hostId)?.keystoreAlias ?: return null
        return secrets.getPrivateKeyPem(alias)
    }

    suspend fun password(hostId: String): String? = secrets.getPassword(hostId)

    suspend fun keyPassphrase(hostId: String): String? = secrets.getKeyPassphrase(hostId)

    /** Patch only last-sampled mem/disk; ignore empty samples. */
    suspend fun updateResources(hostId: String, res: HostResources) {
        if (res.isEmpty) return
        val existing = dao.get(hostId) ?: return
        dao.upsert(
            existing.copy(
                lastMemAvailKb = res.memAvailKb ?: existing.lastMemAvailKb,
                lastMemTotalKb = res.memTotalKb ?: existing.lastMemTotalKb,
                lastDiskAvailKb = res.diskAvailKb ?: existing.lastDiskAvailKb,
                lastDiskTotalKb = res.diskTotalKb ?: existing.lastDiskTotalKb,
                lastStatsAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }
}

private fun HostEntity.toModel() = Host(
    id = id,
    alias = alias,
    hostname = hostname,
    port = port,
    username = username,
    transport = TransportPreference.SSH,
    hasPrivateKey = keystoreAlias != null,
    hasPassword = usesPassword,
    tmuxInstalled = tmuxInstalled,
    moshInstalled = moshInstalled,
    lastLatencyMs = lastLatencyMs,
    lastCheckedAtEpochMs = lastCheckedAtEpochMs,
    lastMemAvailKb = lastMemAvailKb,
    lastMemTotalKb = lastMemTotalKb,
    lastDiskAvailKb = lastDiskAvailKb,
    lastDiskTotalKb = lastDiskTotalKb,
    lastStatsAtEpochMs = lastStatsAtEpochMs,
)
