package app.mtmux.core.model

import kotlinx.serialization.Serializable

/** Stable id for a saved SSH host. */
@JvmInline
value class HostId(val value: String)

@JvmInline
value class SessionId(val value: String)

@Serializable
enum class TransportPreference {
    SSH,
    /** Legacy stored value; always resolved to SSH. */
    MOSH,
    /** Legacy stored value; always resolved to SSH. */
    AUTO,
    ;

    val resolved: TransportPreference get() = SSH
}

@Serializable
enum class ThemeRole {
    SYSTEM_DARK,
    SYSTEM_LIGHT,
    PERSONAL,
}

@Serializable
enum class AppearanceMode {
    SYSTEM,
    LIGHT,
    DARK,
    CUSTOM,
}

/** In-app UI language. SYSTEM follows the device. */
@Serializable
enum class AppLanguage {
    SYSTEM,
    CHINESE,
    ENGLISH,
    ;

    val prefValue: String
        get() = when (this) {
            SYSTEM -> "system"
            CHINESE -> "zh"
            ENGLISH -> "en"
        }

    companion object {
        fun fromPref(raw: String?): AppLanguage = when (raw?.lowercase()) {
            "zh", "zh-cn", "chinese" -> CHINESE
            "en", "english" -> ENGLISH
            "system" -> SYSTEM
            else -> ENGLISH
        }
    }
}

@Serializable
data class Host(
    val id: String,
    val alias: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val transport: TransportPreference = TransportPreference.SSH,
    val hasPrivateKey: Boolean = false,
    /** True when a password is stored for this host (edit UI may leave password blank). */
    val hasPassword: Boolean = false,
    val tmuxInstalled: Boolean? = null,
    val moshInstalled: Boolean? = null,
    val lastLatencyMs: Long? = null,
    val lastCheckedAtEpochMs: Long? = null,
    /** Free memory (KiB) at last successful SSH. */
    val lastMemAvailKb: Long? = null,
    val lastMemTotalKb: Long? = null,
    /** Free space on `/` (KiB) at last successful SSH. */
    val lastDiskAvailKb: Long? = null,
    val lastDiskTotalKb: Long? = null,
    val lastStatsAtEpochMs: Long? = null,
) {
    val lastResources: HostResources
        get() = HostResources(
            memAvailKb = lastMemAvailKb,
            memTotalKb = lastMemTotalKb,
            diskAvailKb = lastDiskAvailKb,
            diskTotalKb = lastDiskTotalKb,
        )
}

@Serializable
data class TmuxSession(
    val hostId: String,
    val name: String,
    val windows: Int = 1,
    /**
     * tmux `#{session_attached}`: whether *any* client is attached on the server.
     * UI live-connection green/yellow uses app-side [liveConnected], not this alone.
     */
    val attached: Boolean = false,
    val createdAt: String? = null,
    val lastActivity: String? = null,
    val snapshotText: String? = null,
    /**
     * Epoch ms for the card corner chip (last user-driven snapshot:
     * leave or explicit refresh). Background recapture must not bump this.
     */
    val snapshotUpdatedAtEpochMs: Long? = null,
    /** Display alias for UI; defaults handled in UI if null. */
    val hostAlias: String? = null,
    /**
     * Absolute path of the tmux server socket this session lives on.
     * Needed when TMPDIR / TMUX_TMPDIR / XDG_RUNTIME_DIR differ across SSH clients
     * (app vs Termius/desktop), which otherwise creates isolated "parallel universes".
     */
    val socketPath: String? = null,
) {
    fun key(): String = "$hostId\t$name"
}

@Serializable
data class Snippet(
    val id: String,
    val name: String,
    val body: String,
    val tags: List<String> = emptyList(),
    val runOnSelect: Boolean = false,
)

enum class SuggestionSource {
    SNIPPET,
    HISTORY,
    BUILTIN,
    PATH,
}

data class Suggestion(
    val id: String,
    val label: String,
    val insertion: String,
    val source: SuggestionSource,
    val runAfterInsert: Boolean = false,
)

/** Sessions list layout (AND-104). */
@Serializable
enum class SessionListMode {
    /** Group cards under each host header. */
    GroupedByHost,
    /** Flat list sorted by last opened (then name). */
    RecentFlat,
}

enum class ConnectionState {
    Idle,
    Connecting,
    Ready,
    Degraded,
    Reconnecting,
    Failed,
}

data class ConnectionStatus(
    val hostId: String,
    val state: ConnectionState,
    val transport: TransportPreference? = null,
    val message: String? = null,
)
