package app.mtmux.core.data.diag

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Diagnostic log for field debugging.
 *
 * **Fresh process = fresh log:** [init] truncates the main file so reinstall and
 * cold-start never pile up old runs. Exception: a previous-process crash file
 * (`muxa.crash.log`) is replayed into the new log so export after reopen still
 * shows the stack. In-session size cap rolls to `muxa.prev.log` if huge.
 * Never write passwords / private keys / passphrases.
 */
object DiagLog {
    private const val TAG = "muxa"
    private const val MAX_BYTES = 512 * 1024
    private val lock = ReentrantLock()
    private var logFile: File? = null
    private var prevFile: File? = null
    private var crashFile: File? = null
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        lock.withLock {
            val dir = File(context.filesDir, "diag").apply { mkdirs() }
            val f = File(dir, "muxa.log")
            val prev = File(dir, "muxa.prev.log")
            val crash = File(dir, "muxa.crash.log")
            // Also clean legacy names from m-tmux era.
            File(dir, "mtmux.log").delete()
            File(dir, "mtmux.prev.log").delete()
            prev.delete()
            // Preserve last crash across process death (main log is wiped every cold start).
            val priorCrash = runCatching {
                if (crash.isFile && crash.length() > 0L) crash.readText() else null
            }.getOrNull()
            f.writeText("")
            if (!priorCrash.isNullOrBlank()) {
                f.appendText(priorCrash)
                if (!priorCrash.endsWith("\n")) f.appendText("\n")
                f.appendText(
                    "${fmt.format(Date())} W/boot: --- end of previous-process crash (replayed) ---\n",
                )
                runCatching { crash.delete() }
            }
            logFile = f
            prevFile = prev
            crashFile = crash
        }
        installCrashHandler()
        i("boot", "DiagLog init (cleared for new process) filesDir=${context.filesDir}/diag")
    }

    /**
     * Capture JVM uncaught exceptions into the log + durable crash file.
     * Without this, process death leaves DiagLog with only the last happy path line.
     */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (isBenignSshjReaderNpe(thread, error)) {
                runCatching {
                    write(
                        'E',
                        "ssh",
                        "swallowed sshj Reader NPE (transport already dead): ${error.message}",
                        error,
                    )
                }
                return@setDefaultUncaughtExceptionHandler
            }
            runCatching {
                val stack = error.stackTraceToString().take(12_000)
                val msg =
                    "FATAL uncaught on ${thread.name}: ${error.javaClass.name}: ${error.message}"
                // Best-effort write before process dies (bypass level helpers).
                writeCrash(msg, stack)
            }
            previous?.uncaughtException(thread, error)
                ?: run {
                    // No prior handler — rethrow path: kill process after log.
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(10)
                }
        }
    }

    /**
     * sshj 0.38 [SessionChannel.notifyError] NPE on a dying transport
     * (ChannelInputStream still null). The socket is already gone; killing
     * the process is worse than swallowing. Field: 0.2.9 Xiaomi crash.
     */
    private fun isBenignSshjReaderNpe(thread: Thread, error: Throwable): Boolean {
        if (error !is NullPointerException) return false
        if (!thread.name.startsWith("sshj-Reader")) return false
        val msg = error.message.orEmpty()
        if (msg.contains("ChannelInputStream.notifyError")) return true
        return error.stackTrace.any {
            it.className.contains("SessionChannel") && it.methodName == "notifyError"
        }
    }

    private fun writeCrash(msg: String, stack: String) {
        val header = "${fmt.format(Date())} E/crash: $msg\n"
        val body = stack.lineSequence().joinToString("") { "${fmt.format(Date())} E/crash: $it\n" }
        val blob = header + body
        Log.e(TAG, msg)
        lock.withLock {
            runCatching { logFile?.appendText(blob) }
            runCatching {
                val cf = crashFile
                if (cf != null) {
                    cf.writeText(blob)
                }
            }
        }
    }

    fun i(area: String, msg: String) = write('I', area, msg, null)

    fun w(area: String, msg: String) = write('W', area, msg, null)

    fun e(area: String, msg: String, t: Throwable? = null) = write('E', area, msg, t)

    /** Full log text for export (clipboard / share). */
    fun readAll(): String = lock.withLock {
        val f = logFile ?: return@withLock "(diag not initialized)\n"
        if (!f.exists()) return@withLock "(empty)\n"
        runCatching { f.readText() }.getOrElse { "(read failed: ${it.message})\n" }
    }

    fun clear() = lock.withLock {
        logFile?.writeText("")
        prevFile?.delete()
        // note: recursive i() would re-enter lock — write unlocked line
        val f = logFile
        if (f != null) {
            val line = "${fmt.format(Date())} I/boot: log cleared\n"
            runCatching { f.appendText(line) }
            Log.i(TAG, "boot: log cleared")
        }
    }

    fun filePath(): String? = logFile?.absolutePath

    private fun write(level: Char, area: String, msg: String, t: Throwable?) {
        val line = buildString {
            append(fmt.format(Date()))
            append(' ')
            append(level)
            append('/')
            append(area)
            append(": ")
            append(sanitize(msg))
            if (t != null) {
                append(" | ")
                append(t.javaClass.simpleName)
                append(": ")
                append(sanitize(t.message ?: ""))
            }
            append('\n')
        }
        when (level) {
            'E' -> Log.e(TAG, "$area: $msg", t)
            'W' -> Log.w(TAG, "$area: $msg")
            else -> Log.i(TAG, "$area: $msg")
        }
        lock.withLock {
            val f = logFile ?: return@withLock
            runCatching {
                if (f.exists() && f.length() > MAX_BYTES) {
                    val bak = prevFile ?: File(f.parentFile, "muxa.prev.log")
                    f.copyTo(bak, overwrite = true)
                    f.writeText("")
                }
                f.appendText(line)
            }
        }
    }

    /** Strip likely secrets from log lines. */
    private fun sanitize(s: String): String {
        var out = s
        out = out.replace(Regex("-----BEGIN[\\s\\S]*?-----END[^-]+-----"), "[PEM_REDACTED]")
        out = out.replace(Regex("(?i)(password|passphrase|secret|token)\\s*[=:]\\s*\\S+"), "$1=[REDACTED]")
        return out.take(2000)
    }
}
