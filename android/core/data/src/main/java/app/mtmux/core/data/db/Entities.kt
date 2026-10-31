package app.mtmux.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hosts")
data class HostEntity(
    @PrimaryKey val id: String,
    val alias: String,
    val hostname: String,
    val port: Int,
    val username: String,
    val transport: String,
    /** Keystore alias for private key material; never store PEM plaintext here. */
    val keystoreAlias: String? = null,
    val usesPassword: Boolean = false,
    val tmuxInstalled: Boolean? = null,
    val moshInstalled: Boolean? = null,
    val lastLatencyMs: Long? = null,
    val lastCheckedAtEpochMs: Long? = null,
    val lastMemAvailKb: Long? = null,
    val lastMemTotalKb: Long? = null,
    val lastDiskAvailKb: Long? = null,
    val lastDiskTotalKb: Long? = null,
    val lastStatsAtEpochMs: Long? = null,
)

@Entity(tableName = "snippets")
data class SnippetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val body: String,
    val tagsCsv: String = "",
    val runOnSelect: Boolean = false,
)

@Entity(tableName = "command_history")
data class CommandHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hostId: String,
    val command: String,
    val usedAtEpochMs: Long,
)

@Entity(tableName = "session_snapshots")
data class SnapshotEntity(
    @PrimaryKey val key: String, // hostId + "\t" + sessionName
    val text: String,
    val updatedAtEpochMs: Long,
)

/**
 * Last-known remote session list (local snapshot).
 * Loaded instantly on app start; live refresh only on pull/manual refresh.
 */
@Entity(tableName = "cached_sessions")
data class CachedSessionEntity(
    @PrimaryKey val key: String, // hostId + "\t" + sessionName
    val hostId: String,
    val name: String,
    val windows: Int = 1,
    val attached: Boolean = false,
    val hostAlias: String? = null,
    val socketPath: String? = null,
    val listedAtEpochMs: Long = 0L,
)
