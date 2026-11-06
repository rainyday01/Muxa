package app.mtmux.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.mtmux.core.data.diag.DiagLog
import app.mtmux.core.model.TransportPreference
import app.mtmux.core.model.locale.AppLocale
import app.mtmux.core.ui.R
import app.mtmux.ssh.PtySession
import app.mtmux.ssh.SshjEngine
import app.mtmux.ssh.TmuxControl
import app.mtmux.ssh.openPtyExec
import app.mtmux.ssh.openPtyShell
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Finger scroll calls closer together than this belong to one gesture. */
private const val SCROLL_GESTURE_GAP_MS = 400L
private const val RESUME_PROBE_TIMEOUT_MS = 5_000L

/**
 * Binds remote SSH PTY bytes into a **Termux** [TerminalSession] (battle-tested VT).
 * Rendering is done by Termux [com.termux.view.TerminalView], not our hand-rolled VT.
 */
class TerminalSessionController(
    private val engine: SshjEngine,
    private val scope: CoroutineScope,
    private val appContext: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private enum class StatusPhase { Idle, Connecting, Transient, Dead }

    private var statusPhase = StatusPhase.Idle

    private fun s(id: Int, vararg args: Any): String = AppLocale.getString(appContext, id, *args)

    private fun setConnecting(id: Int, vararg args: Any) {
        statusPhase = StatusPhase.Connecting
        _status.value = s(id, *args)
    }

    private fun setTransient(id: Int, vararg args: Any) {
        statusPhase = StatusPhase.Transient
        _status.value = s(id, *args)
    }

    private fun setDeadStatus() {
        statusPhase = StatusPhase.Dead
        _status.value = s(R.string.term_disconnected_retry)
    }

    private fun clearStatus() {
        statusPhase = StatusPhase.Idle
        _status.value = ""
    }

    private val _status = MutableStateFlow("")
    /**
     * Ephemeral human status only (connecting / error / disconnected).
     * Do **not** put "SSH 123ms" here — transport + latency are separate so the
     * title bar never shows "SSH · SSH 123ms".
     */
    val status: StateFlow<String> = _status.asStateFlow()

    private val _transportLabel = MutableStateFlow("SSH")
    val transportLabel: StateFlow<String> = _transportLabel.asStateFlow()

    private val _latencyMs = MutableStateFlow<Long?>(null)
    /** Last measured RTT (ms); null when disconnected / unknown. */
    val latencyMs: StateFlow<Long?> = _latencyMs.asStateFlow()

    private val _sessionReady = MutableStateFlow(0L)
    /** Bumps when Termux session is (re)created so UI can re-attach. */
    val sessionGeneration: StateFlow<Long> = _sessionReady.asStateFlow()

    private val _linkAlive = MutableStateFlow(false)
    /** True while PTY can accept stdin (false after EOF / write fail / close). */
    val linkAlive: StateFlow<Boolean> = _linkAlive.asStateFlow()

    private var attachedHostId: String? = null

    /**
     * Hard generation for attach I/O. Bumped on prepare/close.
     * Collectors and late attachTmux results must match this or drop output —
     * Compose State alone is not reliable from IO coroutines (orphan reattach
     * painted previous session into the next buffer).
     */
    private val channelEpoch = java.util.concurrent.atomic.AtomicLong(0L)

    /**
     * Serialize PTY open. Without this, concurrent attachTmux (size-ready +
     * ON_RESUME reattach, or rapid session switch) can open two remote PTYs and
     * only keep the last ref — the first becomes a leaked orphan channel that
     * still feeds bytes / consumes server tmux clients.
     */
    private val attachMutex = Mutex()

    fun currentChannelEpoch(): Long = channelEpoch.get()

    fun bumpChannelEpoch(reason: String): Long {
        val e = channelEpoch.incrementAndGet()
        DiagLog.i("pty", "channelEpoch=$e ($reason)")
        return e
    }

    private fun epochLive(epoch: Long): Boolean = channelEpoch.get() == epoch

    private fun abortResult(why: String): Result<Nothing> =
        Result.failure(IllegalStateException("attach aborted ($why)"))

    private var pty: PtySession? = null
    private var collectJob: Job? = null
    private var writerJob: Job? = null
    private var ctrlLatch = false
    private var shiftLatch = false
    private var loggedFirstOutput = false
    /**
     * Inline tmux panes (AGy) keep history on the main buffer. The tmux *client*
     * still sends 1049h, so TerminalView thinks this is an alt TUI. Page those
     * panes via `tmux copy-mode` instead of CSI PgUp.
     */
    @Volatile private var tmuxHistoryPaging = false
    @Volatile private var tmuxCopyModeDepth = 0
    @Volatile private var lastTmuxPageMs = 0L
    /** Pane is known to be on its main screen; false until the first pull answers. */
    @Volatile private var tmuxPaneInline = false
    @Volatile private var paneHistorySignature: String? = null
    @Volatile private var lastPaneSyncMs = 0L
    private var lastAltScrollMs = 0L
    private val paneSyncRunning = java.util.concurrent.atomic.AtomicBoolean(false)
    /** Epoch captured when current collector started. */
    private var collectorEpoch: Long = -1L

    /** Last successful attach — used to re-open after screen-off / Doze kills the channel. */
    data class LastAttach(
        val hostId: String,
        val sessionName: String,
        val cols: Int,
        val rows: Int,
        val socketPath: String?,
        val transport: TransportPreference,
        val moshBinaryOk: Boolean,
        val moshUdpReady: Boolean,
        val hostname: String,
        val sshPort: Int,
        val username: String,
        val rawShell: Boolean = false,
    )
    var lastAttach: LastAttach? = null
        private set

    /**
     * FIFO queue for all PTY stdin writes.
     *
     * IME commits Chinese one code point at a time via [TerminalSession.writeCodePoint].
     * Launching a free [Dispatchers.IO] job per character reorders them (e.g.
     * 「这是一条测试消息」→「这试测一条息是消」). One consumer preserves order.
     */
    private val writeQueue = Channel<ByteArray>(Channel.UNLIMITED)

    /** Termux session owned by controller; UI attaches [com.termux.view.TerminalView] to it. */
    var termuxSession: TerminalSession? = null
        private set

    private var writeListener: TerminalSession.WriteListener? = null
    private var sizeListener: TerminalSession.SizeListener? = null
    private var lastRemoteCols = 0
    private var lastRemoteRows = 0
    /** Uptime of last successful attach — IME WINCH follow-up only while attach is fresh. */
    @Volatile private var lastAttachElapsedMs: Long = 0L

    /** TERM for next PTY allocate (xterm-256color, …). */
    var termType: String = "xterm-256color"

    /** Scrollback lines for next Termux session create. */
    var transcriptRows: Int = 1000

    /** Bytes the user typed or pasted through the terminal view (main thread). */
    var onUserInput: ((ByteArray) -> Unit)? = null

    /** Attached tmux session and socket, or null for none / the install shell. */
    fun attachedTmuxTarget(): Pair<String, String?>? =
        lastAttach?.takeIf { !it.rawShell && it.sessionName.isNotBlank() }
            ?.let { it.sessionName to it.socketPath }

    init {
        // Single writer: order of trySend == order of remote stdin.
        writerJob = scope.launch(Dispatchers.IO) {
            for (chunk in writeQueue) {
                // Skip stdin if link already torn down (leave / epoch bump left queue drained).
                if (!_linkAlive.value && pty == null) {
                    continue
                }
                runCatching {
                    val p = pty
                    if (p != null) p.writeBytes(chunk)
                    else error("no PTY")
                }.onFailure { e ->
                    // Intentional close often surfaces as "PTY closed" — not a fault toast path.
                    if (e.message?.contains("no PTY") == true ||
                        e.message?.contains("PTY closed") == true
                    ) {
                        return@onFailure
                    }
                    DiagLog.w("pty", "write fail: ${e.message}")
                    if (isTransportBroken(e)) {
                        markLinkDead("write failed: ${e.message ?: "disconnected"}")
                    }
                }
            }
        }
    }

    fun isLinkAlive(): Boolean {
        if (!_linkAlive.value) return false
        val p = pty
        return p != null && p.isOpen()
    }

    private fun markLinkAlive() {
        _linkAlive.value = true
    }

    private fun markLinkDead(reason: String) {
        if (!_linkAlive.value && pty == null) return
        DiagLog.w("pty", "link dead: $reason")
        _linkAlive.value = false
        setDeadStatus()
        _latencyMs.value = null
        // Drop half-open handles so writes stop immediately
        runCatching { pty?.close() }
        pty = null
    }

    /**
     * One-shot RTT via a lightweight remote `true` (reuses pooled SSH).
     * Safe to call on a timer while a session is attached.
     */
    suspend fun refreshLatencyMs(hostId: String? = attachedHostId): Long? {
        val id = hostId ?: return null
        if (!_linkAlive.value) return null
        val t0 = System.nanoTime()
        val r = runCatching { engine.exec(id, "true") }.getOrNull()
        if (r == null || r.isFailure) return null
        val ms = (System.nanoTime() - t0) / 1_000_000L
        _latencyMs.value = ms
        // Keep status clean — only overwrite if it was a connect-phase leftover.
        val st = _status.value
        if (statusPhase == StatusPhase.Connecting ||
            st.startsWith("SSH ") || st.contains("ms")
        ) {
            clearStatus()
        }
        return ms
    }

    /**
     * Health check after app resume. Returns false if the interactive channel is dead
     * (caller should re-auth + [attachTmux] / [attachRawShell]).
     */
    suspend fun healthCheckOnResume(sshConnected: Boolean): Boolean = withContext(Dispatchers.IO) {
        val p = pty
        if (p == null || !p.isOpen()) {
            markLinkDead("PTY gone on resume")
            return@withContext false
        }
        if (!sshConnected) {
            markLinkDead("SSH not connected on resume")
            return@withContext false
        }
        // Writes succeed on a half-open socket for a long time; only a reply proves the link.
        val hostId = lastAttach?.hostId ?: attachedHostId
        val probe = scope.async(Dispatchers.IO) {
            hostId != null && engine.exec(hostId, "true").isSuccess
        }
        if (withTimeoutOrNull(RESUME_PROBE_TIMEOUT_MS) { probe.await() } != true) {
            probe.cancel()
            markLinkDead("no reply on resume")
            return@withContext false
        }
        DiagLog.i("pty", "link alive on resume")
        markLinkAlive()
        true
    }

    /** The keep-alive window ended in the background: give the connection up on purpose. */
    fun releaseLinkInBackground() {
        markLinkDead("background keep window ended")
    }

    fun isCtrlLatched(): Boolean = ctrlLatch
    fun setCtrlLatch(v: Boolean) {
        ctrlLatch = v
    }
    fun isShiftLatched(): Boolean = shiftLatch
    fun setShiftLatch(v: Boolean) {
        shiftLatch = v
    }
    fun toggleShiftLatch() {
        shiftLatch = !shiftLatch
    }

    fun prepareSession(client: TerminalSessionClient) {
        // Invalidate any in-flight collector / attach from a previous UI session.
        closePtyOnly()
        bumpChannelEpoch("prepareSession")
        lastRemoteCols = 0
        lastRemoteRows = 0
        termuxSession?.finishIfRunning()
        val rows = transcriptRows.coerceIn(100, 50_000)
        val session = TerminalSession(client, /* transcriptRows */ rows)
        DiagLog.i("pty", "prepareSession term=$termType buffer=$rows epoch=${channelEpoch.get()}")
        writeListener = TerminalSession.WriteListener { data, offset, count ->
            if (count <= 0) return@WriteListener
            // Copy immediately: writeCodePoint reuses a small shared buffer.
            val copy = data.copyOfRange(offset, offset + count)
            if (!session.isWritingEmulatorReply()) onUserInput?.invoke(copy)
            cancelTmuxCopyModeIfNeeded()
            // Non-blocking enqueue; writerJob drains FIFO on IO.
            if (writeQueue.trySend(copy).isFailure) {
                DiagLog.w("pty", "write queue closed, drop ${copy.size}b")
            }
        }
        // TerminalView already debounces layout (IME animation). Only skip duplicates here.
        val sizeEpoch = channelEpoch.get()
        sizeListener = TerminalSession.SizeListener { columns, rows ->
            // Skip no-op and tiny ±1 row thrash (chrome settle / sub-pixel layout).
            if (columns == lastRemoteCols && rows == lastRemoteRows) return@SizeListener
            if (columns == lastRemoteCols
                && lastRemoteRows > 0
                && kotlin.math.abs(rows - lastRemoteRows) <= 1
            ) {
                DiagLog.i(
                    "pty",
                    "WINCH skip ±1 row ${lastRemoteCols}x$lastRemoteRows→${columns}x$rows",
                )
                return@SizeListener
            }
            val prevCols = lastRemoteCols
            val prevRows = lastRemoteRows
            val prev = "${prevCols}x$prevRows"
            val rowDelta = if (prevRows > 0) kotlin.math.abs(rows - prevRows) else 0
            lastRemoteCols = columns
            lastRemoteRows = rows
            scope.launch {
                // Drop WINCH if this Termux session was already replaced/closed.
                if (!epochLive(sizeEpoch)) {
                    DiagLog.w("pty", "WINCH drop stale epoch want=$sizeEpoch now=${channelEpoch.get()}")
                    return@launch
                }
                DiagLog.i("pty", "termux size WINCH ${columns}x$rows (was $prev)")
                resizeRemote(columns, rows, sizeEpoch)
                // Follow-up only right after attach: alt TUI first paint can race the
                // first IME WINCH. Repeating it on every bar/IME/panel toggle flashes
                // the window (field 0.2.0: WINCH + follow-up ~160ms later).
                val attachAge = android.os.SystemClock.uptimeMillis() - lastAttachElapsedMs
                if (rowDelta > 2 && lastAttachElapsedMs > 0L && attachAge < 2_500L) {
                    mainHandler.post {
                        if (!epochLive(sizeEpoch)) return@post
                        termuxSession?.refresh()
                    }
                    mainHandler.postDelayed({
                        if (!epochLive(sizeEpoch)) return@postDelayed
                        termuxSession?.refresh()
                        scope.launch {
                            if (epochLive(sizeEpoch)) {
                                resizeRemote(columns, rows, sizeEpoch)
                                DiagLog.i("pty", "WINCH follow-up redraw ${columns}x$rows")
                            }
                        }
                    }, 160L)
                }
            }
        }
        session.setWriteListener(writeListener)
        session.setSizeListener(sizeListener)
        termuxSession = session
        _sessionReady.value = _sessionReady.value + 1
    }

    /**
     * @param moshBinaryOk `mosh-server` installed on host.
     * @param moshUdpReady Positive UDP verification (env check or prior good session).
     *   AUTO only tries Mosh when both binary and UDP ready — never burns multi-second
     *   silent waits on firewalled hosts (e.g. cloud SG without UDP 60000–61000).
     *   Forced [TransportPreference.MOSH] still tries regardless.
     */
    suspend fun attachTmux(
        hostId: String,
        sessionName: String,
        cols: Int = 80,
        rows: Int = 24,
        socketPath: String? = null,
        transport: TransportPreference = TransportPreference.SSH,
        moshBinaryOk: Boolean = false,
        moshUdpReady: Boolean = false,
        hostname: String = "",
        sshPort: Int = 22,
        username: String = "",
        /** If set, abort when [currentChannelEpoch] no longer matches (UI left / re-prepared). */
        expectedEpoch: Long? = null,
    ): Result<AttachOutcome> = attachMutex.withLock {
        val epoch = expectedEpoch ?: channelEpoch.get()
        if (!epochLive(epoch)) {
            DiagLog.w("pty", "attachTmux abort epoch mismatch want=$epoch now=${channelEpoch.get()}")
            return@withLock abortResult("epoch")
        }
        DiagLog.i(
            "pty",
            "attachTmux hostId=$hostId session=$sessionName socket=$socketPath " +
                "transport=SSH size=${cols}x$rows epoch=$epoch",
        )
        // Do NOT set lastAttach until success — aborted mid-open must not leave a
        // resume target for a session that never owned the PTY.
        val pendingLast = LastAttach(
            hostId = hostId,
            sessionName = sessionName,
            cols = cols,
            rows = rows,
            socketPath = socketPath,
            transport = TransportPreference.SSH,
            moshBinaryOk = false,
            moshUdpReady = false,
            hostname = hostname,
            sshPort = sshPort,
            username = username,
            rawShell = false,
        )
        if (!epochLive(epoch)) {
            DiagLog.w("pty", "attachTmux pre-ssh abort epoch=$epoch now=${channelEpoch.get()}")
            closePtyOnly()
            return@withLock abortResult("epoch")
        }
        val ssh = attachSsh(hostId, sessionName, cols, rows, socketPath, epoch)
        if (ssh.isFailure) {
            val err = ssh.exceptionOrNull()
            // Quiet path when UI already left — not a real SSH fault.
            if (err?.message?.contains("aborted", ignoreCase = true) == true) {
                DiagLog.w("pty", "ssh attach aborted session=$sessionName: ${err.message}")
            } else {
                DiagLog.e("pty", "ssh attach fail session=$sessionName", err)
            }
            return@withLock Result.failure(err ?: IllegalStateException("ssh attach failed"))
        }
        if (!epochLive(epoch)) {
            DiagLog.w("pty", "ssh attach ok but epoch stale — drop session=$sessionName")
            closePtyOnly()
            return@withLock abortResult("epoch after open")
        }
        lastAttach = pendingLast
        lastAttachElapsedMs = android.os.SystemClock.uptimeMillis()
        attachedHostId = hostId
        clearStatus()
        refreshTmuxPagingHint()
        DiagLog.i("pty", "ssh attach ok session=$sessionName ${cols}x$rows")
        // Do NOT re-push open-time cols/rows after a delay.
        // Field log 0.1.99: IME opened mid-reattach → WINCH 110x46, then this delayed
        // resize(openCols, openRows) forced 110x82 again → Grok input line stayed under
        // the keyboard ("输入行没有跟着键盘往上移"). Only reassert the *latest* size
        // known from Termux sizeListener (may already be the IME footprint).
        scope.launch {
            delay(100)
            if (!epochLive(epoch)) return@launch
            val c = lastRemoteCols.takeIf { it >= 20 } ?: cols
            val r = lastRemoteRows.takeIf { it >= 5 } ?: rows
            if (epochLive(epoch)) {
                runCatching { pty?.resize(c, r) }
                DiagLog.i("pty", "post-open size confirm ${c}x$r (open was ${cols}x$rows)")
            }
            delay(150)
            if (!epochLive(epoch)) return@launch
            val c2 = lastRemoteCols.takeIf { it >= 20 } ?: c
            val r2 = lastRemoteRows.takeIf { it >= 5 } ?: r
            if (epochLive(epoch)) runCatching { pty?.resize(c2, r2) }
            if (epochLive(epoch)) refreshLatencyMs(hostId)
        }
        Result.success(AttachOutcome(transportUsed = "SSH", moshUdpFailed = false))
    }

    data class AttachOutcome(
        val transportUsed: String,
        /** True if we tried Mosh and got no terminal bytes (UDP likely blocked). */
        val moshUdpFailed: Boolean,
    )

    fun isTransportBroken(t: Throwable?): Boolean {
        val msg = generateSequence(t) { it.cause }.mapNotNull { it.message }.joinToString(" ")
        return msg.contains("EOF", ignoreCase = true) ||
            msg.contains("Broken transport", ignoreCase = true) ||
            msg.contains("Connection reset", ignoreCase = true) ||
            msg.contains("not connected", ignoreCase = true)
    }

    private suspend fun attachSsh(
        hostId: String,
        sessionName: String,
        cols: Int,
        rows: Int,
        socketPath: String?,
        epoch: Long,
    ): Result<Unit> {
        setConnecting(R.string.term_opening)
        _transportLabel.value = "SSH"
        val remote = TmuxControl.attachOrCreateCmd(sessionName, socketPath)
        return openExec(hostId, cols, rows, remote, epoch)
    }

    /**
     * Interactive login shell (install wizard).
     * @param bootstrap optional bytes written once the shell starts (e.g. install command).
     */
    suspend fun attachRawShell(
        hostId: String,
        cols: Int = 80,
        rows: Int = 24,
        bootstrap: String? = null,
    ): Result<Unit> = attachMutex.withLock {
        val epoch = channelEpoch.get()
        if (!epochLive(epoch)) return@withLock abortResult("epoch")
        setConnecting(R.string.term_open_shell)
        _transportLabel.value = "SSH"
        DiagLog.i(
            "pty",
            "attachRawShell size=${cols}x$rows term=$termType bootstrap=${bootstrap?.length ?: 0}b epoch=$epoch",
        )
        val r = openShell(hostId, cols, rows, bootstrap, epoch)
        if (r.isSuccess && epochLive(epoch)) {
            lastAttachElapsedMs = android.os.SystemClock.uptimeMillis()
            lastAttach = LastAttach(
                hostId = hostId,
                sessionName = "",
                cols = cols,
                rows = rows,
                socketPath = null,
                transport = TransportPreference.SSH,
                moshBinaryOk = false,
                moshUdpReady = false,
                hostname = "",
                sshPort = 22,
                username = "",
                rawShell = true,
            )
        }
        r
    }

    /**
     * Open remote exec PTY. Caller must hold [attachMutex].
     * Never throws for epoch races — always Result.failure with "aborted" in message.
     * (Kotlin Result.map does not catch transform exceptions; earlier error() crashed the app.)
     */
    private suspend fun openExec(
        hostId: String,
        cols: Int,
        rows: Int,
        remoteCommand: String,
        epoch: Long,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!epochLive(epoch)) {
            return@withContext abortResult("epoch before openExec")
        }
        closePtyOnly()
        loggedFirstOutput = false
        val t0 = System.currentTimeMillis()
        engine.openPtyExec(hostId, cols, rows, scope, remoteCommand, termType = termType).fold(
            onSuccess = { session ->
                if (!epochLive(epoch)) {
                    runCatching { session.close() }
                    DiagLog.w(
                        "pty",
                        "openExec stale epoch after PTY open want=$epoch now=${channelEpoch.get()} — closed orphan",
                    )
                    return@fold abortResult("epoch after open")
                }
                // Defensive: drop any leftover handle before taking ownership.
                if (pty != null) closePtyOnly()
                pty = session
                startPtyCollector(session, epoch)
                markLinkAlive()
                lastRemoteCols = cols
                lastRemoteRows = rows
                // close() does not take attachMutex — leave can bump epoch between the
                // check above and assign. Re-verify and tear down if we lost the race.
                if (!epochLive(epoch)) {
                    DiagLog.w(
                        "pty",
                        "openExec lost race after assign want=$epoch now=${channelEpoch.get()} — closePtyOnly",
                    )
                    closePtyOnly()
                    return@fold abortResult("epoch race after assign")
                }
                clearStatus()
                DiagLog.i(
                    "pty",
                    "collector started term=$termType cmd len=${remoteCommand.length} " +
                        "epoch=$epoch openMs=${System.currentTimeMillis() - t0}",
                )
                Result.success(Unit)
            },
            onFailure = { Result.failure(it) },
        )
    }

    /** Open login shell. Caller must hold [attachMutex]. */
    private suspend fun openShell(
        hostId: String,
        cols: Int,
        rows: Int,
        bootstrap: String? = null,
        epoch: Long = channelEpoch.get(),
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!epochLive(epoch)) return@withContext abortResult("epoch before openShell")
        closePtyOnly()
        loggedFirstOutput = false
        engine.openPtyShell(hostId, cols, rows, scope, bootstrap, termType = termType).fold(
            onSuccess = { session ->
                if (!epochLive(epoch)) {
                    runCatching { session.close() }
                    DiagLog.w(
                        "pty",
                        "openShell stale epoch want=$epoch now=${channelEpoch.get()} — closed orphan",
                    )
                    return@fold abortResult("epoch after open")
                }
                if (pty != null) closePtyOnly()
                pty = session
                startPtyCollector(session, epoch)
                markLinkAlive()
                lastRemoteCols = cols
                lastRemoteRows = rows
                if (!epochLive(epoch)) {
                    DiagLog.w("pty", "openShell lost race after assign — closePtyOnly")
                    closePtyOnly()
                    return@fold abortResult("epoch race after assign")
                }
                setTransient(R.string.term_shell_ready)
                DiagLog.i("pty", "openShell ok hostId=$hostId ${cols}x$rows")
                Result.success(Unit)
            },
            onFailure = { e ->
                DiagLog.e("pty", "openShell fail hostId=$hostId", e)
                setTransient(R.string.term_shell_fail, e.message ?: e.javaClass.simpleName)
                Result.failure(e)
            },
        )
    }

    private fun startPtyCollector(session: PtySession, epoch: Long) {
        // Always cancel prior job — openExec closePtyOnly should have done this, but
        // concurrent/racy paths must not leave two consumers on different sessions.
        collectJob?.cancel()
        collectorEpoch = epoch
        collectJob = scope.launch(Dispatchers.IO) {
            try {
                session.output.consumeEach { bytes ->
                    // Drop + stop if UI already prepared a new session / closed this channel.
                    if (!epochLive(epoch) || pty !== session) {
                        DiagLog.w(
                            "pty",
                            "collector drop stale epoch=$epoch now=${channelEpoch.get()} " +
                                "ptyMatch=${pty === session} — stop",
                        )
                        return@launch
                    }
                    if (!loggedFirstOutput && bytes.isNotEmpty()) {
                        loggedFirstOutput = true
                        val preview = bytes.decodeToString(0, minOf(bytes.size, 80))
                            .replace("\u001B", "\\e")
                            .replace("\n", "\\n")
                            .replace("\r", "\\r")
                        DiagLog.i("pty", "first output bytes=${bytes.size} preview=$preview")
                        // Alt TUI (1049h): first paint often races IME WINCH; re-push
                        // current size so Grok can place the bottom input after resize.
                        if (preview.contains("1049h") || preview.contains("?1049h")) {
                            val c0 = lastRemoteCols
                            val r0 = lastRemoteRows
                            if (c0 >= 20 && r0 >= 5) {
                                mainHandler.postDelayed({
                                    if (!epochLive(epoch)) return@postDelayed
                                    DiagLog.i("pty", "alt first-paint size nudge ${c0}x$r0")
                                    scope.launch {
                                        if (epochLive(epoch)) {
                                            resizeRemote(c0, r0, epoch)
                                            forceRedraw()
                                        }
                                    }
                                }, 220L)
                            }
                        }
                    }
                    termuxSession?.appendOutput(bytes)
                }
            } finally {
                // stdout EOF / channel closed — phone sleep / NAT / server often do this.
                if (pty === session && epochLive(epoch)) {
                    markLinkDead("PTY output closed (EOF)")
                }
            }
        }
    }

    suspend fun sendText(text: String) {
        if (text.isEmpty()) return
        writeBytes(text.toByteArray(Charsets.UTF_8))
    }

    /** Clipboard paste: bracketed-paste / TUI-safe newlines, not raw Enter. */
    fun pasteText(text: String) {
        if (text.isEmpty()) return
        val em = termuxSession?.emulator
        if (em != null) {
            DiagLog.i("pty", "paste chars=${text.length} nl=${text.count { it == '\n' || it == '\r' }}")
            mainHandler.post { em.paste(text) }
            return
        }
        scope.launch { sendText(text) }
    }

    suspend fun sendKeyLabel(label: String) {
        if (label == "Ctrl") {
            ctrlLatch = !ctrlLatch
            return
        }
        val bytes: ByteArray = if (ctrlLatch && label.length == 1) {
            val c = label.lowercase()[0]
            if (c in 'a'..'z') {
                ctrlLatch = false
                byteArrayOf((c.code - 96).toByte())
            } else {
                labelToBytes(label) ?: return
            }
        } else {
            labelToBytes(label) ?: return
        }
        writeBytes(bytes)
    }

    /** Raw bytes for keyboard layout actions (chords / keyCodes). */
    suspend fun sendRawBytes(data: ByteArray) {
        if (data.isEmpty()) return
        writeBytes(data)
    }

    /**
     * Insert a compose-box payload into the remote input. False when the PTY
     * is gone, so the draft stays on screen. Does not append CR: one Enter
     * submits. A refused send is logged without the draft text.
     */
    fun trySendDraft(text: String): Boolean {
        val body = app.mtmux.core.model.ComposeHistory.payload(text)
        if (body.isEmpty() || writeQueue.isClosedForSend || !isLinkAlive()) {
            DiagLog.w(
                "pty",
                "draft kept chars=${text.length} link=${_linkAlive.value} pty=${pty != null}",
            )
            return false
        }
        val em = termuxSession?.emulator
        if (em == null) {
            DiagLog.w("pty", "draft kept chars=${body.length} no emulator")
            return false
        }
        cancelTmuxCopyModeIfNeeded()
        em.insertComposerText(body)
        DiagLog.i("pty", "draft inserted chars=${body.length}")
        return true
    }

    fun toggleCtrlLatch() {
        ctrlLatch = !ctrlLatch
    }

    /**
     * Finger page on tmux-client alt-screen. True when this session's pane keeps
     * history on the main buffer (AGy inline) — host runs `tmux copy-mode`.
     */
    fun onAltFingerScroll(up: Boolean): Boolean {
        syncPaneHistoryOnGesture()
        if (!tmuxHistoryPaging) return false
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastTmuxPageMs < 130L) return true
        lastTmuxPageMs = now
        val attach = lastAttach ?: return false
        if (attach.rawShell) return false
        val lines = 18
        if (up) {
            tmuxCopyModeDepth++
        } else {
            tmuxCopyModeDepth--
        }
        val cmd = if (!up && tmuxCopyModeDepth <= 0) {
            tmuxCopyModeDepth = 0
            TmuxControl.cancelCopyModeCmd(attach.sessionName, attach.socketPath)
        } else {
            if (tmuxCopyModeDepth < 0) tmuxCopyModeDepth = 0
            TmuxControl.scrollHistoryCmd(attach.sessionName, attach.socketPath, up, lines)
        }
        val hostId = attach.hostId
        val epoch = channelEpoch.get()
        DiagLog.i("pty", "tmux copy-mode page ${if (up) "up" else "down"} depth=$tmuxCopyModeDepth")
        scope.launch(Dispatchers.IO) {
            if (!epochLive(epoch)) return@launch
            runCatching { engine.exec(hostId, cmd) }.onFailure {
                DiagLog.w("pty", "tmux copy-mode page fail ${it.message}")
            }
        }
        return true
    }

    /** tmux pane known to be on its main screen; see [TmuxControl.paneHistoryCmd]. */
    fun isAltPaneInline(): Boolean = tmuxPaneInline

    /**
     * tmux holds the real history of an inline pane. Its lines reach the local transcript
     * only partly: a burst larger than a screen, a redraw, a window switch or a reconnect
     * leaves gaps. So a new scroll gesture pulls the pane's history again, if anything
     * arrived since the last pull.
     */
    private fun syncPaneHistoryOnGesture() {
        val now = android.os.SystemClock.uptimeMillis()
        val newGesture = now - lastAltScrollMs > SCROLL_GESTURE_GAP_MS
        lastAltScrollMs = now
        if (!newGesture) return
        val lastOutput = termuxSession?.getLastRemoteOutputAt() ?: return
        if (lastOutput <= lastPaneSyncMs) return
        syncPaneHistory()
    }

    private fun refreshTmuxPagingHint() {
        tmuxHistoryPaging = false
        tmuxCopyModeDepth = 0
        tmuxPaneInline = false
        paneHistorySignature = null
        val epoch = channelEpoch.get()
        scope.launch(Dispatchers.IO) {
            repeat(25) {
                if (loggedFirstOutput || !epochLive(epoch)) return@repeat
                delay(40)
            }
            delay(120)
            if (epochLive(epoch)) syncPaneHistory()
        }
    }

    private fun syncPaneHistory() {
        val attach = lastAttach ?: return
        if (attach.rawShell || attach.sessionName.isBlank()) return
        if (!paneSyncRunning.compareAndSet(false, true)) return
        val epoch = channelEpoch.get()
        val session = termuxSession
        val capacity = (transcriptRows - (session?.getEmulator()?.mRows ?: lastRemoteRows))
            .coerceAtLeast(1)
        scope.launch(Dispatchers.IO) {
            try {
                lastPaneSyncMs = android.os.SystemClock.uptimeMillis()
                val cmd = TmuxControl.paneHistoryCmd(
                    attach.sessionName,
                    attach.socketPath,
                    capacity,
                    paneHistorySignature,
                )
                val pane = engine.exec(attach.hostId, cmd).getOrNull()
                    ?.let(TmuxControl::parsePaneHistory)
                if (!epochLive(epoch) || lastAttach?.sessionName != attach.sessionName) return@launch
                if (pane == null) {
                    DiagLog.w("pty", "tmux scroll meta unavailable session=${attach.sessionName}")
                    return@launch
                }
                DiagLog.i(
                    "pty",
                    "tmux scroll meta alt=${pane.alternate} hist=${pane.historySize} lines=${pane.lines.size}",
                )
                tmuxPaneInline = !pane.alternate
                // An alt-screen app's own redraws refill the transcript, so that case always clears.
                if (!pane.alternate && pane.signature == paneHistorySignature) return@launch
                // A pane with history that returned none: page it on the host instead.
                tmuxHistoryPaging = !pane.alternate && pane.historySize > 0 && pane.lines.isEmpty()
                if (tmuxHistoryPaging) {
                    DiagLog.i("pty", "tmux scroll seed empty — copy-mode page fallback")
                    return@launch
                }
                // An alt-screen app pages itself, so nothing local may shadow its keys.
                val lines = if (pane.alternate) emptyArray() else pane.lines.toTypedArray()
                withContext(Dispatchers.Main) {
                    if (!epochLive(epoch) || termuxSession !== session) return@withContext
                    session?.replaceScrollback(lines)
                    paneHistorySignature = pane.signature
                    DiagLog.i("pty", "tmux scroll seeded ${lines.size} lines for local finger-follow")
                }
            } finally {
                paneSyncRunning.set(false)
            }
        }
    }

    private fun cancelTmuxCopyModeIfNeeded() {
        if (tmuxCopyModeDepth <= 0) return
        tmuxCopyModeDepth = 0
        val attach = lastAttach ?: return
        val cmd = TmuxControl.cancelCopyModeCmd(attach.sessionName, attach.socketPath)
        val hostId = attach.hostId
        scope.launch(Dispatchers.IO) {
            runCatching { engine.exec(hostId, cmd) }
        }
    }

    /** Enqueue stdin bytes (ordered with IME / special-key writes). */
    private suspend fun writeBytes(data: ByteArray) {
        if (data.isEmpty()) return
        cancelTmuxCopyModeIfNeeded()
        // send() suspends only if channel capacity is full (UNLIMITED → never).
        writeQueue.send(data)
    }

    private fun labelToBytes(label: String): ByteArray? {
        val seq: String = when (label) {
            "Esc", "Escape" -> "\u001B"
            "Tab" -> "\t"
            "Enter" -> "\r"
            "Backspace" -> "\u007F"
            "↑", "ArrowUp" -> "\u001B[A"
            "↓", "ArrowDown" -> "\u001B[B"
            "→", "ArrowRight" -> "\u001B[C"
            "←", "ArrowLeft" -> "\u001B[D"
            "Home" -> "\u001B[H"
            "End" -> "\u001B[F"
            "Pg Up", "PageUp" -> "\u001B[5~"
            "Pg Dn", "PageDown" -> "\u001B[6~"
            "Delete" -> "\u001B[3~"
            "Insert" -> "\u001B[2~"
            else -> return label.toByteArray(Charsets.UTF_8)
        }
        return seq.toByteArray(Charsets.US_ASCII)
    }

    suspend fun resize(cols: Int, rows: Int, applyToBuffer: Boolean = true) {
        if (cols < 4 || rows < 4) return
        if (cols == lastRemoteCols && rows == lastRemoteRows && !applyToBuffer) return
        val epoch = channelEpoch.get()
        lastRemoteCols = cols
        lastRemoteRows = rows
        DiagLog.i("pty", "resize remote ${cols}x$rows")
        resizeRemote(cols, rows, epoch)
        // Prefer Termux view-driven updateSize; only force when needed
        if (applyToBuffer) {
            mainHandler.post {
                if (!epochLive(epoch)) return@post
                val em = termuxSession?.getEmulator()
                if (em != null && (em.mColumns != cols || em.mRows != rows)) {
                    termuxSession?.updateSize(cols, rows, 12, 24)
                }
            }
        }
    }

    private suspend fun resizeRemote(cols: Int, rows: Int, epoch: Long = channelEpoch.get()) {
        withContext(Dispatchers.IO) {
            if (!epochLive(epoch)) return@withContext
            runCatching { pty?.resize(cols, rows) }
        }
    }

    fun forceRedraw() {
        mainHandler.post { termuxSession?.refresh() }
    }

    /**
     * Visible VT grid as plain text (what the user last saw).
     * Used when leaving a session for an optimistic list-card update.
     *
     * Prefer this over a long remote history dump: the list card should
     * match the last thing the user saw, not 80 lines of tmux scrollback.
     */
    fun captureVisibleSnapshot(maxChars: Int = 2000): String? {
        val em = termuxSession?.getEmulator() ?: return null
        val cols = em.mColumns.coerceAtLeast(1)
        val rows = em.mRows.coerceAtLeast(1)
        // Active screen (main or alt) — matches TerminalView.
        val raw = runCatching {
            em.screen.getSelectedText(0, 0, cols, rows - 1, false, false)
        }.getOrNull()?.trimEnd()
        if (raw.isNullOrBlank()) return null
        val lines = raw.lines().map { it.trimEnd() }
        val nonBlank = lines.filter { it.isNotBlank() }
        if (nonBlank.isEmpty()) return null
        // Keep last N non-blank lines so the card shows recent context, not empty chrome.
        val tail = if (nonBlank.size > 24) nonBlank.takeLast(24) else nonBlank
        val text = tail.joinToString("\n").trim()
        if (text.isBlank()) return null
        return text.take(maxChars)
    }

    /** True when local VT text is dense enough to show on the list card. */
    fun isUsefulSnapshot(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val nonBlank = text.lines().count { it.isNotBlank() }
        return text.trim().length >= 48 && nonBlank >= 2
    }

    fun notifyScrollChanged() {
        forceRedraw()
    }

    fun clearDisplay(status: String? = null) {
        statusPhase = StatusPhase.Connecting
        _status.value = status ?: s(R.string.term_connecting)
        mainHandler.post { termuxSession?.reset() }
    }

    private fun closePtyOnly() {
        // Null refs first so collector `finally` does not treat intentional close as a crash.
        val oldPty = pty
        pty = null
        _linkAlive.value = false
        collectJob?.cancel()
        collectJob = null
        runCatching { oldPty?.close() }
        ctrlLatch = false
        shiftLatch = false
    }

    fun close() {
        bumpChannelEpoch("close")
        closePtyOnly()
        lastAttach = null
        attachedHostId = null
        termuxSession?.finishIfRunning()
        termuxSession = null
        tmuxHistoryPaging = false
        tmuxCopyModeDepth = 0
        tmuxPaneInline = false
        paneHistorySignature = null
        clearStatus()
        _latencyMs.value = null
        _transportLabel.value = "SSH"
        _sessionReady.value = _sessionReady.value + 1
    }

    /** Call when the controller is permanently discarded (activity destroy). */
    fun destroy() {
        close()
        writeQueue.close()
        writerJob?.cancel()
        writerJob = null
    }

}
