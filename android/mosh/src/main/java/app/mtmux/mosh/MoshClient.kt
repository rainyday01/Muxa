package app.mtmux.mosh

import android.content.Context
import app.mtmux.ssh.ConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel

/**
 * Mosh transport facade (Phase D / AND-060+).
 *
 * Handshake: SSH exec `mosh-server` → parse `MOSH CONNECT <port> <key>` →
 * UDP AES-OCB session with SSP subset (hoststring → terminal bytes).
 *
 * Note: SSH returning MOSH CONNECT only means the **server process started**.
 * Usable Mosh also needs a working **UDP path** to that port (security group / NAT).
 */
interface MoshClient {
    /**
     * Bootstrap via existing SSH [ConnectionManager], then open UDP session.
     * @param remoteCommand shell command for mosh-server to run (e.g. tmux attach)
     */
    suspend fun connect(
        params: MoshConnectParams,
        remoteCommand: String,
        cols: Int,
        rows: Int,
        scope: CoroutineScope,
    ): Result<MoshSession>

    /**
     * Env-check: start a short-lived mosh-server, send one UDP packet, wait for reply.
     * Distinguishes "binary OK / SSH handshake OK" from "UDP blocked".
     */
    suspend fun probeUdp(
        params: MoshConnectParams,
        timeoutMs: Long = 1800,
    ): MoshUdpProbeResult
}

/** Result of [MoshClient.probeUdp] — for install wizard / env check UI. */
data class MoshUdpProbeResult(
    val ok: Boolean,
    /** Server UDP port from MOSH CONNECT, if handshake reached that far. */
    val port: Int? = null,
    /** Human-readable detail (success or concrete failure reason). */
    val detail: String,
)

data class MoshConnectParams(
    val hostId: String,
    val hostname: String,
    val sshPort: Int = 22,
    val username: String,
    /** UDP port range hint for mosh-server (-p low:high); null = server default */
    val portRange: IntRange? = null,
)

enum class MoshLinkState {
    Connecting,
    Connected,
    Syncing,
    Degraded,
    Closed,
}

interface MoshSession {
    val hostId: String
    val output: ReceiveChannel<ByteArray>
    val linkState: kotlinx.coroutines.flow.StateFlow<MoshLinkState>
    suspend fun write(data: ByteArray)
    suspend fun resize(cols: Int, rows: Int)
    fun close()
}

/** @deprecated name kept for DI call sites during migration */
class StubMoshClient : MoshClient {
    override suspend fun connect(
        params: MoshConnectParams,
        remoteCommand: String,
        cols: Int,
        rows: Int,
        scope: CoroutineScope,
    ): Result<MoshSession> =
        Result.failure(UnsupportedOperationException("Use UdpMoshClient"))

    override suspend fun probeUdp(
        params: MoshConnectParams,
        timeoutMs: Long,
    ): MoshUdpProbeResult =
        MoshUdpProbeResult(false, null, "Mosh client is disabled")
}

fun createMoshClient(connections: ConnectionManager, appContext: Context): MoshClient =
    UdpMoshClient(connections, appContext)
