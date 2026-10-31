package app.mtmux.ssh

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.connection.channel.direct.Session

/**
 * Interactive PTY over either `exec` (preferred, fast) or login shell.
 * Output is a [Channel] so early bytes are never dropped before the UI collector attaches.
 */
class PtySession(
    private val session: Session,
    private val stdin: OutputStream,
    private val stdout: InputStream,
    private val onCloseRemote: () -> Unit,
    scope: CoroutineScope,
) {
    private val closed = AtomicBoolean(false)
    private val _out = Channel<ByteArray>(capacity = 512)
    val output: ReceiveChannel<ByteArray> = _out

    private val readerJob: Job = scope.launch(Dispatchers.IO) {
        val buf = ByteArray(32 * 1024)
        try {
            while (isActive && !closed.get()) {
                val n = try {
                    stdout.read(buf)
                } catch (_: Exception) {
                    break
                }
                if (n < 0) break
                if (n > 0) {
                    val copy = buf.copyOf(n)
                    // Prefer drop-oldest on overflow rather than blocking remote forever
                    val sent = _out.trySend(copy)
                    if (sent.isFailure) {
                        // drain one and retry once
                        _out.tryReceive()
                        _out.trySend(copy)
                    }
                }
            }
        } finally {
            _out.close()
        }
    }

    suspend fun writeText(text: String) = writeBytes(text.toByteArray(Charsets.UTF_8))

    suspend fun writeBytes(data: ByteArray) = withContext(Dispatchers.IO) {
        if (closed.get() || !isOpen()) {
            error("PTY closed")
        }
        stdin.write(data)
        stdin.flush()
    }

    suspend fun resize(cols: Int, rows: Int) = withContext(Dispatchers.IO) {
        if (closed.get() || !isOpen()) error("PTY closed")
        runCatching {
            val m = session.javaClass.methods.firstOrNull {
                it.name == "changeWindowDimensions" && it.parameterTypes.size == 4
            }
            m?.invoke(session, cols, rows, cols * 8, rows * 16)
            Unit
        }.getOrThrow()
    }

    /** False after EOF / explicit [close] — used for resume health checks. */
    fun isOpen(): Boolean {
        if (closed.get()) return false
        return try {
            // sshj Session exposes isOpen; fall back to "not closed" if missing.
            val m = session.javaClass.methods.firstOrNull {
                it.name == "isOpen" && it.parameterTypes.isEmpty()
            }
            when (val v = m?.invoke(session)) {
                is Boolean -> v
                else -> true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        readerJob.cancel()
        runCatching { onCloseRemote() }
        runCatching { session.close() }
        _out.close()
    }
}

/** @deprecated name kept for call sites */
typealias PtySessionHandles = PtySession
typealias PtySessionInternal = PtySession

/**
 * Fast path: allocate PTY + exec force-command (no login shell, no prompt wait).
 * Ideal for `tmux attach` / `tmux new-session -A`.
 */
suspend fun SshjEngine.openPtyExec(
    hostId: String,
    cols: Int,
    rows: Int,
    scope: CoroutineScope,
    remoteCommand: String,
    termType: String = "xterm-256color",
): Result<PtySession> = withContext(Dispatchers.IO) {
    fun openOnce(): PtySession {
        val client = requireClient(hostId)
        val session = client.startSession()
        // TERM + size matter for ncurses/Claude TUIs — use real pixel guess
        val pxW = (cols * 10).coerceAtLeast(320)
        val pxH = (rows * 20).coerceAtLeast(240)
        val term = termType.ifBlank { "xterm-256color" }
        session.allocatePTY(term, cols, rows, pxW, pxH, emptyMap())
        val cmd = session.exec(remoteCommand)
        return PtySession(
            session = session,
            stdin = cmd.outputStream,
            stdout = cmd.inputStream,
            onCloseRemote = {
                runCatching { cmd.close() }
            },
            scope = scope,
        )
    }
    runCatching { openOnce() }.recoverCatching { first ->
        // Broken transport / EOF: drop poisoned client so next connect() is a real TCP.
        val msg = generateSequence(first) { it.cause }.mapNotNull { it.message }.joinToString(" ")
        if (msg.contains("EOF", ignoreCase = true) ||
            msg.contains("broken", ignoreCase = true) ||
            msg.contains("not connected", ignoreCase = true) ||
            msg.contains("Disconnected", ignoreCase = true)
        ) {
            invalidate(hostId)
        }
        throw first
    }
}

/**
 * Login shell (install wizard / free shell).
 */
suspend fun SshjEngine.openPtyShell(
    hostId: String,
    cols: Int,
    rows: Int,
    scope: CoroutineScope,
    initialCommand: String? = null,
    termType: String = "xterm-256color",
): Result<PtySession> = withContext(Dispatchers.IO) {
    runCatching {
        val client = requireClient(hostId)
        val session = client.startSession()
        val term = termType.ifBlank { "xterm-256color" }
        session.allocatePTY(term, cols, rows, cols * 8, rows * 16, emptyMap())
        val shell = session.startShell()
        if (initialCommand != null) {
            // Small delay not needed if we write after shell starts; still racey vs MOTD.
            shell.outputStream.write(initialCommand.toByteArray(Charsets.UTF_8))
            shell.outputStream.flush()
        }
        PtySession(
            session = session,
            stdin = shell.outputStream,
            stdout = shell.inputStream,
            onCloseRemote = { runCatching { shell.close() } },
            scope = scope,
        )
    }
}

/** Backward-compatible name used by TerminalSessionController. */
suspend fun SshjEngine.openPty(
    hostId: String,
    cols: Int,
    rows: Int,
    scope: CoroutineScope,
    initialCommand: String? = null,
    termType: String = "xterm-256color",
): Result<PtySession> {
    // If looks like a forced remote command line (tmux ...), use exec path.
    val cmd = initialCommand?.trim()?.trimEnd('\n')
    return if (cmd != null && (cmd.startsWith("tmux ") || cmd.startsWith("exec "))) {
        openPtyExec(hostId, cols, rows, scope, cmd, termType = termType)
    } else {
        openPtyShell(hostId, cols, rows, scope, initialCommand, termType = termType)
    }
}
