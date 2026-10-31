package app.mtmux.ssh

import android.content.Context
import app.mtmux.core.model.locale.AppLocale
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.transport.TransportException
import java.io.IOException

/**
 * Real SSH engine via sshj (AND-020 / AND-021 / AND-022).
 *
 * Crypto provider bootstrap + adaptive algorithm config for Android.
 */
class SshjEngine(
    private val knownHosts: KnownHostsStore,
    private val appContext: Context,
) : SshEngine {

    private fun s(id: Int, vararg args: Any): String = AppLocale.getString(appContext, id, *args)

    private val clients = ConcurrentHashMap<String, SSHClient>()

    /** True if we already hold a live authenticated client for [hostId]. */
    fun isConnected(hostId: String): Boolean {
        val c = clients[hostId] ?: return false
        return try {
            c.isConnected && c.isAuthenticated
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Drop a cached client that is dead/poisoned (e.g. after Broken transport EOF).
     * Next [connect] will open a fresh TCP session.
     */
    fun invalidate(hostId: String) {
        val c = clients.remove(hostId) ?: return
        runCatching { c.disconnect() }
        runCatching { c.close() }
    }

    internal fun requireClient(hostId: String): SSHClient =
        clients[hostId]?.takeIf {
            try {
                it.isConnected && it.isAuthenticated
            } catch (_: Exception) {
                false
            }
        } ?: error("not connected: $hostId")

    override suspend fun connect(params: SshConnectParams): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                SshCryptoBootstrap.ensure()
                // Reuse existing live connection — do NOT tear down on every call
                if (isConnected(params.hostId)) {
                    return@runCatching
                }
                disconnect(params.hostId)
                val pending = AtomicReference<HostKeyCheck?>(null)

                try {
                    doConnect(params, pending, forceExcludeCurve25519 = false, forceExcludeChacha = false)
                } catch (e: Exception) {
                    val check = pending.get()
                    if (check != null) throw HostKeyNeedsConfirmation(check)

                    val strategy = recoveryStrategy(e)
                    if (strategy != null) {
                        doConnect(
                            params,
                            pending,
                            forceExcludeCurve25519 = strategy.excludeCurve25519,
                            forceExcludeChacha = strategy.excludeChacha,
                        )
                    } else {
                        throw wrapConnectError(e)
                    }
                }
            }
        }

    private fun doConnect(
        params: SshConnectParams,
        pending: AtomicReference<HostKeyCheck?>,
        forceExcludeCurve25519: Boolean,
        forceExcludeChacha: Boolean,
    ) {
        val client = SSHClient(
            MtmuxSshConfig.create(
                forceExcludeCurve25519 = forceExcludeCurve25519,
                forceExcludeChacha = forceExcludeChacha,
            ),
        )
        client.addHostKeyVerifier(hostKeyVerifier(pending))
        client.connectTimeout = 15_000
        client.timeout = 30_000
        try {
            client.connect(params.hostname, params.port)
        } catch (e: Exception) {
            val check = pending.get()
            runCatching { client.close() }
            if (check != null) throw HostKeyNeedsConfirmation(check)
            throw e
        }
        try {
            authenticate(client, params)
            client.connection.keepAlive.keepAliveInterval = 30
            clients[params.hostId] = client
        } catch (e: Exception) {
            runCatching { client.disconnect() }
            runCatching { client.close() }
            throw e
        }
    }

    private data class Recovery(val excludeCurve25519: Boolean, val excludeChacha: Boolean)

    private fun recoveryStrategy(e: Throwable): Recovery? {
        val msg = throwableText(e)
        return when {
            msg.contains("x25519") ||
                msg.contains("curve25519") ||
                (msg.contains("no such algorithm") && msg.contains("provider bc")) ->
                Recovery(excludeCurve25519 = true, excludeChacha = false)

            msg.contains("chacha20") || msg.contains("poly1305") ->
                Recovery(excludeCurve25519 = !SshCryptoBootstrap.isX25519Available(), excludeChacha = true)

            msg.contains("aes/gcm") || msg.contains("gcm") && msg.contains("no such") ->
                // rebuild without GCM happens via capability probe; force chacha off only
                Recovery(excludeCurve25519 = !SshCryptoBootstrap.isX25519Available(), excludeChacha = false)

            else -> null
        }
    }

    private fun wrapConnectError(e: Exception): Exception {
        val msg = throwableText(e)
        val friendly = when {
            msg.contains("x25519") || msg.contains("curve25519") ->
                s(R.string.ssh_kex_unavailable, SshCryptoBootstrap.diagnostics())

            msg.contains("auth fail") || e is UserAuthException ->
                s(R.string.ssh_auth_fail)

            msg.contains("timeout") || msg.contains("timed out") ->
                s(R.string.ssh_timeout)

            msg.contains("connection refused") ->
                s(R.string.ssh_refused)

            msg.contains("network is unreachable") || msg.contains("unable to resolve") ||
                msg.contains("unknownhost") || msg.contains("invalid") ||
                msg.contains("uri") || msg.contains("illegal") ->
                s(R.string.ssh_unresolved, e.message ?: "")

            msg.contains("host key") ->
                s(R.string.ssh_host_key)

            e is TransportException ->
                s(R.string.ssh_transport, e.message ?: "", SshCryptoBootstrap.diagnostics())

            else ->
                s(R.string.ssh_connect_fail, e.message ?: e.javaClass.simpleName)
        }
        return IOException(friendly, e)
    }

    private fun hostKeyVerifier(
        pending: AtomicReference<HostKeyCheck?>,
    ): HostKeyVerifier = object : HostKeyVerifier {
        override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
            return when (val check = knownHosts.check(hostname, port, key)) {
                HostKeyCheck.Ok -> true
                is HostKeyCheck.Unknown -> {
                    pending.set(check)
                    false
                }
                is HostKeyCheck.Mismatch -> {
                    pending.set(check)
                    false
                }
            }
        }

        override fun findExistingAlgorithms(hostname: String?, port: Int): MutableList<String> =
            mutableListOf()
    }

    private fun throwableText(e: Throwable): String =
        generateSequence(e) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()

    fun acceptHostKey(check: HostKeyCheck) {
        when (check) {
            is HostKeyCheck.Unknown -> knownHosts.accept(check)
            is HostKeyCheck.Mismatch -> knownHosts.acceptMismatchReplace(check)
            HostKeyCheck.Ok -> Unit
        }
    }

    override suspend fun disconnect(hostId: String) {
        withContext(Dispatchers.IO) {
            clients.remove(hostId)?.let { c ->
                runCatching { c.disconnect() }
                runCatching { c.close() }
            }
        }
    }

    override suspend fun exec(hostId: String, command: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val client = requireClient(hostId)
                client.startSession().use { session ->
                    // No PTY for control-plane — faster, no term negotiation
                    val cmd = session.exec(command)
                    val out = IOUtils.readFully(cmd.inputStream).toString("UTF-8")
                    val err = IOUtils.readFully(cmd.errorStream).toString("UTF-8")
                    // Cap wait: slow links should not hang 30s on list/probe
                    cmd.join(12, TimeUnit.SECONDS)
                    when {
                        out.isNotBlank() -> out
                        err.isNotBlank() -> err
                        else -> ""
                    }
                }
            }
        }

    suspend fun openShellSession(
        hostId: String,
        cols: Int = 80,
        rows: Int = 24,
    ): Result<PtySession> =
        openPtyShell(hostId, cols, rows, kotlinx.coroutines.GlobalScope, null)

    private fun authenticate(client: SSHClient, params: SshConnectParams) {
        val user = params.username.trim()
        if (user.isEmpty()) {
            throw IOException(s(R.string.ssh_empty_user))
        }

        val methods = mutableListOf<net.schmizz.sshj.userauth.method.AuthMethod>()
        val tried = mutableListOf<String>()

        // 1) Public key first when present
        if (!params.privateKeyPem.isNullOrBlank()) {
            val keys = KeyMaterialLoader.load(
                client = client,
                privateKeyPem = params.privateKeyPem,
                passphrase = params.passphrase,
                appContext = appContext,
            )
            methods += net.schmizz.sshj.userauth.method.AuthPublickey(keys)
            tried += "publickey"
        }

        // 2) Password + keyboard-interactive (many cloud images only enable the latter)
        if (!params.password.isNullOrBlank()) {
            val finder = object : net.schmizz.sshj.userauth.password.PasswordFinder {
                override fun reqPassword(
                    resource: net.schmizz.sshj.userauth.password.Resource<*>?,
                ): CharArray = params.password.toCharArray()

                override fun shouldRetry(
                    resource: net.schmizz.sshj.userauth.password.Resource<*>?,
                ): Boolean = false
            }
            methods += net.schmizz.sshj.userauth.method.AuthPassword(finder)
            methods += net.schmizz.sshj.userauth.method.AuthKeyboardInteractive(
                net.schmizz.sshj.userauth.method.PasswordResponseProvider(finder),
            )
            tried += "password"
            tried += "keyboard-interactive"
        }

        if (methods.isEmpty()) {
            throw IOException(s(R.string.ssh_no_auth))
        }

        try {
            // sshj will try methods in order against server-advertised allowed methods
            client.auth(user, methods)
        } catch (e: UserAuthException) {
            throw IOException(friendlyAuthFailure(params, tried, e), e)
        }

        if (!client.isAuthenticated) {
            throw IOException(friendlyAuthFailure(params, tried, null))
        }
    }

    private fun friendlyAuthFailure(
        params: SshConnectParams,
        tried: List<String>,
        e: Exception?,
    ): String {
        val raw = e?.let { throwableText(it) }.orEmpty()
        val hasKey = !params.privateKeyPem.isNullOrBlank()
        val hasPass = !params.password.isNullOrBlank()
        return buildString {
            appendLine(s(R.string.ssh_auth_detail_title))
            appendLine(s(R.string.ssh_auth_user, params.username, params.hostname, params.port))
            appendLine(s(R.string.ssh_auth_tried, tried.joinToString(", ")))
            appendLine()
            appendLine(s(R.string.ssh_auth_check))
            appendLine(s(R.string.ssh_auth_1))
            if (hasKey) {
                appendLine(s(R.string.ssh_auth_2))
                appendLine(s(R.string.ssh_auth_3))
                appendLine(s(R.string.ssh_auth_4))
            }
            if (hasPass) {
                appendLine(s(R.string.ssh_auth_5))
            }
            if (hasKey && hasPass) {
                appendLine(s(R.string.ssh_auth_6))
            }
            if (!hasKey && hasPass) {
                appendLine(s(R.string.ssh_auth_hint_key_only))
            }
            if (hasKey && !hasPass) {
                appendLine(s(R.string.ssh_auth_hint_add_pass))
            }
            if (raw.isNotBlank()) {
                appendLine()
                appendLine(s(R.string.ssh_auth_underlying, e?.message ?: raw))
            }
        }.trim()
    }
}
