package app.mtmux.mosh

import android.content.Context
import app.mtmux.core.model.locale.AppLocale
import app.mtmux.mosh.crypto.MoshCrypto
import app.mtmux.mosh.net.Fragment
import app.mtmux.mosh.net.MoshDirection
import app.mtmux.mosh.net.MoshPacket
import app.mtmux.mosh.transport.FragmentAssembly
import app.mtmux.mosh.transport.Fragmenter
import app.mtmux.mosh.transport.HostMessageParser
import app.mtmux.mosh.transport.TransportInstruction
import app.mtmux.mosh.transport.UserMessageBuilder
import app.mtmux.ssh.ConnectionManager
import app.mtmux.ssh.TmuxControl
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Real Mosh client (AND-060/061):
 * 1. SSH exec mosh-server with forced command
 * 2. Parse MOSH CONNECT  ← only proves server process started (TCP/SSH path)
 * 3. UDP + AES-OCB + zlib-compressed transport instructions
 * 4. Hoststring diffs → raw terminal bytes for Termux TerminalSession
 *
 * Step 2 alone must never be treated as "Mosh ready" — UDP may be firewalled.
 */
class UdpMoshClient(
    private val connections: ConnectionManager,
    private val appContext: Context,
) : MoshClient {
    private fun s(id: Int, vararg args: Any): String = AppLocale.getString(appContext, id, *args)

    override suspend fun connect(
        params: MoshConnectParams,
        remoteCommand: String,
        cols: Int,
        rows: Int,
        scope: CoroutineScope,
    ): Result<MoshSession> = withContext(Dispatchers.IO) {
        runCatching {
            val boot = bootstrap(params, remoteCommand, cols, rows)
            UdpMoshSession(
                hostId = params.hostId,
                hostname = params.hostname,
                udpPort = boot.port,
                key = boot.key,
                cols = cols,
                rows = rows,
                scope = scope,
            ).also { it.start() }
        }
    }

    /**
     * Short-lived mosh-server + one UDP exchange.
     * Reports concrete failure: no MOSH CONNECT vs handshake OK but UDP silent.
     */
    override suspend fun probeUdp(
        params: MoshConnectParams,
        timeoutMs: Long,
    ): MoshUdpProbeResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        try {
            // sleep keeps server alive for the UDP window; exits alone afterwards
            val boot = bootstrap(params, "sleep 15", cols = 80, rows = 24)
            val job = SupervisorJob()
            val scope = CoroutineScope(Dispatchers.IO + job)
            val session = UdpMoshSession(
                hostId = params.hostId,
                hostname = params.hostname,
                udpPort = boot.port,
                key = boot.key,
                cols = 80,
                rows = 24,
                scope = scope,
            ).also { it.start() }
            val deadline = System.currentTimeMillis() + timeoutMs.coerceIn(800L, 8_000L)
            var got = false
            while (System.currentTimeMillis() < deadline) {
                when (session.linkState.value) {
                    MoshLinkState.Connected, MoshLinkState.Syncing -> {
                        got = true
                        break
                    }
                    else -> delay(40)
                }
            }
            session.close()
            job.cancel()
            // Best-effort free the probe port (ignore errors; sleep also ends soon)
            runCatching {
                connections.exec(
                    params.hostId,
                    "fuser -k ${boot.port}/udp >/dev/null 2>&1 || true",
                )
            }
            val ms = System.currentTimeMillis() - t0
            if (got) {
                MoshUdpProbeResult(
                    ok = true,
                    port = boot.port,
                    detail = s(R.string.mosh_udp_ok, boot.port, ms),
                )
            } else {
                MoshUdpProbeResult(
                    ok = false,
                    port = boot.port,
                    detail = s(R.string.mosh_udp_fail, boot.port, timeoutMs),
                )
            }
        } catch (e: Exception) {
            val msg = e.message?.lineSequence()?.take(3)?.joinToString(" ")?.take(160)
                ?: e.javaClass.simpleName
            MoshUdpProbeResult(
                ok = false,
                port = null,
                detail = s(R.string.mosh_probe_fail, msg),
            )
        }
    }

    private suspend fun bootstrap(
        params: MoshConnectParams,
        remoteCommand: String,
        cols: Int,
        rows: Int,
    ): BootstrapResult {
        val portArg = params.portRange?.let { "-p ${it.first}:${it.last}" } ?: ""
        // -s: report socket path only via MOSH CONNECT (login shell not needed)
        // -c 256: color
        // LANG for UTF-8 TUIs
        val cmd = buildString {
            append("mosh-server new -s -c 256 ")
            if (portArg.isNotEmpty()) append(portArg).append(' ')
            append("-l LANG=en_US.UTF-8 -l LC_ALL=en_US.UTF-8 ")
            // width/height via env; mosh-server reads TIOCGWINSZ from pty when available
            append("-- ")
            append(remoteCommand)
        }
        // Prefer login-shell-ish env without MOTD delay
        val wrapped = "export TERM=xterm-256color COLUMNS=$cols LINES=$rows; $cmd 2>&1"
        val out = withTimeout(20_000) {
            connections.exec(params.hostId, wrapped).getOrThrow()
        }
        val match = CONNECT_RE.find(out)
            ?: error(
                s(
                    R.string.mosh_no_connect,
                    out.lineSequence().take(12).joinToString("\n").ifBlank { "(empty)" },
                ),
            )
        val port = match.groupValues[1].toInt()
        val key = match.groupValues[2]
        require(key.length == 22) { "invalid mosh key length ${key.length}" }
        return BootstrapResult(port, MoshCrypto.keyFromPrintable(key))
    }

    private data class BootstrapResult(val port: Int, val key: ByteArray)

    companion object {
        private val CONNECT_RE =
            Regex("""MOSH CONNECT (\d{1,5}) ([A-Za-z0-9+/]{22})""")
    }
}

