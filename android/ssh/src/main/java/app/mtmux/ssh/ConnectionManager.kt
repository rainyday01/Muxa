package app.mtmux.ssh

import android.content.Context
import app.mtmux.core.model.ConnectionState
import app.mtmux.core.model.ConnectionStatus
import app.mtmux.core.model.TransportPreference
import app.mtmux.core.model.locale.AppLocale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Per-host connection reuse (AND-023/024).
 * Avoids re-auth and avoid extra ping RTT on every UI action.
 */
class ConnectionManager(
    private val engine: SshEngine,
    private val appContext: Context? = null,
) {
    private val mutex = Mutex()
    private val statuses = MutableStateFlow<Map<String, ConnectionStatus>>(emptyMap())
    private val lastParams = ConcurrentHashMap<String, SshConnectParams>()
    private val lastReadyAt = ConcurrentHashMap<String, Long>()

    fun observe(): StateFlow<Map<String, ConnectionStatus>> = statuses.asStateFlow()

    fun statusOf(hostId: String): ConnectionStatus =
        statuses.value[hostId] ?: ConnectionStatus(hostId, ConnectionState.Idle)

    /**
     * @param force force a brand-new TCP+auth even if cached ready
     */
    suspend fun connect(params: SshConnectParams, force: Boolean = false): Result<Unit> =
        mutex.withLock {
            lastParams[params.hostId] = params
            val st = statusOf(params.hostId)
            val readyAge = System.currentTimeMillis() - (lastReadyAt[params.hostId] ?: 0L)
            // Trust ready connection for 3 minutes without ping (saves 1 RTT on every enter)
            if (!force && st.state == ConnectionState.Ready && readyAge in 0..180_000L) {
                if (engine is SshjEngine && engine.isConnected(params.hostId)) {
                    return@withLock Result.success(Unit)
                }
            }
            // Soft reuse: engine reuses live client if still authenticated
            if (!force && engine is SshjEngine && engine.isConnected(params.hostId)) {
                set(params.hostId, ConnectionState.Ready, params.preferredTransport)
                lastReadyAt[params.hostId] = System.currentTimeMillis()
                return@withLock Result.success(Unit)
            }

            set(params.hostId, ConnectionState.Connecting)
            val result = engine.connect(params)
            if (result.isSuccess) {
                set(params.hostId, ConnectionState.Ready, params.preferredTransport)
                lastReadyAt[params.hostId] = System.currentTimeMillis()
            } else {
                set(
                    params.hostId,
                    ConnectionState.Failed,
                    message = result.exceptionOrNull()?.message,
                )
                lastReadyAt.remove(params.hostId)
            }
            result
        }

    suspend fun reconnect(hostId: String, attempts: Int = 2): Result<Unit> {
        val params = lastParams[hostId]
            ?: return Result.failure(IllegalStateException("no saved params for $hostId"))
        var last: Result<Unit> = Result.failure(IllegalStateException("no attempt"))
        repeat(attempts) { i ->
            val reconnectMsg = appContext?.let {
                AppLocale.getString(it, R.string.ssh_reconnect, i + 1, attempts)
            } ?: "Reconnect ${i + 1}/$attempts"
            set(hostId, ConnectionState.Reconnecting, message = reconnectMsg)
            if (i > 0) delay(300L * i)
            last = connect(params, force = true)
            if (last.isSuccess) return last
        }
        set(hostId, ConnectionState.Failed, message = last.exceptionOrNull()?.message)
        return last
    }

    suspend fun disconnect(hostId: String) = mutex.withLock {
        engine.disconnect(hostId)
        lastReadyAt.remove(hostId)
        set(hostId, ConnectionState.Idle)
    }

    suspend fun exec(hostId: String, command: String): Result<String> {
        ensureReady(hostId)?.let { return Result.failure(it) }
        val result = engine.exec(hostId, command)
        if (result.isFailure) {
            set(hostId, ConnectionState.Degraded, message = result.exceptionOrNull()?.message)
            invalidateEngine(hostId)
            // one forced reconnect + single retry
            if (lastParams.containsKey(hostId)) {
                val r = reconnect(hostId, attempts = 1)
                if (r.isSuccess) return engine.exec(hostId, command)
            }
        } else {
            lastReadyAt[hostId] = System.currentTimeMillis()
        }
        return result
    }

    /** Mark transport dead so next connect() opens a new TCP session. */
    fun invalidateEngine(hostId: String) {
        if (engine is SshjEngine) engine.invalidate(hostId)
        lastReadyAt.remove(hostId)
        set(hostId, ConnectionState.Failed, message = "transport invalidated")
    }

    /**
     * Ensure live connection; [force] tears down cached client first (after EOF/broken pipe).
     */
    suspend fun ensureConnected(params: SshConnectParams, force: Boolean = false): Result<Unit> {
        if (force) invalidateEngine(params.hostId)
        return connect(params, force = force)
    }

    suspend fun measureLatencyMs(hostId: String): Long? {
        ensureReady(hostId) ?: return null
        val t0 = System.nanoTime()
        val r = engine.exec(hostId, "true")
        if (r.isFailure) return null
        return (System.nanoTime() - t0) / 1_000_000L
    }

    private suspend fun ensureReady(hostId: String): Throwable? {
        val st = statusOf(hostId)
        if (st.state == ConnectionState.Ready || st.state == ConnectionState.Degraded) {
            if (engine is SshjEngine && engine.isConnected(hostId)) return null
        }
        if (lastParams.containsKey(hostId)) {
            val r = reconnect(hostId, attempts = 1)
            return r.exceptionOrNull()
        }
        return IllegalStateException("Host not connected: ${st.state}")
    }

    private fun set(
        hostId: String,
        state: ConnectionState,
        transport: TransportPreference? = null,
        message: String? = null,
    ) {
        val cur = statuses.value.toMutableMap()
        val prev = cur[hostId]
        cur[hostId] = ConnectionStatus(
            hostId = hostId,
            state = state,
            transport = transport ?: prev?.transport,
            message = message,
        )
        statuses.value = cur
    }
}

data class SshConnectParams(
    val hostId: String,
    val hostname: String,
    val port: Int,
    val username: String,
    val privateKeyPem: String? = null,
    val password: String? = null,
    val passphrase: String? = null,
    val preferredTransport: TransportPreference = TransportPreference.SSH,
)

interface SshEngine {
    suspend fun connect(params: SshConnectParams): Result<Unit>
    suspend fun disconnect(hostId: String)
    suspend fun exec(hostId: String, command: String): Result<String>
}