class UdpMoshSession(
    override val hostId: String,
    private val hostname: String,
    private val udpPort: Int,
    key: ByteArray,
    private var cols: Int,
    private var rows: Int,
    private val scope: CoroutineScope,
) : MoshSession {

    private val crypto = MoshCrypto(key)
    private val socket = DatagramSocket().also {
        it.soTimeout = 500
        it.receiveBufferSize = 256 * 1024
        it.sendBufferSize = 64 * 1024
    }
    private val remote = InetSocketAddress(InetAddress.getByName(hostname), udpPort)

    private val _output = Channel<ByteArray>(capacity = 512)
    override val output = _output

    private val _link = MutableStateFlow(MoshLinkState.Connecting)
    override val linkState: StateFlow<MoshLinkState> = _link.asStateFlow()

    private val closed = AtomicBoolean(false)
    private val seqCounter = AtomicLong(1)
    private val fragmenter = Fragmenter()
    private val assembly = FragmentAssembly()

    // Client UserStream state numbers
    private var sentUserNum = 0L
    private var ackedUserNum = 0L
    private var lastServerStateNum = 0L
    private var lastServerAcked = 0L

    private var lastRxTimestamp = 0
    private var lastRxAtMs = 0L
    private var lastAliveAt = System.currentTimeMillis()

    private var recvJob: Job? = null
    private var tickJob: Job? = null

    fun start() {
        _link.value = MoshLinkState.Connecting
        // Initial resize so server has window size
        scope.launch(Dispatchers.IO) {
            sendUserDiff(UserMessageBuilder.resize(cols, rows))
        }
        recvJob = scope.launch(Dispatchers.IO) { recvLoop() }
        tickJob = scope.launch(Dispatchers.IO) { tickLoop() }
    }

    override suspend fun write(data: ByteArray) {
        if (closed.get() || data.isEmpty()) return
        withContext(Dispatchers.IO) {
            sendUserDiff(UserMessageBuilder.keystrokes(data))
        }
    }

    override suspend fun resize(cols: Int, rows: Int) {
        if (cols < 20 || rows < 5) return
        this.cols = cols
        this.rows = rows
        withContext(Dispatchers.IO) {
            sendUserDiff(UserMessageBuilder.resize(cols, rows))
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        _link.value = MoshLinkState.Closed
        recvJob?.cancel()
        tickJob?.cancel()
        runCatching { socket.close() }
        _output.close()
    }

    private suspend fun recvLoop() {
        val buf = ByteArray(2048)
        while (scope.isActive && !closed.get()) {
            try {
                val dp = DatagramPacket(buf, buf.size)
                socket.receive(dp)
                lastAliveAt = System.currentTimeMillis()
                if (_link.value == MoshLinkState.Connecting || _link.value == MoshLinkState.Syncing) {
                    _link.value = MoshLinkState.Connected
                }
                handleWire(buf.copyOf(dp.length))
            } catch (_: java.net.SocketTimeoutException) {
                // idle
            } catch (_: Exception) {
                if (closed.get()) break
                _link.value = MoshLinkState.Degraded
            }
        }
    }

    private suspend fun tickLoop() {
        while (scope.isActive && !closed.get()) {
            delay(50)
            val idle = System.currentTimeMillis() - lastAliveAt
            when {
                idle > 15_000 && _link.value == MoshLinkState.Connected -> {
                    _link.value = MoshLinkState.Syncing
                    // keepalive empty ack
                    sendAckOnly()
                }
                idle > 40_000 && _link.value != MoshLinkState.Closed -> {
                    _link.value = MoshLinkState.Degraded
                }
                idle < 3_000 && _link.value == MoshLinkState.Syncing -> {
                    _link.value = MoshLinkState.Connected
                }
            }
            // periodic ack while active
            if (System.currentTimeMillis() - lastAliveAt < 5_000) {
                sendAckOnly()
            }
        }
    }

    private fun handleWire(wire: ByteArray) {
        val (nonceVal, plain) = runCatching { crypto.decrypt(wire) }.getOrElse {
            // Silent decrypt failure is common when UDP is garbage/wrong key
            return
        }
        val packet = MoshPacket.fromPlaintext(nonceVal, plain)
        if (packet.direction != MoshDirection.TO_CLIENT) return
        lastRxTimestamp = packet.timestamp
        lastRxAtMs = System.currentTimeMillis()

        val frag = Fragment.parse(packet.payload)
        val inst = assembly.add(frag) ?: return
        if (inst.protocolVersion != 2) return

        // server acks our user states
        if (inst.ackNum > ackedUserNum) ackedUserNum = inst.ackNum

        // apply host state
        if (inst.newNum == lastServerStateNum) return
        // only apply if we can (old_num known: 0 or last)
        if (inst.oldNum != 0L && inst.oldNum != lastServerStateNum &&
            inst.oldNum != lastServerAcked
        ) {
            // try apply anyway if we have empty base and old is 0
            if (inst.oldNum != 0L) return
        }

        val update = HostMessageParser.parse(inst.diff)
        var pushed = 0
        for (chunk in update.hostBytes) {
            if (chunk.isNotEmpty()) {
                _output.trySend(chunk)
                pushed += chunk.size
            }
        }
        // Complete redraws sometimes only carry resize; still mark link healthy.
        if (pushed == 0 && inst.diff.isNotEmpty()) {
            // Keep connection alive; terminal may get bytes on next state.
        }
        update.resize?.let { (w, h) ->
            cols = w
            rows = h
        }
        lastServerStateNum = inst.newNum
        lastServerAcked = inst.ackNum
    }

    private fun sendUserDiff(diff: ByteArray) {
        if (closed.get()) return
        val newNum = sentUserNum + 1
        val inst = TransportInstruction(
            protocolVersion = 2,
            oldNum = sentUserNum,
            newNum = newNum,
            ackNum = lastServerStateNum,
            throwawayNum = ackedUserNum,
            diff = diff,
            chaff = randomChaff(),
        )
        sentUserNum = newNum
        sendInstruction(inst)
    }

    private fun sendAckOnly() {
        if (closed.get()) return
        val inst = TransportInstruction(
            protocolVersion = 2,
            oldNum = sentUserNum,
            newNum = sentUserNum,
            ackNum = lastServerStateNum,
            throwawayNum = ackedUserNum,
            diff = ByteArray(0),
            chaff = randomChaff(),
        )
        sendInstruction(inst)
    }

    private fun sendInstruction(inst: TransportInstruction) {
        val frags = fragmenter.makeFragments(inst, MTU)
        for (f in frags) {
            sendFragment(f)
        }
    }

    private fun sendFragment(frag: Fragment) {
        val now = timestamp16()
        val reply = if (lastRxAtMs > 0 && System.currentTimeMillis() - lastRxAtMs < 1000) {
            lastRxTimestamp
        } else {
            0xFFFF
        }
        val seq = seqCounter.getAndIncrement()
        val packet = MoshPacket(
            seq = seq,
            direction = MoshDirection.TO_SERVER,
            timestamp = now,
            timestampReply = reply,
            payload = frag.toBytes(),
        )
        val wire = crypto.encrypt(packet.nonceValue(), packet.toPlaintext())
        val dp = DatagramPacket(wire, wire.size, remote.address, remote.port)
        socket.send(dp)
    }

    private fun randomChaff(): ByteArray {
        val n = Random.nextInt(0, 16)
        return if (n == 0) ByteArray(0) else Random.nextBytes(n)
    }

    private fun timestamp16(): Int =
        ((System.currentTimeMillis() * 1000 / 1000) and 0xFFFF).toInt() // ms low 16 — close enough

    companion object {
        private const val MTU = 500
    }
}

/** Build the remote command for mosh-server to run (tmux attach). */
fun moshTmuxCommand(sessionName: String, socketPath: String?): String {
    // mosh-server -- <command> — keep it one shell line
    val attach = TmuxControl.attachOrCreateCmd(sessionName, socketPath)
    // wrap as: bash -c '...' so mosh-server argv is a single command
    return "bash -lc " + TmuxControl.shellQuote(attach)
}
