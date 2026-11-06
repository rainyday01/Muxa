package app.mtmux.android

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.mtmux.autocomplete.AutocompleteEngine
import app.mtmux.autocomplete.InputLineTracker
import app.mtmux.core.data.db.CachedSessionEntity
import app.mtmux.core.data.db.SnippetEntity
import app.mtmux.core.data.db.SnapshotEntity
import app.mtmux.core.data.diag.DiagLog
import app.mtmux.core.data.diag.DiagRedactor
import app.mtmux.core.data.diag.GitHubIssue
import app.mtmux.core.data.diag.LogHubClient
import app.mtmux.core.data.diag.LogHubCooldownException
import app.mtmux.core.data.diag.LogHubHttpException
import app.mtmux.core.data.export.ConfigExport
import app.mtmux.core.data.keyboard.BuiltinKeyboards
import app.mtmux.core.data.keyboard.KeyboardActionResolver
import app.mtmux.core.data.keyboard.ResolvedKeyAction
import app.mtmux.core.data.prefs.UserPreferences
import app.mtmux.core.model.AppLanguage
import app.mtmux.core.model.AppearanceMode
import app.mtmux.core.model.ComposeHistory
import app.mtmux.core.ui.locale.AppLocale
import app.mtmux.core.ui.R as UiR
import app.mtmux.core.model.Host
import app.mtmux.core.model.KeyboardLayout
import app.mtmux.core.model.SessionListMode
import app.mtmux.core.model.SnapshotText
import app.mtmux.core.model.Snippet
import app.mtmux.core.model.Suggestion
import app.mtmux.core.model.SuggestionSource
import app.mtmux.core.model.TmuxSession
import app.mtmux.core.model.TransportPreference
import app.mtmux.core.ui.feedback.LocalSnackbarHost
import app.mtmux.core.ui.feedback.rememberAppSnackbarHostState
import app.mtmux.core.ui.theme.LocalMtmuxTheme
import app.mtmux.core.ui.theme.MtmuxTheme
import app.mtmux.feature.hosts.HostEditorScreen
import app.mtmux.feature.hosts.HostFieldSanitizer
import app.mtmux.feature.hosts.HostKeyConfirmDialog
import app.mtmux.feature.hosts.HostsScreen
import app.mtmux.feature.hosts.InstallWizardScreen
import app.mtmux.feature.sessions.NewSessionDialog
import app.mtmux.feature.sessions.SessionMoreSheet
import app.mtmux.feature.sessions.SessionsScreen
import app.mtmux.feature.settings.KeyboardEditorScreen
import app.mtmux.feature.settings.KeyboardImportDialog
import app.mtmux.feature.settings.KeyboardListScreen
import app.mtmux.feature.settings.PrivacyScreen
import app.mtmux.feature.settings.SettingsScreen
import app.mtmux.feature.settings.SnippetsScreen
import app.mtmux.feature.settings.TerminalAppearanceScreen
import app.mtmux.feature.terminal.MtmuxSessionClient
import app.mtmux.feature.terminal.TerminalScreen
import app.mtmux.feature.terminal.TerminalSchemeApplier
import app.mtmux.core.ui.terminal.BuiltinSchemes
import app.mtmux.ssh.EnvProbe
import app.mtmux.ssh.HostKeyCheck
import app.mtmux.ssh.HostKeyNeedsConfirmation
import app.mtmux.ssh.SshConnectParams
import app.mtmux.ssh.TmuxControl
import app.mtmux.ssh.TmuxWindow
import app.mtmux.ssh.applyEnvProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(app.mtmux.core.ui.locale.AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge: do NOT use SystemBarStyle.auto { system night }.
        // That follows the *phone* day/night and fights app-forced dark/light
        // (system light + app dark → dark status icons on black chrome).
        // MtmuxTheme / SystemBarContrast always re-applies icon contrast from
        // the resolved app surface. Initial dark() = light icons until Compose runs.
        // When editing appearance/theme code, verify all OS×app combos in
        // SystemBarContrast KDoc.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        val app = application as MtmuxApp
        setContent { MtmuxRoot(app, this) }
    }

}

private enum class Tab(val route: String, val titleRes: Int) {
    Sessions("sessions", app.mtmux.core.ui.R.string.tab_sessions),
    Hosts("hosts", app.mtmux.core.ui.R.string.tab_hosts),
    Settings("settings", app.mtmux.core.ui.R.string.tab_settings),
}

/**
 * Leave-time card text: visible screen first (what the user just saw).
 * Remote visible pane is fallback. Never pick "longest history".
 */
private fun pickLeaveSnapshot(local: String?, remote: String?): String? {
    val source = when {
        SnapshotText.isUseful(local) -> local
        SnapshotText.isUseful(remote) -> remote
        else -> local?.takeIf { it.isNotBlank() } ?: remote
    }
    return SnapshotText.forCard(source, maxChars = SnapshotText.STORE_CHARS)
}

@Composable
private fun MtmuxRoot(app: MtmuxApp, activity: ComponentActivity) {
    val c = app.container
    val ctx = LocalContext.current
    val appVersionName = remember(ctx) {
        runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull()?.takeIf { !it.isNullOrBlank() }
            ?: BuildConfig.VERSION_NAME
    }
    val appearance by c.prefs.appearance.collectAsState(initial = AppearanceMode.SYSTEM)
    val appLanguage by c.prefs.appLanguage.collectAsState(initial = AppLanguage.SYSTEM)
    val customTheme by c.prefs.customThemeId.collectAsState(initial = null)
    val autocompleteOn by c.prefs.autocompleteEnabled.collectAsState(initial = true)
    val openLast by c.prefs.openLastSessionOnLaunch.collectAsState(initial = false)
    val sessionListMode by c.prefs.sessionListMode.collectAsState(initial = SessionListMode.GroupedByHost)
    val hostOrder by c.prefs.hostOrder.collectAsState(initial = emptyList())
    val sessionOrder by c.prefs.sessionOrder.collectAsState(initial = emptyList())
    val terminalFontSizePx by c.prefs.terminalFontSizePx.collectAsState(
        initial = UserPreferences.DEFAULT_FONT_PX,
    )
    val terminalSchemeId by c.prefs.terminalSchemeId.collectAsState(
        initial = UserPreferences.DEFAULT_SCHEME_ID,
    )
    val terminalTypefaceId by c.prefs.terminalTypefaceId.collectAsState(
        initial = UserPreferences.DEFAULT_TYPEFACE_ID,
    )
    val terminalTermType by c.prefs.terminalTermType.collectAsState(
        initial = UserPreferences.DEFAULT_TERM_TYPE,
    )
    val terminalBufferLines by c.prefs.terminalBufferLines.collectAsState(
        initial = UserPreferences.DEFAULT_BUFFER_LINES,
    )
    val preventSleeping by c.prefs.preventSleeping.collectAsState(initial = true)
    val backgroundKeepMinutes by c.prefs.backgroundKeepMinutes.collectAsState(
        initial = UserPreferences.DEFAULT_BACKGROUND_KEEP_MINUTES,
    )
    // The hold works either way; this only decides whether its notification shows.
    val requestNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> DiagLog.i("keep", "notification permission granted=$granted") }
    val shortcutOrder by c.prefs.shortcutBarOrder.collectAsState(initial = emptyList())
    val composeEnabled by c.prefs.composeBoxEnabled.collectAsState(initial = false)
    val composeHistory by c.prefs.composeHistory.collectAsState(initial = emptyList())
    val hosts by c.hosts.observeHosts().collectAsState(initial = emptyList())

    /** SSH environment probe: tmux and host resources. */
    suspend fun probeEnvFull(host: Host): EnvProbe {
        return c.tmux.probeEnvironment(host.id)
    }

    fun envSummary(env: EnvProbe): String {
        val tmux = if (env.tmux) "✓" else "✗"
        return "tmux=$tmux${env.latencyMs?.let { " ${it}ms" } ?: ""}"
    }
    val snippetsFlow by c.db.snippets().observeAll().collectAsState(initial = emptyList())
    val snippetModels = remember(snippetsFlow) {
        snippetsFlow.map {
            Snippet(it.id, it.name, it.body, it.tagsCsv.split(',').filter { t -> t.isNotBlank() }, it.runOnSelect)
        }
    }
    val keyboardLayouts by c.keyboards.observeAll().collectAsState(initial = BuiltinKeyboards.all())
    val activeKeyboardId by c.keyboards.activeLayoutId.collectAsState(initial = "general")
    val activeKeyboard: KeyboardLayout =
        keyboardLayouts.find { it.id == activeKeyboardId }
            ?: BuiltinKeyboards.byId(activeKeyboardId)
            ?: BuiltinKeyboards.general
    val scope = rememberCoroutineScope()
    val snackbar = rememberAppSnackbarHostState()

    var sessions by remember { mutableStateOf<List<TmuxSession>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var emptyNeedsInstall by remember { mutableStateOf(false) }
    var sessionsFromCache by remember { mutableStateOf(false) }
    var terminalSession by remember { mutableStateOf<TmuxSession?>(null) }
    /** Bumps on each enter/leave so a slow attach cannot paint a previous session. */
    var attachGen by remember { mutableStateOf(0L) }
    /**
     * Non-Compose generation for leave/reattach races. Snapshot State can lag inside
     * IO coroutines; AtomicLong is always current.
     */
    val attachEpochGate = remember { java.util.concurrent.atomic.AtomicLong(0L) }
    /** Wait for TerminalScreen real size before opening PTY (avoids 80x24→resize race). */
    var pendingSurfaceSize by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var attachStartedForGen by remember { mutableStateOf(0L) }
    /** Set to attachGen only after PTY attach succeeds (UI overlay). */
    var attachReadyGen by remember { mutableStateOf(0L) }
    val termController = remember { TerminalSessionController(c.sshEngine, scope, ctx) }
    val transportLabel by termController.transportLabel.collectAsState()
    val termStatus by termController.status.collectAsState()
    val latencyMs by termController.latencyMs.collectAsState()
    val termuxSessionGen by termController.sessionGeneration.collectAsState()

    // Keep TERM / buffer prefs on controller before each prepareSession
    termController.termType = terminalTermType
    termController.transcriptRows = terminalBufferLines

    // Termius "Prevent sleeping": keep display on while in a terminal session
    DisposableEffect(terminalSession != null, preventSleeping) {
        val win = activity.window
        if (terminalSession != null && preventSleeping) {
            win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    var ctrlLatched by remember { mutableStateOf(false) }
    var shiftLatched by remember { mutableStateOf(false) }
    var altLatched by remember { mutableStateOf(false) }
    var editingHost by remember { mutableStateOf<Host?>(null) }
    /** Loaded secrets for host editor (prefill password field as masked bullets). */
    var editingSavedPassword by remember { mutableStateOf<String?>(null) }
    var editingSavedPassphrase by remember { mutableStateOf<String?>(null) }
    var showHostEditor by remember { mutableStateOf(false) }
    var pendingHostKey by remember { mutableStateOf<HostKeyCheck?>(null) }
    var pendingHostKeyRetry by remember { mutableStateOf<Host?>(null) }
    var moreSession by remember { mutableStateOf<TmuxSession?>(null) }
    var showNewSession by remember { mutableStateOf(false) }
    var installHost by remember { mutableStateOf<Host?>(null) }
    var installProbe by remember { mutableStateOf<EnvProbe?>(null) }
    var windowsDialog by remember { mutableStateOf<Pair<TmuxSession, List<TmuxWindow>>?>(null) }
    var installPty by remember { mutableStateOf<Pair<Host, String>?>(null) }
    var showSnippets by remember { mutableStateOf(false) }
    var showKeyboards by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showTerminalAppearance by remember { mutableStateOf(false) }
    var editingKeyboard by remember { mutableStateOf<KeyboardLayout?>(null) }
    var keyboardImport by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<Suggestion>>(emptyList()) }
    var suggestJob by remember { mutableStateOf<Job?>(null) }
    /** Shell line being typed, rebuilt from the bytes sent to the PTY. */
    val inputTracker = remember { InputLineTracker() }
    /** Last completion probe found a shell prompt (not a full-screen app). */
    var shellLineActive by remember { mutableStateOf(false) }
    /** Uptime of the last probe that found a full-screen app: skip probing while typing into it. */
    val lastTuiProbeAt = remember { longArrayOf(0L) }
    var didOpenLast by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var exportBusy by remember { mutableStateOf(false) }
    /** Encrypted export waiting for the user to pick a file. */
    var pendingExportBlob by remember { mutableStateOf<String?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var importBusy by remember { mutableStateOf(false) }
    var importLoaded by remember { mutableStateOf<String?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    /** hostId\\tname → last opened epoch (AND-104 recent view) */
    var lastOpened by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    /** In-process last background capture-pane, so stale cards are not hammered every refresh. */
    val lastBgCapture = remember { mutableMapOf<String, Long>() }

    fun hideSoftKeyboard() {
        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val token = activity.window?.decorView?.windowToken
        imm?.hideSoftInputFromWindow(token, 0)
        // Also clear focus from any focused child
        activity.currentFocus?.clearFocus()
    }

    fun dismissToasts() {
        scope.launch {
            runCatching { snackbar.currentSnackbarData?.dismiss() }
        }
    }

    /**
     * @param force show even over terminal/wizard overlays (install errors, leave feedback).
     * Default still suppresses soft toasts on pure terminal to avoid ghost snackbars.
     */
    fun tr(id: Int, vararg args: Any): String =
        if (args.isEmpty()) ctx.getString(id) else ctx.getString(id, *args)

    fun toast(msg: String, force: Boolean = false) {
        // Soft toasts while a full-screen terminal is open reappear as ghosts on leave.
        // Wizard / settings overlays may show snackbars; installPty uses force for errors.
        if (!force && (terminalSession != null || installPty != null)) {
            return
        }
        scope.launch {
            runCatching { snackbar.currentSnackbarData?.dismiss() }
            snackbar.showSnackbar(msg, duration = SnackbarDuration.Short)
        }
    }

    fun openUrl(url: String) {
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { toast(tr(UiR.string.toast_no_browser), force = true) }
    }

    // Diagnostic zip waiting for the user to pick where to save it, and the
    // GitHub issue to open once it is saved (null = export only).
    var pendingDiagZip by remember { mutableStateOf<ByteArray?>(null) }
    var pendingIssueUrl by remember { mutableStateOf<String?>(null) }
    val saveDiagZip = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val bytes = pendingDiagZip
        val issueUrl = pendingIssueUrl
        pendingDiagZip = null
        pendingIssueUrl = null
        scope.launch {
            if (uri != null && bytes != null) {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
                    }.getOrDefault(false)
                }
                DiagLog.i("diag", "redacted log export ok=$ok bytes=${bytes.size}")
                toast(tr(if (ok) UiR.string.toast_diag_saved else UiR.string.toast_diag_save_fail), force = true)
            }
            // Open the issue even if saving was cancelled: it carries the log tail.
            issueUrl?.let { openUrl(it) }
        }
    }

    /** Redact the log, then ask where to save it; [note] non-null also files a GitHub issue. */
    fun exportRedactedDiag(note: String?) {
        scope.launch {
            val log = withContext(Dispatchers.Default) { DiagRedactor.redact(LogHubClient.cappedLog()) }
            val zip = withContext(Dispatchers.Default) {
                LogHubClient.buildZip(
                    logText = log,
                    deviceId = "redacted",
                    appVersion = appVersionName,
                    note = note.orEmpty(),
                )
            }
            pendingDiagZip = zip
            pendingIssueUrl = note?.let {
                GitHubIssue.newIssueUrl(
                    GitHubIssue.Report(
                        note = it,
                        appVersion = appVersionName,
                        device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                        osVersion = android.os.Build.VERSION.RELEASE.orEmpty(),
                        log = log,
                    ),
                )
            }
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
                .format(java.util.Date())
            runCatching { saveDiagZip.launch("muxa-diag-$stamp.zip") }.onFailure {
                // No document picker: still let the user file the issue.
                pendingDiagZip = null
                pendingIssueUrl?.let { url -> openUrl(url) }
                pendingIssueUrl = null
            }
        }
    }

    /** Clipboard copy that Android 13+ keeps out of the clipboard preview. */
    fun copySensitive(label: String, text: String) {
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    }

    val saveExportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val blob = pendingExportBlob
        pendingExportBlob = null
        if (uri == null || blob == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(blob.toByteArray()) } != null
                }.getOrDefault(false)
            }
            toast(tr(if (ok) UiR.string.toast_export_saved else UiR.string.toast_export_fail), force = true)
        }
    }

    val openImportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readNBytesCompat(1 shl 20)
                        String(bytes, Charsets.UTF_8)
                    }
                }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                importError = tr(UiR.string.import_err_read)
            } else {
                importError = null
                importLoaded = text
            }
        }
    }

    fun runExport(password: CharArray, includeSecrets: Boolean, target: ExportTarget) {
        exportBusy = true
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.Default) {
                    val hostList = c.hosts.observeHosts().first()
                    val secrets = if (!includeSecrets) emptyMap() else {
                        hostList.associate { h ->
                            h.id to ConfigExport.HostSecrets(
                                password = c.hosts.password(h.id),
                                privateKeyPem = c.hosts.privateKeyPem(h.id),
                                passphrase = c.hosts.keyPassphrase(h.id),
                            )
                        }.filterValues { !it.isEmpty }
                    }
                    val json = ConfigExport.exportJson(
                        ConfigExport.Bundle(hostList, c.db.snippets().all(), secrets),
                    )
                    ConfigExport.encrypt(json, password)
                }
            }
            password.fill('\u0000')
            exportBusy = false
            result.onSuccess { blob ->
                DiagLog.i("export", "config export ok secrets=$includeSecrets target=$target")
                showExport = false
                when (target) {
                    ExportTarget.Clipboard -> {
                        copySensitive("muxa-config", blob)
                        toast(
                            tr(
                                if (includeSecrets) UiR.string.toast_export_copied_secrets
                                else UiR.string.toast_export_copied,
                            ),
                            force = true,
                        )
                    }
                    ExportTarget.File -> {
                        pendingExportBlob = blob
                        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
                            .format(java.util.Date())
                        runCatching { saveExportFile.launch("muxa-config-$stamp.json") }.onFailure {
                            pendingExportBlob = null
                            toast(tr(UiR.string.toast_export_fail), force = true)
                        }
                    }
                }
            }.onFailure {
                DiagLog.e("export", "config export failed", it)
                toast(tr(UiR.string.toast_export_fail), force = true)
            }
        }
    }

    fun runImport(blob: String, password: CharArray) {
        importBusy = true
        importError = null
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.Default) {
                    val bundle = ConfigExport.parseImport(ConfigExport.decrypt(blob, password))
                    bundle.hosts.forEach { h ->
                        val sec = bundle.secrets[h.id]
                        c.hosts.importHost(h, sec?.password, sec?.privateKeyPem, sec?.passphrase)
                    }
                    bundle.snippets.forEach { c.db.snippets().upsert(it) }
                    bundle
                }
            }
            password.fill('\u0000')
            importBusy = false
            result.onSuccess { b ->
                DiagLog.i("export", "config import ok hosts=${b.hosts.size} snippets=${b.snippets.size} secrets=${b.secrets.size}")
                showImport = false
                importLoaded = null
                toast(tr(UiR.string.toast_imported_n, b.hosts.size, b.snippets.size), force = true)
            }.onFailure {
                DiagLog.w("export", "config import failed ${it.javaClass.simpleName}")
                importError = when (it) {
                    is ConfigExport.WrongPasswordException -> tr(UiR.string.import_err_password)
                    is org.json.JSONException, is IllegalArgumentException -> tr(UiR.string.import_err_format)
                    else -> tr(UiR.string.toast_import_fail_msg, it.message ?: it.javaClass.simpleName)
                }
            }
        }
    }

    suspend fun paramsOf(h: Host): SshConnectParams {
        // Auto-repair paste mistakes / legacy bad saves (newlines, glued password)
        val parsed = HostFieldSanitizer.parseHostname(h.hostname)
        val hostName = parsed.host
        var password = c.hosts.password(h.id)
        var username = h.username
        var port = h.port
        var dirty = false
        if (hostName != h.hostname.trim()) {
            dirty = true
            DiagLog.w("ssh", "hostname cleaned alias=${h.alias} raw=${h.hostname} -> $hostName hints=${parsed.hints}")
        }
        if (!parsed.password.isNullOrBlank() && password.isNullOrEmpty()) {
            password = parsed.password
            dirty = true
        }
        if (!parsed.username.isNullOrBlank() && username.isBlank()) {
            username = parsed.username!!
            dirty = true
        }
        val parsedPort = parsed.port
        if (parsedPort != null && parsedPort != port) {
            port = parsedPort
            dirty = true
        }
        if (dirty && HostFieldSanitizer.isPlausibleHost(hostName)) {
            runCatching {
                c.hosts.upsert(
                    h.copy(hostname = hostName, username = username, port = port),
                    password = password?.toCharArray(),
                )
            }.onFailure { DiagLog.e("ssh", "persist hostname fix failed", it) }
        }
        return SshConnectParams(
            hostId = h.id,
            hostname = hostName,
            port = port,
            username = username,
            privateKeyPem = c.hosts.privateKeyPem(h.id),
            password = password,
            passphrase = c.hosts.keyPassphrase(h.id),
            preferredTransport = h.transport,
        )
    }

    /**
     * @param quiet when true, log failures without snackbar (multi-host background refresh
     *   must not surface unrelated dead hosts as "create failed").
     */
    suspend fun connectWithHostKeyUi(h: Host, quiet: Boolean = false): Boolean {
        val params = paramsOf(h)
        DiagLog.i(
            "ssh",
            "connect start alias=${h.alias} host=${params.hostname}:${params.port} " +
                "user=${h.username} transport=${h.transport} rawHost=${h.hostname}",
        )
        val t0 = System.currentTimeMillis()
        val r = c.connections.connect(params)
        if (r.isSuccess) {
            DiagLog.i("ssh", "connect ok alias=${h.alias} ${System.currentTimeMillis() - t0}ms")
            return true
        }
        val ex = r.exceptionOrNull()
        val cause = generateSequence(ex) { it.cause }.firstOrNull { it is HostKeyNeedsConfirmation }
            as? HostKeyNeedsConfirmation
        if (cause != null) {
            DiagLog.w("ssh", "connect needs host key alias=${h.alias} type=${cause.check.javaClass.simpleName}")
            // Host-key UI always needed even in quiet multi-host refresh
            pendingHostKey = cause.check
            pendingHostKeyRetry = h
            return false
        }
        DiagLog.e("ssh", "connect fail alias=${h.alias} ${System.currentTimeMillis() - t0}ms", ex)
        if (!quiet) {
            toast(tr(UiR.string.toast_connect_fail, h.alias, ex?.message ?: tr(UiR.string.toast_connect_fail_generic)))
        }
        return false
    }

    /** Instant: paint last known sessions + pane snapshots (no SSH). */
    suspend fun loadSessionsFromCache() {
        val cached = c.db.cachedSessions().all()
        if (cached.isEmpty()) {
            DiagLog.i("sessions", "cache empty")
            return
        }
        val snaps = c.db.snapshots().all().associateBy { it.key }
        val hostMap = hosts.associateBy { it.id }
        val mapped = cached.map { row ->
            val snap = snaps[row.key]
            TmuxSession(
                hostId = row.hostId,
                name = row.name,
                windows = row.windows,
                attached = row.attached,
                snapshotText = SnapshotText.forCard(snap?.text),
                snapshotUpdatedAtEpochMs = snap?.updatedAtEpochMs,
                hostAlias = row.hostAlias ?: hostMap[row.hostId]?.alias,
                socketPath = row.socketPath,
            )
        }
        sessions = mapped
        sessionsFromCache = true
        emptyNeedsInstall = false
        DiagLog.i("sessions", "cache hit count=${mapped.size}")
    }

    suspend fun persistSessionCache(list: List<TmuxSession>) {
        val now = System.currentTimeMillis()
        val rows = list.map { s ->
            CachedSessionEntity(
                key = s.key(),
                hostId = s.hostId,
                name = s.name,
                windows = s.windows,
                attached = s.attached,
                hostAlias = s.hostAlias,
                socketPath = s.socketPath,
                listedAtEpochMs = now,
            )
        }
        // Replace cache wholesale so killed sessions disappear
        c.db.cachedSessions().clear()
        if (rows.isNotEmpty()) c.db.cachedSessions().upsertAll(rows)
    }

    /**
     * Refresh session cards.
     *
     * @param onlyHostIds when set, only SSH/list those hosts and merge into the existing list
     *   (other hosts untouched). Used after create/rename/kill so a dead secondary host cannot
     *   toast "failure" over a successful create.
     * @param quietFailures when true (default for multi-host refresh), log connect/list errors
     *   without snackbar; keep previous sessions for that host instead of wiping the list.
     */
    fun refreshSessions(
        fullProbe: Boolean = false,
        onlyHostIds: Set<String>? = null,
        quietFailures: Boolean = onlyHostIds == null,
    ) {
        scope.launch {
            loading = true
            val all = mutableListOf<TmuxSession>()
            var anyMissingTmux = false
            val previousByHost = sessions.groupBy { it.hostId }
            val targets = if (onlyHostIds != null) {
                hosts.filter { it.id in onlyHostIds }
            } else {
                hosts
            }
            if (hosts.isEmpty()) {
                toast(tr(UiR.string.toast_add_host_first))
            } else if (targets.isEmpty()) {
                DiagLog.w("sessions", "refresh skipped: no matching hosts only=$onlyHostIds")
            } else {
                // Parallel per-host: list first, heavy probe only when stale / requested
                val jobs = targets.map { h ->
                    launch {
                        if (!connectWithHostKeyUi(h, quiet = quietFailures)) {
                            // Keep last-known cards so a dead host does not blank the UI
                            synchronized(all) {
                                previousByHost[h.id]?.let { all += it }
                            }
                            return@launch
                        }
                        val stale = h.lastCheckedAtEpochMs?.let {
                            System.currentTimeMillis() - it > 5 * 60_000L
                        } != false
                        if (fullProbe || stale || h.tmuxInstalled == null) {
                            // Background refresh: packages only (UDP is slow; explicit 环境检查 does UDP)
                            val env = c.tmux.probeEnvironment(h.id)
                            c.hosts.upsert(h.applyEnvProbe(env))
                            if (!env.tmux) {
                                anyMissingTmux = true
                                return@launch
                            }
                        } else if (h.tmuxInstalled == false) {
                            anyMissingTmux = true
                            return@launch
                        }
                        // Fast path: list-sessions only (no capture-pane on refresh)
                        c.tmux.listSessions(h.id).onSuccess { list ->
                            DiagLog.i("tmux", "listSessions ok alias=${h.alias} count=${list.size} names=${list.map { it.name }}")
                            val mapped = list.map { s ->
                                val key = s.key()
                                val snapRow = c.db.snapshots().get(key)
                                s.copy(
                                    snapshotText = SnapshotText.forCard(snapRow?.text),
                                    snapshotUpdatedAtEpochMs = snapRow?.updatedAtEpochMs,
                                    hostAlias = h.alias,
                                )
                            }
                            synchronized(all) { all += mapped }
                        }.onFailure {
                            DiagLog.e("tmux", "listSessions fail alias=${h.alias}", it)
                            if (!quietFailures) {
                                toast("${h.alias} list: ${it.message}")
                            }
                            synchronized(all) {
                                previousByHost[h.id]?.let { all += it }
                            }
                        }
                        // Background: fill missing snaps + refresh up to 3 oldest/stale cards.
                        // Text may update; the corner chip stays last-used (do not stamp now).
                        launch {
                            val hostSessions = synchronized(all) {
                                all.filter { it.hostId == h.id }
                            }
                            val need = hostSessions
                                .sortedWith(
                                    compareBy<TmuxSession> {
                                        if (it.snapshotText.isNullOrBlank()) 0 else 1
                                    }.thenBy {
                                        val bg = synchronized(lastBgCapture) { lastBgCapture[it.key()] }
                                        bg ?: it.snapshotUpdatedAtEpochMs ?: 0L
                                    },
                                )
                                .take(3)
                            need.forEach { s ->
                                c.tmux.capturePane(h.id, s.name, s.socketPath).getOrNull()?.let { text ->
                                    val trimmed = text.trim()
                                    if (trimmed.isBlank()) {
                                        DiagLog.w("tmux", "capture empty ${s.hostId}/${s.name}")
                                        return@forEach
                                    }
                                    val now = System.currentTimeMillis()
                                    val leaveAge = s.snapshotUpdatedAtEpochMs?.let { now - it }
                                    val bgAge = synchronized(lastBgCapture) {
                                        lastBgCapture[s.key()]
                                    }?.let { now - it }
                                    // Keep a fresh leave-time card; don't replace with a live dump.
                                    if (!s.snapshotText.isNullOrBlank() &&
                                        leaveAge != null && leaveAge < 30 * 60_000L
                                    ) {
                                        return@forEach
                                    }
                                    if (!s.snapshotText.isNullOrBlank() &&
                                        bgAge != null && bgAge < 30 * 60_000L
                                    ) {
                                        return@forEach
                                    }
                                    val card = SnapshotText.forCard(trimmed) ?: return@forEach
                                    val stamp = SnapshotText.stampForBackgroundCapture(
                                        s.snapshotUpdatedAtEpochMs,
                                        now,
                                    )
                                    synchronized(lastBgCapture) { lastBgCapture[s.key()] = now }
                                    DiagLog.i(
                                        "tmux",
                                        "capture ok ${s.hostId}/${s.name} bytes=${card.length} " +
                                            "keepAge=${stamp != now}",
                                    )
                                    c.db.snapshots().upsert(
                                        SnapshotEntity(
                                            key = s.key(),
                                            text = card.take(SnapshotText.STORE_CHARS),
                                            updatedAtEpochMs = stamp,
                                        ),
                                    )
                                    synchronized(all) {
                                        val idx = all.indexOfFirst { it.key() == s.key() }
                                        if (idx >= 0) {
                                            all[idx] = all[idx].copy(
                                                snapshotText = card,
                                                snapshotUpdatedAtEpochMs = stamp,
                                            )
                                        }
                                    }
                                    sessions = sessions.map {
                                        if (it.key() == s.key()) {
                                            it.copy(
                                                snapshotText = card,
                                                snapshotUpdatedAtEpochMs = stamp,
                                            )
                                        } else {
                                            it
                                        }
                                    }
                                } ?: DiagLog.w("tmux", "capture empty ${s.hostId}/${s.name}")
                            }
                        }
                    }
                }
                jobs.forEach { it.join() }
            }
            val finalList = if (onlyHostIds != null) {
                // Merge: replace listed hosts, keep everyone else as-is
                val kept = sessions.filter { it.hostId !in onlyHostIds }
                (kept + all).sortedBy { it.name }
            } else {
                all.sortedBy { it.name }
            }
            sessions = finalList
            sessionsFromCache = false
            emptyNeedsInstall = anyMissingTmux || (hosts.isNotEmpty() && finalList.isEmpty())
            persistSessionCache(finalList)
            loading = false
        }
    }

    // Cold start: paint long-lived local snapshot first (no SSH storm).
    // Only if cache is empty do we do a one-shot live refresh.
    LaunchedEffect(Unit) {
        loadSessionsFromCache()
        if (sessions.isEmpty()) {
            val hostList = runCatching { c.hosts.observeHosts().first() }.getOrDefault(emptyList())
            if (hostList.isNotEmpty()) {
                DiagLog.i("sessions", "no cache → first live refresh")
                refreshSessions()
            }
        }
    }

    LaunchedEffect(openLast, hosts) {
        if (!openLast || hosts.isEmpty() || didOpenLast) return@LaunchedEffect
        val last = c.prefs.lastSession.first() ?: return@LaunchedEffect
        val (hostId, sessionName) = last
        val host = hosts.find { it.id == hostId } ?: return@LaunchedEffect
        didOpenLast = true
        val known = sessions.find { it.hostId == host.id && it.name == sessionName }
        attachGen++
        attachStartedForGen = 0L
        attachReadyGen = 0L
        pendingSurfaceSize = null
        termController.close()
        termController.clearDisplay(tr(UiR.string.term_connecting))
        terminalSession = known
            ?: TmuxSession(hostId = host.id, name = sessionName, hostAlias = host.alias)
        DiagLog.i(
            "attach",
            "openLast gen=$attachGen host=${host.alias} session=$sessionName (wait surface)",
        )
    }

    /**
     * Suggestions for the line in [inputTracker]. Inside tmux the outer terminal
     * is always on the alternate screen, so the pane itself is asked whether a
     * full-screen app is running, and paths complete in the pane's directory.
     */
    fun scheduleSuggest(session: TmuxSession) {
        suggestJob?.cancel()
        val prefix = inputTracker.line.takeLast(80)
        if (!autocompleteOn || prefix.isBlank()) {
            suggestions = emptyList()
            return
        }
        val host = hosts.find { it.id == session.hostId }
        if (android.os.SystemClock.uptimeMillis() - lastTuiProbeAt[0] < 3_000L) {
            suggestions = emptyList()
            return
        }
        suggestJob = scope.launch {
            delay(200)
            val compgen = AutocompleteEngine.remoteCompgenCommand(prefix)
            val target = termController.attachedTmuxTarget()
            val tui: Boolean
            val pathHints: List<String>
            if (target != null) {
                val out = withContext(Dispatchers.IO) {
                    c.connections.exec(
                        session.hostId,
                        TmuxControl.paneCompletionCmd(target.first, target.second, compgen),
                    ).getOrNull()
                }.orEmpty()
                val lines = out.lines()
                tui = lines.any { it.trim() == "ALT=1" }
                pathHints = AutocompleteEngine.parseCompgenOutput(
                    lines.filterNot { it.startsWith("ALT=") }.joinToString("\n"),
                )
            } else {
                tui = termController.termuxSession?.getEmulator()?.isAlternateBufferActive == true
                pathHints = if (tui || compgen == null) {
                    emptyList()
                } else {
                    withContext(Dispatchers.IO) {
                        AutocompleteEngine.parseCompgenOutput(
                            c.connections.exec(session.hostId, compgen).getOrNull().orEmpty(),
                        )
                    }
                }
            }
            shellLineActive = !tui
            lastTuiProbeAt[0] = if (tui) android.os.SystemClock.uptimeMillis() else 0L
            suggestions = if (tui) {
                emptyList()
            } else {
                c.autocomplete.suggest(
                    hostId = session.hostId,
                    linePrefix = prefix,
                    tuiMode = false,
                    pathHints = pathHints,
                    vars = buildMap {
                        host?.let {
                            put("host", it.hostname)
                            put("user", it.username)
                        }
                        put("session", session.name)
                    },
                )
            }
        }
    }

    /** Every byte the user sends to the terminal passes through here. */
    fun trackUserInput(bytes: ByteArray) {
        val session = terminalSession ?: return
        val submitted = inputTracker.feed(bytes)
        if (submitted != null && autocompleteOn && shellLineActive) {
            scope.launch { c.autocomplete.recordHistory(session.hostId, submitted) }
        }
        scheduleSuggest(session)
    }
    termController.onUserInput = { trackUserInput(it) }

    /** Snippet body with {{host}}/{{user}}/{{session}} filled in, sent as typed input. */
    fun insertSnippet(sn: Snippet) {
        val session = terminalSession
        val host = session?.let { s -> hosts.find { it.id == s.hostId } }
        val body = AutocompleteEngine.applyVars(
            sn.body,
            buildMap {
                host?.let {
                    put("host", it.hostname)
                    put("user", it.username)
                }
                session?.let { put("session", it.name) }
            },
        )
        if (!termController.trySendDraft(body)) {
            toast(tr(UiR.string.toast_not_connected), force = true)
            return
        }
        if (sn.runOnSelect) scope.launch { termController.sendRawBytes("\r".toByteArray()) }
    }

    fun markSessionOpened(hostId: String, sessionName: String) {
        val key = "$hostId\t$sessionName"
        lastOpened = lastOpened + (key to System.currentTimeMillis())
    }

    fun handleKeyboardKey(key: app.mtmux.core.model.KeyboardKey) {
        scope.launch {
            when (val resolved = KeyboardActionResolver.resolve(key)) {
                ResolvedKeyAction.LatchCtrl -> {
                    termController.toggleCtrlLatch()
                    ctrlLatched = termController.isCtrlLatched()
                }
                ResolvedKeyAction.LatchShift -> {
                    termController.toggleShiftLatch()
                    shiftLatched = termController.isShiftLatched()
                }
                ResolvedKeyAction.LatchAlt -> altLatched = !altLatched
                is ResolvedKeyAction.SendBytes -> {
                    var payload = resolved.data
                    if (ctrlLatched && payload.size == 1) {
                        val ch = payload[0].toInt().toChar()
                        if (ch in 'a'..'z' || ch in 'A'..'Z') {
                            payload = byteArrayOf((ch.lowercaseChar().code - 96).toByte())
                            termController.setCtrlLatch(false)
                            ctrlLatched = false
                        }
                    }
                    if (shiftLatched) {
                        payload = KeyboardActionResolver.applyShiftLatch(payload)
                        termController.setShiftLatch(false)
                        shiftLatched = false
                    }
                    if (altLatched) {
                        // Meta sends ESC first (readline Alt-b / Alt-f, TUI shortcuts).
                        payload = byteArrayOf(0x1B) + payload
                        altLatched = false
                    }
                    termController.sendRawBytes(payload)
                    ctrlLatched = termController.isCtrlLatched()
                    shiftLatched = termController.isShiftLatched()
                    trackUserInput(payload)
                }
                ResolvedKeyAction.None -> Unit
            }
        }
    }

    MtmuxTheme(appearance = appearance, customThemeId = customTheme) {
        CompositionLocalProvider(LocalSnackbarHost provides snackbar) {
            if (showTerminalAppearance) {
                BackHandler { showTerminalAppearance = false }
                TerminalAppearanceScreen(
                    initialTypefaceId = terminalTypefaceId,
                    initialSchemeId = terminalSchemeId,
                    initialFontSizePx = terminalFontSizePx,
                    onCancel = { showTerminalAppearance = false },
                    onSave = { tf, sc, px ->
                        scope.launch {
                            c.prefs.setTerminalTypefaceId(tf)
                            c.prefs.setTerminalSchemeId(sc)
                            c.prefs.setTerminalFontSizePx(px)
                            TerminalSchemeApplier.apply(BuiltinSchemes.byId(sc), termController.termuxSession)
                            showTerminalAppearance = false
                            toast(tr(UiR.string.toast_appearance_saved))
                        }
                    },
                )
                return@CompositionLocalProvider
            }

            if (showPrivacy) {
                BackHandler { showPrivacy = false }
                PrivacyScreen(
                    appVersion = appVersionName,
                    onBack = { showPrivacy = false },
                    onDeleteAllData = {
                        scope.launch {
                            // Secrets first (Keystore-backed), then every file, database and
                            // preference the app owns. The system kills the process afterwards.
                            val secretsOk = runCatching { c.hosts.deleteAllData() }.isSuccess
                            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                            if (!am.clearApplicationUserData()) {
                                DiagLog.e("privacy", "clearApplicationUserData refused secretsOk=$secretsOk")
                                toast(tr(UiR.string.privacy_delete_all_fail), force = true)
                            }
                        }
                    },
                )
                return@CompositionLocalProvider
            }

            if (showSnippets) {
                BackHandler { showSnippets = false }
                SnippetsScreen(
                    snippets = snippetModels,
                    onBack = { showSnippets = false },
                    onSave = { sn ->
                        scope.launch {
                            c.db.snippets().upsert(
                                SnippetEntity(
                                    id = sn.id,
                                    name = sn.name,
                                    body = sn.body,
                                    tagsCsv = sn.tags.joinToString(","),
                                    runOnSelect = sn.runOnSelect,
                                ),
                            )
                            toast(tr(UiR.string.toast_snippet_saved))
                        }
                    },
                    onDelete = { id ->
                        scope.launch {
                            c.db.snippets().delete(id)
                            toast(tr(UiR.string.toast_deleted))
                        }
                    },
                )
                return@CompositionLocalProvider
            }

            if (editingKeyboard != null) {
                val layout = editingKeyboard!!
                BackHandler { editingKeyboard = null }
                KeyboardEditorScreen(
                    initial = layout,
                    readOnly = layout.builtin,
                    onBack = { editingKeyboard = null },
                    onSave = { edited ->
                        scope.launch {
                            c.keyboards.saveCustom(edited)
                                .onSuccess {
                                    toast(tr(UiR.string.toast_saved_name, it.name))
                                    editingKeyboard = null
                                }
                                .onFailure { toast(it.message ?: tr(UiR.string.toast_save_fail)) }
                        }
                    },
                )
                return@CompositionLocalProvider
            }

            if (showKeyboards) {
                BackHandler {
                    if (keyboardImport) keyboardImport = false
                    else showKeyboards = false
                }
                KeyboardListScreen(
                    layouts = keyboardLayouts,
                    activeId = activeKeyboardId,
                    onBack = { showKeyboards = false },
                    onSelectActive = { id ->
                        scope.launch {
                            c.keyboards.setActive(id)
                            toast(tr(UiR.string.toast_switched, keyboardLayouts.find { it.id == id }?.name ?: id))
                        }
                    },
                    onEdit = { editingKeyboard = it },
                    onDuplicate = { src ->
                        scope.launch {
                            c.keyboards.duplicate(src)
                                .onSuccess { toast(tr(UiR.string.toast_copied_as, it.name)) }
                                .onFailure { toast(it.message ?: tr(UiR.string.toast_copy_fail)) }
                        }
                    },
                    onDelete = { layout ->
                        scope.launch {
                            c.keyboards.deleteCustom(layout.id)
                                .onSuccess { toast(tr(UiR.string.toast_deleted)) }
                                .onFailure { toast(it.message ?: tr(UiR.string.toast_delete_fail)) }
                        }
                    },
                    onExport = { layout ->
                        val json = c.keyboards.exportJson(layout)
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("keyboard-json", json))
                        toast(tr(UiR.string.toast_copied_json))
                    },
                    onImport = { keyboardImport = true },
                    onNew = {
                        editingKeyboard = KeyboardLayout(
                            id = "custom-" + java.util.UUID.randomUUID().toString().take(8),
                            name = tr(UiR.string.my_layout),
                            barKeys = BuiltinKeyboards.general.barKeys,
                            expandedKeys = BuiltinKeyboards.general.expandedKeys,
                            builtin = false,
                        )
                    },
                )
                if (keyboardImport) {
                    KeyboardImportDialog(
                        onDismiss = { keyboardImport = false },
                        onImport = { raw ->
                            scope.launch {
                                c.keyboards.importJson(raw)
                                    .onSuccess {
                                        toast(tr(UiR.string.toast_imported_layout, it.name))
                                        keyboardImport = false
                                    }
                                    .onFailure { toast(it.message ?: tr(UiR.string.toast_import_fail)) }
                            }
                        },
                    )
                }
                return@CompositionLocalProvider
            }

            if (showExport) {
                ExportConfigDialog(
                    busy = exportBusy,
                    onDismiss = { showExport = false },
                    onExport = { pwd, includeSecrets, target -> runExport(pwd, includeSecrets, target) },
                )
            }

            if (showImport) {
                ImportConfigDialog(
                    busy = importBusy,
                    loadedText = importLoaded,
                    error = importError,
                    onDismiss = {
                        showImport = false
                        importLoaded = null
                        importError = null
                    },
                    onPickFile = {
                        runCatching { openImportFile.launch(arrayOf("application/json", "text/plain", "*/*")) }
                    },
                    onImport = { blob, pwd -> runImport(blob, pwd) },
                )
            }

            val hk = pendingHostKey
            if (hk != null) {
                HostKeyConfirmDialog(
                    check = hk,
                    onAccept = {
                        c.sshEngine.acceptHostKey(hk)
                        val retry = pendingHostKeyRetry
                        pendingHostKey = null
                        pendingHostKeyRetry = null
                        if (retry != null) {
                            scope.launch {
                                if (connectWithHostKeyUi(retry)) {
                                    toast(tr(UiR.string.toast_trusted, retry.alias))
                                    refreshSessions()
                                }
                            }
                        }
                    },
                    onReject = {
                        pendingHostKey = null
                        pendingHostKeyRetry = null
                        toast(tr(UiR.string.toast_key_rejected))
                    },
                )
            }

            windowsDialog?.let { (sess, wins) ->
                AlertDialog(
                    onDismissRequest = { windowsDialog = null },
                    title = { Text(stringResource(UiR.string.windows_title, sess.name)) },
                    text = {
                        Text(
                            wins.joinToString("\n") {
                                "${it.index}: ${it.name} · " + tr(UiR.string.windows_panes, it.panes) +
                                    if (it.active) " *" else ""
                            }.ifBlank { tr(UiR.string.windows_empty) },
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { windowsDialog = null }) { Text(stringResource(UiR.string.close)) }
                    },
                )
            }

            if (showNewSession) {
                NewSessionDialog(
                    hosts = hosts,
                    initialHostId = hosts.firstOrNull()?.id,
                    onDismiss = { showNewSession = false },
                    onCreate = { h, name, template ->
                        showNewSession = false
                        scope.launch {
                            if (!connectWithHostKeyUi(h)) return@launch
                            c.tmux.newSessionOrAttach(h.id, name)
                                .onSuccess {
                                    DiagLog.i("tmux", "newSession ok alias=${h.alias} name=$name")
                                    if (!template.isNullOrBlank()) {
                                        c.connections.exec(
                                            h.id,
                                            """
${TmuxControl.SOCKET_HELPER}
_sock=${'$'}(_mtmux_find_session ${TmuxControl.shellQuote(name)})
if [ -n "${'$'}_sock" ]; then
  tmux -S "${'$'}_sock" send-keys -t ${TmuxControl.shellQuote(name)} ${TmuxControl.shellQuote(template)} Enter
else
  tmux send-keys -t ${TmuxControl.shellQuote(name)} ${TmuxControl.shellQuote(template)} Enter 2>/dev/null || true
fi
""".trimIndent(),
                                        )
                                    }
                                    // Show the card immediately; do not wait for multi-host refresh
                                    if (sessions.none { it.hostId == h.id && it.name == name }) {
                                        sessions = (
                                            sessions + TmuxSession(
                                                hostId = h.id,
                                                name = name,
                                                hostAlias = h.alias,
                                            )
                                            ).sortedBy { it.name }
                                    }
                                    toast(tr(UiR.string.toast_created, h.alias, name))
                                    // Only re-list this host — field: full refresh connected dead
                                    // "center" for 15s and toasted failure over a successful create.
                                    refreshSessions(
                                        fullProbe = false,
                                        onlyHostIds = setOf(h.id),
                                        quietFailures = false,
                                    )
                                }
                                .onFailure {
                                    DiagLog.e("tmux", "newSession fail alias=${h.alias} name=$name", it)
                                    toast(it.message ?: tr(UiR.string.toast_create_fail))
                                }
                        }
                    },
                )
            }

            moreSession?.let { sess ->
                SessionMoreSheet(
                    session = sess,
                    onDismiss = { moreSession = null },
                    onEnter = {
                        moreSession = null
                        scope.launch {
                            val host = hosts.find { it.id == sess.hostId } ?: return@launch
                            // Enter path uses size-first attach in TerminalScreen — just open UI
                            c.prefs.setLastSession(host.id, sess.name)
                            markSessionOpened(host.id, sess.name)
                            attachGen++
                            attachStartedForGen = 0L
                            attachReadyGen = 0L
                            pendingSurfaceSize = null
                            termController.close()
                            termController.clearDisplay(tr(UiR.string.term_connecting))
                            terminalSession = sess
                        }
                    },
                    onRefreshSnapshot = {
                        scope.launch {
                            val host = hosts.find { it.id == sess.hostId } ?: return@launch
                            if (!connectWithHostKeyUi(host)) return@launch
                            c.tmux.capturePane(host.id, sess.name, sess.socketPath).onSuccess { text ->
                                val card = SnapshotText.forCard(text)
                                if (card == null) {
                                    toast(tr(UiR.string.no_content))
                                    moreSession = null
                                    return@launch
                                }
                                val now = System.currentTimeMillis()
                                c.db.snapshots().upsert(
                                    SnapshotEntity(
                                        key = "${host.id}\t${sess.name}",
                                        text = card.take(SnapshotText.STORE_CHARS),
                                        updatedAtEpochMs = now,
                                    ),
                                )
                                sessions = sessions.map {
                                    if (it.key() == sess.key()) {
                                        it.copy(
                                            snapshotText = card,
                                            snapshotUpdatedAtEpochMs = now,
                                        )
                                    } else {
                                        it
                                    }
                                }
                                toast(tr(UiR.string.toast_snap_updated))
                            }
                            moreSession = null
                        }
                    },
                    onRename = { newName ->
                        scope.launch {
                            val host = hosts.find { it.id == sess.hostId } ?: return@launch
                            if (!connectWithHostKeyUi(host)) return@launch
                            c.tmux.renameSession(host.id, sess.name, newName, sess.socketPath)
                                .onSuccess {
                                    toast(tr(UiR.string.toast_renamed))
                                    moreSession = null
                                    refreshSessions(
                                        onlyHostIds = setOf(host.id),
                                        quietFailures = false,
                                    )
                                }
                                .onFailure { toast(it.message ?: tr(UiR.string.toast_rename_fail)) }
                        }
                    },
                    onKill = {
                        scope.launch {
                            val host = hosts.find { it.id == sess.hostId } ?: return@launch
                            if (!connectWithHostKeyUi(host)) return@launch
                            c.tmux.killSession(host.id, sess.name, sess.socketPath)
                                .onSuccess {
                                    toast(tr(UiR.string.toast_killed))
                                    moreSession = null
                                    // Optimistic remove so dead-host noise cannot resurrect the card
                                    sessions = sessions.filterNot { it.key() == sess.key() }
                                    refreshSessions(
                                        onlyHostIds = setOf(host.id),
                                        quietFailures = false,
                                    )
                                }
                                .onFailure { toast(it.message ?: tr(UiR.string.toast_kill_fail)) }
                        }
                    },
                    onCopyCommand = {
                        val host = hosts.find { it.id == sess.hostId }
                        val sock = sess.socketPath
                        val target = TmuxControl.shellQuote(sess.name)
                        val attachPart = if (!sock.isNullOrBlank()) {
                            "tmux -S ${TmuxControl.shellQuote(sock)} attach -t $target"
                        } else {
                            "tmux attach -t $target"
                        }
                        val cmd = if (host != null) {
                            val portPart = if (host.port != 22) "-p ${host.port} " else ""
                            "ssh -t $portPart${host.username}@${host.hostname} ${TmuxControl.shellQuote(attachPart)}"
                        } else {
                            attachPart
                        }
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("attach", cmd))
                        toast(tr(UiR.string.toast_copied))
                        moreSession = null
                    },
                    onShowWindows = {
                        scope.launch {
                            val host = hosts.find { it.id == sess.hostId } ?: return@launch
                            if (!connectWithHostKeyUi(host)) return@launch
                            c.tmux.listWindows(host.id, sess.name, sess.socketPath).onSuccess {
                                windowsDialog = sess to it
                            }.onFailure { toast(it.message ?: tr(UiR.string.toast_windows_fail)) }
                            moreSession = null
                        }
                    },
                    onMoveSessionUp = {
                        scope.launch {
                            c.prefs.moveSession(
                                sessionKey = sess.key(),
                                up = true,
                                allSessionKeys = sessions.map { it.key() },
                            )
                            toast(tr(UiR.string.toast_session_up))
                            moreSession = null
                        }
                    },
                    onMoveSessionDown = {
                        scope.launch {
                            c.prefs.moveSession(
                                sessionKey = sess.key(),
                                up = false,
                                allSessionKeys = sessions.map { it.key() },
                            )
                            toast(tr(UiR.string.toast_session_down))
                            moreSession = null
                        }
                    },
                    onMoveHostUp = {
                        scope.launch {
                            c.prefs.moveHost(
                                hostId = sess.hostId,
                                up = true,
                                allHostIds = hosts.map { it.id },
                            )
                            toast(tr(UiR.string.toast_host_up))
                            moreSession = null
                        }
                    },
                    onMoveHostDown = {
                        scope.launch {
                            c.prefs.moveHost(
                                hostId = sess.hostId,
                                up = false,
                                allHostIds = hosts.map { it.id },
                            )
                            toast(tr(UiR.string.toast_host_down))
                            moreSession = null
                        }
                    },
                )
            }

            if (installHost != null && installProbe != null) {
                fun closeInstallWizard() {
                    installHost = null
                    installProbe = null
                }
                BackHandler { closeInstallWizard() }
                InstallWizardScreen(
                    hostAlias = installHost!!.alias,
                    probe = installProbe!!,
                    onBack = { closeInstallWizard() },
                    onOpenInstallPty = { cmd ->
                        val h = installHost!!
                        // Keep host for auto-reprobe after install terminal closes
                        installPty = h to cmd
                        installHost = null
                        installProbe = null
                    },
                    onReprobe = {
                        scope.launch {
                            val h = installHost ?: return@launch
                            if (!connectWithHostKeyUi(h)) {
                                toast(tr(UiR.string.toast_connect_probe_fail), force = true)
                                return@launch
                            }
                            toast(tr(UiR.string.toast_checking), force = true)
                            val env = probeEnvFull(h)
                            installProbe = env
                            val updated = h.applyEnvProbe(env)
                            c.hosts.upsert(updated)
                            installHost = updated
                            toast(
                                when {
                                    env.tmux -> tr(UiR.string.env_ready_ssh)
                                    else -> tr(UiR.string.toast_not_ready, envSummary(env))
                                },
                                force = true,
                            )
                        }
                    },
                )
                return@CompositionLocalProvider
            }

            if (installPty != null) {
                val (h, cmd) = installPty!!
                var installSurface by remember(h.id) { mutableStateOf<Pair<Int, Int>?>(null) }
                var installShellReady by remember(h.id) { mutableStateOf(false) }
                var installPhase by remember(h.id) { mutableStateOf(tr(UiR.string.install_prepare)) }
                var installFailed by remember(h.id) { mutableStateOf(false) }
                // *** Critical: same as normal terminal — without this, prepareSession()
                // creates Termux session but UI never recomposes → termuxSession stays null
                // → no TerminalView → no surface size → stuck on "连接中/等待画面" forever.
                @Suppress("UNUSED_VARIABLE")
                val installSessionTick = termuxSessionGen
                fun leaveInstall(reprobe: Boolean) {
                    termController.close()
                    installPty = null
                    ctrlLatched = false
                    shiftLatched = false
                    altLatched = false
                    if (reprobe) {
                        scope.launch {
                            if (!connectWithHostKeyUi(h)) {
                                toast(tr(UiR.string.toast_install_reprobe_fail), force = true)
                                return@launch
                            }
                            val env = runCatching { probeEnvFull(h) }
                                .getOrElse { e ->
                                    toast(tr(UiR.string.toast_reprobe_fail, e.message ?: e.javaClass.simpleName), force = true)
                                    return@launch
                                }
                            c.hosts.upsert(h.applyEnvProbe(env))
                            installProbe = env
                            installHost = (hosts.find { it.id == h.id } ?: h).applyEnvProbe(env)
                            toast(
                                when {
                                    env.tmux -> tr(UiR.string.env_ready_ssh)
                                    installFailed -> tr(UiR.string.toast_install_fail_summary, envSummary(env))
                                    else -> tr(UiR.string.toast_rechecked, envSummary(env))
                                },
                                force = true,
                            )
                        }
                    }
                }
                BackHandler { leaveInstall(reprobe = true) }
                DisposableEffect(h.id) {
                    onDispose { termController.close() }
                }
                // 1) Create Termux session → bumps sessionGeneration → recompose with non-null session
                LaunchedEffect(h.id) {
                    DiagLog.i("pty", "install: prepareSession host=${h.alias}")
                    termController.prepareSession(MtmuxSessionClient())
                    installPhase = tr(UiR.string.install_wait_layout)
                }
                // 2) Size → SSH → shell → run install command
                LaunchedEffect(h.id, cmd) {
                    DiagLog.i("pty", "install: wait surface host=${h.alias} gen=$installSessionTick")
                    val sz = runCatching {
                        kotlinx.coroutines.withTimeout(15_000L) {
                            snapshotFlow { installSurface }
                                .first { it != null && it.first >= 20 && it.second >= 5 }!!
                        }
                    }.getOrElse {
                        // Fallback: still try with a sane default so install is not dead forever.
                        DiagLog.w(
                            "pty",
                            "install: surface timeout host=${h.alias} — fallback 80x24 (${it.message})",
                        )
                        installPhase = tr(UiR.string.install_layout_timeout)
                        80 to 24
                    }
                    installPhase = tr(UiR.string.install_connecting, h.alias)
                    DiagLog.i("pty", "install: connect host=${h.alias} size=${sz.first}x${sz.second}")
                    val connected = runCatching { connectWithHostKeyUi(h) }.getOrDefault(false)
                    if (!connected) {
                        installFailed = true
                        installPhase = tr(UiR.string.install_ssh_fail)
                        toast(tr(UiR.string.install_ssh_fail_toast), force = true)
                        return@LaunchedEffect
                    }
                    installPhase = tr(UiR.string.install_open_shell)
                    // Bootstrap: run install as soon as shell is up (more reliable than delayed sendText).
                    val bootstrap = buildString {
                        append("echo '=== Muxa install ==='; ")
                        append("echo ")
                        append(TmuxControl.shellQuote(cmd))
                        append("; ")
                        append(cmd)
                        append("\n")
                    }
                    val attach = termController.attachRawShell(
                        hostId = h.id,
                        cols = sz.first,
                        rows = sz.second,
                        bootstrap = bootstrap,
                    )
                    if (attach.isFailure) {
                        val err = attach.exceptionOrNull()
                        val msg = err?.message ?: err?.javaClass?.simpleName ?: tr(UiR.string.install_unknown_error)
                        installFailed = true
                        installPhase = tr(UiR.string.install_shell_fail, msg)
                        DiagLog.e("pty", "install: attachRawShell fail host=${h.alias}", err)
                        toast(tr(UiR.string.install_fail_toast, msg), force = true)
                        return@LaunchedEffect
                    }
                    installShellReady = true
                    installPhase = ""
                    DiagLog.i("pty", "install: shell+bootstrap ok host=${h.alias}")
                }
                TerminalScreen(
                    title = tr(UiR.string.install_title, h.alias),
                    termuxSession = termController.termuxSession,
                    suggestions = emptyList(),
                    keyboardLayout = activeKeyboard,
                    availableLayouts = keyboardLayouts,
                    onKeyboardLayoutChange = { id -> scope.launch { c.keyboards.setActive(id) } },
                    fontSizePx = terminalFontSizePx,
                    typefaceId = terminalTypefaceId,
                    schemeId = terminalSchemeId,
                    ctrlLatched = ctrlLatched,
                    onCtrlLatch = { latched ->
                        termController.setCtrlLatch(latched)
                        ctrlLatched = latched
                    },
                    shiftLatched = shiftLatched,
                    onShiftLatch = { latched ->
                        termController.setShiftLatch(latched)
                        shiftLatched = latched
                    },
                    altLatched = altLatched,
                    onAltLatch = { altLatched = it },
                    connecting = !installShellReady,
                    connectingMessage = installPhase.ifBlank { tr(UiR.string.term_connecting) },
                    onBack = { leaveInstall(reprobe = true) },
                    onKeyPress = { handleKeyboardKey(it) },
                    shortcutOrder = shortcutOrder,
                    onShortcutOrderChange = { ids -> scope.launch { c.prefs.setShortcutBarOrder(ids) } },
                    composeEnabled = composeEnabled,
                    onComposeEnabledChange = { on -> scope.launch { c.prefs.setComposeBoxEnabled(on) } },
                    composeHistory = composeHistory,
                    onComposeHistoryChange = { items -> scope.launch { c.prefs.setComposeHistory(items) } },
                    onSendDraft = { text -> termController.trySendDraft(text) },
                    onSuggestion = {},
                    onSurfaceSize = { cols, rows ->
                        installSurface = cols to rows
                        DiagLog.i("pty", "install: surface $cols x $rows")
                    },
                    onResize = { cols, rows ->
                        installSurface = cols to rows
                        if (installShellReady) {
                            scope.launch { termController.resize(cols, rows, applyToBuffer = false) }
                        }
                    },
                    onDisconnect = { leaveInstall(reprobe = true) },
                )
                return@CompositionLocalProvider
            }

            if (showHostEditor) {
                BackHandler {
                    showHostEditor = false
                    editingSavedPassword = null
                    editingSavedPassphrase = null
                }
                HostEditorScreen(
                    initial = editingHost,
                    savedPassword = editingSavedPassword,
                    savedPassphrase = editingSavedPassphrase,
                    onBack = {
                        showHostEditor = false
                        editingSavedPassword = null
                        editingSavedPassphrase = null
                    },
                    onSave = { result ->
                        scope.launch {
                            c.hosts.upsert(
                                host = result.host,
                                privateKeyPem = result.privateKeyPem,
                                password = result.password,
                                keyPassphrase = result.passphrase,
                            )
                            showHostEditor = false
                            editingSavedPassword = null
                            editingSavedPassphrase = null
                            toast(tr(UiR.string.toast_host_saved, result.host.alias))
                        }
                    },
                    onDelete = editingHost?.let { h ->
                        {
                            scope.launch {
                                c.hosts.delete(h.id)
                                showHostEditor = false
                                editingSavedPassword = null
                                editingSavedPassphrase = null
                                toast(tr(UiR.string.toast_deleted))
                            }
                        }
                    },
                )
                return@CompositionLocalProvider
            }

            if (terminalSession != null) {
                val s = terminalSession!!
                val lifecycleOwner = LocalLifecycleOwner.current
                // Entering terminal: clear any list-level snackbar so it can't reappear on exit
                LaunchedEffect(Unit) { dismissToasts() }

                fun leaveTerminal(reason: String) {
                    // Hide third-party IME when leaving terminal (list must not keep keyboard)
                    hideSoftKeyboard()
                    dismissToasts()
                    // Snapshot strategy (0.1.71):
                    // 1) Local VT grab for instant card update (if dense enough).
                    // 2) Remote capture-pane async while SSH stays pooled (must NOT
                    //    invalidateEngine here — that raced quick re-enter and killed
                    //    the next attach with ConnectionException: Disconnected / flicker).
                    // 3) Prefer denser of local / remote / existing.
                    val snapKey = "${s.hostId}\t${s.name}"
                    val hostId = s.hostId
                    val sessionName = s.name
                    val socketPath = s.socketPath
                    val localSnap = termController.captureVisibleSnapshot(maxChars = 2000)
                    val now = System.currentTimeMillis()
                    markSessionOpened(hostId, sessionName)
                    // Capture attach gen after bump so late snapshot jobs never assume exclusive SSH.
                    val leaveGen = attachGen + 1
                    attachEpochGate.set(leaveGen)

                    fun applySnapToList(text: String, at: Long) {
                        val card = SnapshotText.forCard(text) ?: return
                        var hit = false
                        sessions = sessions.map {
                            if (it.hostId == hostId && it.name == sessionName) {
                                hit = true
                                it.copy(
                                    snapshotText = card,
                                    snapshotUpdatedAtEpochMs = at,
                                )
                            } else {
                                it
                            }
                        }
                        if (!hit) {
                            DiagLog.w(
                                "attach",
                                "snapshot list miss host=$hostId session=$sessionName — DB still written",
                            )
                        }
                    }

                    // Show the visible grid immediately — that is the leave-time view.
                    if (SnapshotText.isUseful(localSnap)) {
                        applySnapToList(localSnap!!, now)
                        DiagLog.i(
                            "attach",
                            "snapshot optimistic session=$sessionName chars=${localSnap.length}",
                        )
                    }

                    attachGen = leaveGen
                    attachStartedForGen = 0L
                    attachReadyGen = 0L
                    pendingSurfaceSize = null
                    DiagLog.i("attach", "$reason from $hostId/$sessionName leaveGen=$leaveGen")
                    // Close PTY only; keep SSH engine for reuse + capture-pane.
                    termController.close()
                    terminalSession = null
                    ctrlLatched = false
                    shiftLatched = false
                    altLatched = false
                    suggestJob?.cancel()
                    suggestions = emptyList()
                    inputTracker.reset()
                    shellLineActive = false

                    scope.launch {
                        val remote = runCatching {
                            c.tmux.capturePane(hostId, sessionName, socketPath).getOrNull()
                        }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
                        val best = pickLeaveSnapshot(localSnap, remote)
                        if (best != null) {
                            val t = System.currentTimeMillis()
                            runCatching {
                                c.db.snapshots().upsert(
                                    SnapshotEntity(
                                        key = snapKey,
                                        text = best.take(SnapshotText.STORE_CHARS),
                                        updatedAtEpochMs = t,
                                    ),
                                )
                            }
                            applySnapToList(best, t)
                            DiagLog.i(
                                "attach",
                                "snapshot saved on leave session=$sessionName " +
                                    "local=${localSnap?.length ?: 0} remote=${remote?.length ?: 0} " +
                                    "best=${best.length} preview=${best.take(60).replace("\n", "⏎")}",
                            )
                        } else {
                            DiagLog.w(
                                "attach",
                                "snapshot empty on leave session=$sessionName " +
                                    "local=${localSnap?.length ?: 0} remote=${remote?.length ?: 0}",
                            )
                        }
                        // Intentionally no invalidateEngine: rapid session switch reuses SSH.
                    }
                }

                // Edge-swipe / system back → session list (not home screen)
                BackHandler { leaveTerminal("back") }

                // Prepare Termux session FIRST (Compose runs LaunchedEffects in declaration
                // order). Attach must capture channelEpoch after prepare bumps it.
                LaunchedEffect(attachGen, s.hostId, s.name) {
                    termController.prepareSession(MtmuxSessionClient())
                }

                // Resume after background only — NOT the first composition enter.
                // Bug: ON_RESUME fires when the terminal composable mounts; healthCheck fails
                // (PTY not up yet) and raced size-ready attach → double attachTmux, double WINCH,
                // and DA2 "41;320;0c" junk in the app input line.
                DisposableEffect(lifecycleOwner, s.name, s.hostId) {
                    var sawPause = false
                    val obs = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_PAUSE -> {
                                sawPause = true
                                val alive = termController.isLinkAlive()
                                DiagLog.i(
                                    "attach",
                                    "ON_PAUSE session=${s.name} link=$alive keepMin=$backgroundKeepMinutes",
                                )
                                if (alive) {
                                    BackgroundKeepAlive.enter(
                                        ctx,
                                        "${s.hostAlias ?: s.hostId} · ${s.name}",
                                        backgroundKeepMinutes,
                                    )
                                }
                            }
                            Lifecycle.Event.ON_RESUME -> {
                                BackgroundKeepAlive.exit(ctx)
                                if (!sawPause) {
                                    // First enter after open — attach is owned by LaunchedEffect.
                                    DiagLog.i("attach", "ON_RESUME skip (initial enter) session=${s.name}")
                                    return@LifecycleEventObserver
                                }
                                if (attachReadyGen != attachGen) {
                                    DiagLog.i(
                                        "attach",
                                        "ON_RESUME skip (attach not ready) session=${s.name}",
                                    )
                                    return@LifecycleEventObserver
                                }
                                DiagLog.i("attach", "ON_RESUME session=${s.name}")
                                val resumeGen = attachGen
                                val resumeGate = attachEpochGate.get()
                                val resumeKey = s.key()
                                val resumeChannelEpoch = termController.currentChannelEpoch()
                                scope.launch {
                                    fun stillHere(): Boolean {
                                        // Atomic gates only — Compose State lags in IO coroutines
                                        // (orphan reattach painted previous session into next).
                                        if (attachEpochGate.get() != resumeGate) return false
                                        if (termController.currentChannelEpoch() != resumeChannelEpoch) {
                                            return false
                                        }
                                        // Best-effort UI identity (may lag; epoch is authoritative).
                                        if (attachGen != resumeGen) return false
                                        if (terminalSession?.key() != resumeKey) return false
                                        return true
                                    }

                                    val host = hosts.find { it.id == s.hostId }
                                    val sshOk = host != null && c.sshEngine.isConnected(host.id)
                                    val live = termController.healthCheckOnResume(sshOk)
                                    if (!stillHere()) {
                                        DiagLog.i("attach", "ON_RESUME abort after health (left)")
                                        return@launch
                                    }
                                    if (live) {
                                        DiagLog.i("attach", "ON_RESUME link kept session=${s.name}")
                                        termController.forceRedraw()
                                        val sz = pendingSurfaceSize
                                        if (sz != null && attachStartedForGen == attachGen) {
                                            termController.resize(
                                                sz.first,
                                                sz.second,
                                                applyToBuffer = true,
                                            )
                                        }
                                        return@launch
                                    }
                                    if (host == null) {
                                        toast(tr(UiR.string.toast_disconnected_missing))
                                        return@launch
                                    }
                                    DiagLog.w("attach", "ON_RESUME reattach session=${s.name}")
                                    toast(tr(UiR.string.toast_reconnecting))
                                    c.connections.invalidateEngine(host.id)
                                    if (!stillHere()) return@launch
                                    if (!connectWithHostKeyUi(host)) {
                                        if (stillHere()) toast(tr(UiR.string.toast_reconnect_auth))
                                        return@launch
                                    }
                                    if (!stillHere()) {
                                        DiagLog.i("attach", "ON_RESUME abort after connect (left)")
                                        return@launch
                                    }
                                    // Capture size at call time; IME may change during attach —
                                    // never re-apply this stale pair after success (see below).
                                    val szOpen = pendingSurfaceSize ?: (80 to 24)
                                    val colsOpen = szOpen.first.coerceAtLeast(20)
                                    val rowsOpen = szOpen.second.coerceAtLeast(5)
                                    val r = termController.attachTmux(
                                        hostId = host.id,
                                        sessionName = s.name,
                                        cols = colsOpen,
                                        rows = rowsOpen,
                                        socketPath = s.socketPath,
                                        transport = TransportPreference.SSH,
                                        hostname = host.hostname,
                                        sshPort = host.port,
                                        username = host.username,
                                        expectedEpoch = resumeChannelEpoch,
                                    )
                                    if (!stillHere()) {
                                        // User left while reattach ran — tear down orphan PTY.
                                        DiagLog.i("attach", "ON_RESUME abort after attach (left)")
                                        // Only close if we still own this epoch (don't kill next session).
                                        if (termController.currentChannelEpoch() == resumeChannelEpoch) {
                                            termController.close()
                                        }
                                        return@launch
                                    }
                                    r.onSuccess { outcome ->
                                        if (!stillHere()) {
                                            DiagLog.i("attach", "ON_RESUME drop stale success")
                                            if (termController.currentChannelEpoch() == resumeChannelEpoch) {
                                                termController.close()
                                            }
                                            return@onSuccess
                                        }
                                        // LIVE surface only. Field: goIme mid-reattach → WINCH 46,
                                        // then resize(open 82) put Grok input under the keyboard.
                                        val live = pendingSurfaceSize
                                        val cLive = live?.first?.coerceAtLeast(20) ?: colsOpen
                                        val rLive = live?.second?.coerceAtLeast(5) ?: rowsOpen
                                        DiagLog.i(
                                            "attach",
                                            "ON_RESUME reattach ok ${outcome.transportUsed} " +
                                                "session=${s.name} open=${colsOpen}x$rowsOpen " +
                                                "live=${cLive}x$rLive",
                                        )
                                        toast(tr(UiR.string.toast_reconnected, outcome.transportUsed))
                                        launch {
                                            runCatching {
                                                val res = c.tmux.probeHostResources(host.id)
                                                c.hosts.updateResources(host.id, res)
                                            }
                                        }
                                        termController.resize(cLive, rLive, applyToBuffer = true)
                                        termController.forceRedraw()
                                        launch {
                                            delay(300)
                                            if (!stillHere()) return@launch
                                            val s2 = pendingSurfaceSize ?: return@launch
                                            if (s2.first >= 20 && s2.second >= 5) {
                                                DiagLog.i(
                                                    "attach",
                                                    "ON_RESUME post-paint reassert " +
                                                        "${s2.first}x${s2.second}",
                                                )
                                                termController.resize(
                                                    s2.first,
                                                    s2.second,
                                                    applyToBuffer = true,
                                                )
                                                termController.forceRedraw()
                                            }
                                        }
                                    }.onFailure {
                                        if (it.message?.contains("aborted", ignoreCase = true) == true) {
                                            return@onFailure
                                        }
                                        DiagLog.e("attach", "ON_RESUME reattach fail", it)
                                        if (stillHere()) toast(tr(UiR.string.toast_reconnect_fail, it.message ?: ""))
                                    }
                                }
                            }
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(obs)
                    BackgroundKeepAlive.onExpired = {
                        scope.launch(Dispatchers.IO) {
                            termController.releaseLinkInBackground()
                            c.connections.invalidateEngine(s.hostId)
                        }
                    }
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(obs)
                        BackgroundKeepAlive.onExpired = null
                        BackgroundKeepAlive.exit(ctx)
                        DiagLog.i("attach", "dispose terminal ${s.hostId}/${s.name}")
                        termController.close()
                    }
                }
                // A link that died in the background has nothing left to hold.
                LaunchedEffect(Unit) {
                    termController.linkAlive.collect { alive ->
                        if (!alive) BackgroundKeepAlive.exit(ctx)
                    }
                }
                // Size-first attach: wait for surface size WITHOUT re-keying on every resize.
                // Keying on pendingSurfaceSize cancelled mid-SSH when IME opened (black screen).
                LaunchedEffect(attachGen, s.name, s.hostId) {
                    val gen = attachGen
                    val enterGate = attachEpochGate.get()
                    if (attachStartedForGen == gen) return@LaunchedEffect
                    val host = hosts.find { it.id == s.hostId }
                    if (host == null) {
                        DiagLog.e("attach", "host missing id=${s.hostId}")
                        leaveTerminal("no-host")
                        return@LaunchedEffect
                    }
                    // Wait for first valid surface size; size changes later only WINCH.
                    // Field log 0.1.93: enter grok-cli2 → goIme during SSH → first paint at
                    // 110x82 then WINCH 110x46 left Grok input chrome missing until re-enter.
                    // Hold briefly so an early IME open can shrink rows before we attach.
                    var sz = snapshotFlow { pendingSurfaceSize }
                        .filterNotNull()
                        .first { it.first >= 20 && it.second >= 5 }
                    run {
                        val waitDeadline = System.currentTimeMillis() + 450L
                        var lastChange = System.currentTimeMillis()
                        var last = sz
                        while (System.currentTimeMillis() < waitDeadline) {
                            delay(70)
                            if (attachEpochGate.get() != enterGate || attachGen != gen) return@LaunchedEffect
                            val cur = pendingSurfaceSize
                            if (cur != null && cur.first >= 20 && cur.second >= 5) {
                                if (cur != last) {
                                    last = cur
                                    lastChange = System.currentTimeMillis()
                                    DiagLog.i(
                                        "attach",
                                        "surface size settle ${sz.first}x${sz.second}→${cur.first}x${cur.second}",
                                    )
                                    sz = cur
                                } else if (System.currentTimeMillis() - lastChange >= 140L) {
                                    break
                                }
                            }
                        }
                    }
                    fun stillEntering(): Boolean {
                        if (attachEpochGate.get() != enterGate) return false
                        if (attachGen != gen) return false
                        return true
                    }
                    if (!stillEntering()) return@LaunchedEffect
                    if (attachStartedForGen == gen) return@LaunchedEffect
                    attachStartedForGen = gen
                    val (cols, rows) = sz
                    DiagLog.i("attach", "size-ready attach ${cols}x$rows gen=$gen session=${s.name}")
                    val t0 = System.currentTimeMillis()
                    // Prefer warm pool; on Broken transport / EOF, force-reconnect once and retry.
                    if (!connectWithHostKeyUi(host)) {
                        if (stillEntering()) {
                            termController.close()
                            terminalSession = null
                            attachStartedForGen = 0L
                        }
                        return@LaunchedEffect
                    }
                    if (!stillEntering()) return@LaunchedEffect
                    // Capture AFTER prepareSession (declared above) has bumped epoch.
                    val channelEpoch = termController.currentChannelEpoch()
                    /** Always use the live surface size — IME often opens mid-SSH (log:
                     * size-ready 110x82, goIme during attach, retry still used 82, then
                     * post-attach 46; Grok alt first paint lost the bottom input chrome). */
                    fun liveAttachSize(): Pair<Int, Int> {
                        val live = pendingSurfaceSize
                        val c0 = live?.first?.takeIf { it >= 20 } ?: cols
                        val r0 = live?.second?.takeIf { it >= 5 } ?: rows
                        return c0 to r0
                    }
                    suspend fun doAttach(): Result<TerminalSessionController.AttachOutcome> {
                        val (aCols, aRows) = liveAttachSize()
                        if (aCols != cols || aRows != rows) {
                            DiagLog.i(
                                "attach",
                                "live size for attach was ${cols}x$rows now ${aCols}x$aRows",
                            )
                        }
                        return termController.attachTmux(
                            hostId = host.id,
                            sessionName = s.name,
                            cols = aCols,
                            rows = aRows,
                            socketPath = s.socketPath,
                            transport = TransportPreference.SSH,
                            hostname = host.hostname,
                            sshPort = host.port,
                            username = host.username,
                            expectedEpoch = channelEpoch,
                        )
                    }
                    var result = doAttach()
                    if (result.isFailure &&
                        termController.isTransportBroken(result.exceptionOrNull()) &&
                        stillEntering()
                    ) {
                        DiagLog.w(
                            "attach",
                            "transport broken on first try session=${s.name} — " +
                                "invalidate + reconnect + retry",
                        )
                        c.connections.invalidateEngine(host.id)
                        if (!connectWithHostKeyUi(host) || !stillEntering()) {
                            // fall through with original failure if reconnect impossible
                        } else {
                            result = doAttach()
                        }
                    }
                    result.onSuccess { outcome ->
                        // Atomic gate first — Compose attachGen can lag and let orphans through.
                        if (!stillEntering() ||
                            termController.currentChannelEpoch() != channelEpoch
                        ) {
                            DiagLog.i(
                                "attach",
                                "ok but left/stale gen=$gen gate=${attachEpochGate.get()} " +
                                    "epoch now=${termController.currentChannelEpoch()} — close only if still ours",
                            )
                            if (termController.currentChannelEpoch() == channelEpoch) {
                                termController.close()
                            }
                            return@onSuccess
                        }
                        val used = liveAttachSize()
                        DiagLog.i(
                            "attach",
                            "ok gen=$gen ${outcome.transportUsed} " +
                                "${System.currentTimeMillis() - t0}ms ${used.first}x${used.second} " +
                                "session=${s.name}",
                        )
                        attachReadyGen = gen
                        scope.launch {
                            runCatching {
                                val res = c.tmux.probeHostResources(host.id)
                                c.hosts.updateResources(host.id, res)
                            }.onFailure {
                                DiagLog.w("stats", "resources fail host=${host.alias} ${it.message}")
                            }
                        }
                        // Sync size + redraw immediately, then again after first alt paint
                        // window so Grok/Claude can re-layout the bottom input chrome.
                        suspend fun reassertSurface(tag: String) {
                            if (!stillEntering()) return
                            val s0 = pendingSurfaceSize ?: return
                            if (s0.first < 20 || s0.second < 5) return
                            DiagLog.i(
                                "attach",
                                "$tag size reassert ${s0.first}x${s0.second}",
                            )
                            termController.resize(s0.first, s0.second, applyToBuffer = true)
                            termController.forceRedraw()
                        }
                        reassertSurface("post-attach")
                        // Two delayed passes: IME settle + TUI first full redraw.
                        launch {
                            delay(320)
                            reassertSurface("post-paint-1")
                            delay(280)
                            reassertSurface("post-paint-2")
                        }
                    }.onFailure {
                        // User left while PTY was opening — expected, not an error toast.
                        if (it.message?.contains("aborted", ignoreCase = true) == true) {
                            DiagLog.w("attach", "aborted gen=$gen session=${s.name}: ${it.message}")
                            return@onFailure
                        }
                        DiagLog.e("attach", "fail gen=$gen session=${s.name}", it)
                        if (stillEntering()) {
                            // Poisoned pool: drop so the next tap opens a clean TCP session.
                            if (termController.isTransportBroken(it)) {
                                c.connections.invalidateEngine(host.id)
                            }
                            toast(tr(UiR.string.toast_enter_fail, it.message ?: ""))
                            termController.close()
                            terminalSession = null
                            attachStartedForGen = 0L
                            attachReadyGen = 0L
                        }
                    }
                }
                // Force recompose when session object is recreated
                @Suppress("UNUSED_VARIABLE")
                val sessionTick = termuxSessionGen
                // Live RTT every 10s while attached (title: host · sess · SSH · 42ms).
                LaunchedEffect(attachReadyGen, s.hostId, s.name) {
                    if (attachReadyGen != attachGen) return@LaunchedEffect
                    val hostId = s.hostId
                    while (true) {
                        if (attachReadyGen != attachGen) return@LaunchedEffect
                        // Nobody reads the title in the background; SSH keepalives watch the link.
                        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            runCatching { termController.refreshLatencyMs(hostId) }
                        }
                        delay(10_000L)
                    }
                }
                val titleText = buildString {
                    append(s.hostAlias ?: s.hostId)
                    append(" · ")
                    append(s.name)
                    append(" · ")
                    append(transportLabel)
                    latencyMs?.let { append(" · "); append(it); append("ms") }
                    // Ephemeral only (断开/错误/同步) — never "SSH 123ms".
                    if (termStatus.isNotBlank() &&
                        !termStatus.contains("ms", ignoreCase = true) &&
                        !termStatus.equals(transportLabel, ignoreCase = true)
                    ) {
                        append(" · ")
                        append(termStatus)
                    }
                }
                TerminalScreen(
                    title = titleText,
                    termuxSession = termController.termuxSession,
                    ctrlLatched = ctrlLatched,
                    onCtrlLatch = { latched ->
                        termController.setCtrlLatch(latched)
                        ctrlLatched = latched
                    },
                    shiftLatched = shiftLatched,
                    onShiftLatch = { latched ->
                        termController.setShiftLatch(latched)
                        shiftLatched = latched
                    },
                    altLatched = altLatched,
                    onAltLatch = { altLatched = it },
                    snippets = snippetModels,
                    onSnippet = { insertSnippet(it) },
                    autocompleteEnabled = autocompleteOn,
                    keyboardLayout = activeKeyboard,
                    availableLayouts = keyboardLayouts,
                    onKeyboardLayoutChange = { id -> scope.launch { c.keyboards.setActive(id) } },
                    fontSizePx = terminalFontSizePx,
                    typefaceId = terminalTypefaceId,
                    schemeId = terminalSchemeId,
                    suggestions = suggestions,
                    connecting = attachReadyGen != attachGen,
                    connectingMessage = termStatus,
                    onAltFingerScroll = { up -> termController.onAltFingerScroll(up) },
                    isAltPaneInline = { termController.isAltPaneInline() },
                    onBack = { leaveTerminal("back") },
                    onKeyPress = { handleKeyboardKey(it) },
                    shortcutOrder = shortcutOrder,
                    onShortcutOrderChange = { ids -> scope.launch { c.prefs.setShortcutBarOrder(ids) } },
                    composeEnabled = composeEnabled,
                    onComposeEnabledChange = { on -> scope.launch { c.prefs.setComposeBoxEnabled(on) } },
                    composeHistory = composeHistory,
                    onComposeHistoryChange = { items -> scope.launch { c.prefs.setComposeHistory(items) } },
                    onSendDraft = { text -> termController.trySendDraft(text) },
                    onSuggestion = { sug ->
                        val body = ComposeHistory.payload(sug.insertion)
                        // Paths complete the last token; other suggestions replace the typed line.
                        val typed = if (sug.source == SuggestionSource.PATH) 0 else {
                            inputTracker.line.let { it.codePointCount(0, it.length) }
                        }
                        scope.launch {
                            if (typed > 0) {
                                val erase = ByteArray(typed) { 0x7F }
                                termController.sendRawBytes(erase)
                                inputTracker.feed(erase)
                            }
                            if (termController.trySendDraft(body)) {
                                if (sug.runAfterInsert) {
                                    termController.sendRawBytes("\r".toByteArray())
                                    trackUserInput("\r".toByteArray())
                                }
                            }
                            suggestJob?.cancel()
                            suggestions = emptyList()
                        }
                    },
                    onSurfaceSize = { cols, rows ->
                        pendingSurfaceSize = cols to rows
                    },
                    onResize = { cols, rows ->
                        // Track geometry for size-first attach; remote WINCH is driven by
                        // TerminalSession sizeListener (after TerminalView debounce settles).
                        pendingSurfaceSize = cols to rows
                    },
                    onDisconnect = { leaveTerminal("disconnect") },
                    onToggleAutocomplete = {
                        scope.launch {
                            c.prefs.setAutocompleteEnabled(!autocompleteOn)
                            toast(tr(if (!autocompleteOn) UiR.string.toast_complete_on else UiR.string.toast_complete_off))
                        }
                    },
                    onPaste = {
                        scope.launch {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val text = cm.primaryClip
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)
                                ?.coerceToText(ctx)
                                ?.toString()
                            if (!text.isNullOrEmpty()) {
                                termController.pasteText(text)
                            } else {
                                toast(tr(UiR.string.toast_clipboard_empty))
                            }
                        }
                    },
                    onOpenKeyboardSettings = { showKeyboards = true },
                )
                return@CompositionLocalProvider
            }

            val nav = rememberNavController()
            val backStack by nav.currentBackStackEntryAsState()
            val current = backStack?.destination?.route

            val chrome = LocalMtmuxTheme.current.colors
            Scaffold(
                snackbarHost = {
                    // Always charcoal bar + pure white text (light/dark).
                    // Do not use theme secondaryContainer (pale lavender on light → "blank toast").
                    // Explicit Text(color=White): Typography used to bake onSurface into
                    // bodyMedium and Compose prefers style.color over contentColor.
                    val toastBar = Color(0xFF32303A)
                    val toastInk = Color(0xFFFFFFFF)
                    val toastAction = Color(0xFFD0BCFF)
                    SnackbarHost(snackbar) { data ->
                        Snackbar(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            action = data.visuals.actionLabel?.let { label ->
                                {
                                    TextButton(onClick = { data.performAction() }) {
                                        Text(label, color = toastAction, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            },
                            dismissAction = if (data.visuals.withDismissAction) {
                                {
                                    TextButton(onClick = { data.dismiss() }) {
                                        Text(stringResource(UiR.string.close), color = toastInk)
                                    }
                                }
                            } else {
                                null
                            },
                            containerColor = toastBar,
                            contentColor = toastInk,
                            actionContentColor = toastAction,
                            dismissActionContentColor = toastInk,
                        ) {
                            Text(
                                text = data.visuals.message,
                                color = toastInk,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                },
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = chrome.background,
                bottomBar = {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        tonalElevation = 0.dp,
                        containerColor = chrome.surface,
                        contentColor = chrome.onSurface,
                        modifier = Modifier.padding(bottom = 4.dp),
                    ) {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = current == tab.route,
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo(Tab.Sessions.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        when (tab) {
                                            Tab.Sessions -> Icons.Default.List
                                            Tab.Hosts -> Icons.Default.Computer
                                            Tab.Settings -> Icons.Default.Settings
                                        },
                                        contentDescription = stringResource(tab.titleRes),
                                    )
                                },
                                label = { Text(stringResource(tab.titleRes)) },
                                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                    selectedIconColor = chrome.onSecondaryContainer,
                                    selectedTextColor = chrome.onSurface,
                                    indicatorColor = chrome.secondaryContainer,
                                    unselectedIconColor = chrome.onSurfaceMuted,
                                    unselectedTextColor = chrome.onSurfaceMuted,
                                ),
                            )
                        }
                    }
                },
            ) { padding ->
                NavHost(
                    navController = nav,
                    startDestination = Tab.Sessions.route,
                    modifier = Modifier.padding(padding),
                ) {
                    composable(Tab.Sessions.route) {
                        SessionsScreen(
                            sessions = sessions,
                            hosts = hosts,
                            loading = loading,
                            message = when {
                                loading -> tr(UiR.string.refreshing)
                                sessionsFromCache && sessions.isNotEmpty() -> tr(UiR.string.cache_hint)
                                else -> null
                            },
                            emptyNeedsInstall = emptyNeedsInstall,
                            listMode = sessionListMode,
                            lastOpened = lastOpened,
                            hostOrder = hostOrder,
                            sessionOrder = sessionOrder,
                            onListModeChange = { mode ->
                                scope.launch { c.prefs.setSessionListMode(mode) }
                            },
                            onRefresh = { refreshSessions() },
                            onOpen = { session ->
                                scope.launch {
                                    val host = hosts.find { it.id == session.hostId }
                                    if (host == null) {
                                        toast(tr(UiR.string.toast_host_missing))
                                        return@launch
                                    }
                                    val gen = ++attachGen
                                    attachStartedForGen = 0L
                                    attachReadyGen = 0L
                                    pendingSurfaceSize = null
                                    termController.close()
                                    termController.clearDisplay(tr(UiR.string.term_connecting))
                                    c.prefs.setLastSession(host.id, session.name)
                                    markSessionOpened(host.id, session.name)
                                    // Show terminal UI first; LaunchedEffect attaches after real size
                                    terminalSession = session
                                    DiagLog.i(
                                        "attach",
                                        "enter gen=$gen host=${host.alias} session=${session.name} " +
                                            "(wait surface size) socket=${session.socketPath}",
                                    )
                                }
                            },
                            liveSessionKey = terminalSession?.key(),
                            onMore = { moreSession = it },
                            onNewSession = { showNewSession = true },
                            onOpenInstall = {
                                scope.launch {
                                    // The empty list means tmux is missing somewhere: check that host first.
                                    val h = hosts.firstOrNull { it.tmuxInstalled == false }
                                        ?: hosts.firstOrNull()
                                    if (h == null) {
                                        toast(tr(UiR.string.toast_add_host_first))
                                        return@launch
                                    }
                                    if (!connectWithHostKeyUi(h)) return@launch
                                    toast(tr(UiR.string.toast_checking))
                                    installProbe = probeEnvFull(h)
                                    installHost = h
                                }
                            },
                            onAddHost = {
                                nav.navigate(Tab.Hosts.route)
                                editingHost = null
                                editingSavedPassword = null
                                editingSavedPassphrase = null
                                showHostEditor = true
                            },
                        )
                    }
                    composable(Tab.Hosts.route) {
                        HostsScreen(
                            hosts = hosts,
                            onAdd = {
                                editingHost = null
                                editingSavedPassword = null
                                editingSavedPassphrase = null
                                showHostEditor = true
                            },
                            onOpen = { host ->
                                scope.launch {
                                    // Prefill password as masked ciphertext so transport-only
                                    // edits (AUTO/SSH/Mosh) can save without retyping.
                                    val pw = c.hosts.password(host.id)
                                    val pp = c.hosts.keyPassphrase(host.id)
                                    editingSavedPassword = pw
                                    editingSavedPassphrase = pp
                                    editingHost = if (!pw.isNullOrEmpty() && !host.hasPassword) {
                                        // Repair legacy hosts that have a secret but flag was false
                                        host.copy(hasPassword = true).also {
                                            c.hosts.upsert(it)
                                        }
                                    } else {
                                        host
                                    }
                                    showHostEditor = true
                                }
                            },
                            onProbe = { host ->
                                scope.launch {
                                    if (!connectWithHostKeyUi(host)) return@launch
                                    toast(tr(UiR.string.toast_host_checking, host.alias))
                                    val env = probeEnvFull(host)
                                    c.hosts.upsert(host.applyEnvProbe(env))
                                    toast("${host.alias}: ${envSummary(env)}")
                                    if (!env.tmux) {
                                        installProbe = env
                                        installHost = host
                                    }
                                }
                            },
                            onInstall = { host ->
                                scope.launch {
                                    if (!connectWithHostKeyUi(host)) return@launch
                                    toast(tr(UiR.string.toast_checking_udp))
                                    installProbe = probeEnvFull(host)
                                    installHost = host
                                }
                            },
                        )
                    }
                    composable(Tab.Settings.route) {
                        SettingsScreen(
                            appearance = appearance,
                            appLanguage = appLanguage,
                            customThemeId = customTheme,
                            autocompleteEnabled = autocompleteOn,
                            openLastSession = openLast,
                            terminalFontSizePx = terminalFontSizePx,
                            terminalTermType = terminalTermType,
                            terminalBufferLines = terminalBufferLines,
                            preventSleeping = preventSleeping,
                            backgroundKeepMinutes = backgroundKeepMinutes,
                            appVersion = appVersionName,
                            onAppearance = { scope.launch { c.prefs.setAppearance(it) } },
                            onAppLanguage = { lang ->
                                scope.launch {
                                    AppLocale.write(activity, lang)
                                    c.prefs.setAppLanguage(lang)
                                    activity.recreate()
                                }
                            },
                            onCustomTheme = {
                                scope.launch {
                                    c.prefs.setAppearance(AppearanceMode.CUSTOM)
                                    c.prefs.setCustomThemeId(it)
                                }
                            },
                            onAutocomplete = { scope.launch { c.prefs.setAutocompleteEnabled(it) } },
                            onOpenLast = { scope.launch { c.prefs.setOpenLastSession(it) } },
                            onTerminalFontSize = { px ->
                                scope.launch { c.prefs.setTerminalFontSizePx(px) }
                            },
                            onOpenTerminalAppearance = { showTerminalAppearance = true },
                            onTerminalTermType = { t ->
                                scope.launch { c.prefs.setTerminalTermType(t) }
                            },
                            onTerminalBufferLines = { n ->
                                scope.launch { c.prefs.setTerminalBufferLines(n) }
                            },
                            onPreventSleeping = { v ->
                                scope.launch { c.prefs.setPreventSleeping(v) }
                            },
                            onBackgroundKeepMinutes = { minutes ->
                                scope.launch { c.prefs.setBackgroundKeepMinutes(minutes) }
                                if (minutes > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                                    PackageManager.PERMISSION_GRANTED
                                ) {
                                    requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onOpenSnippets = { showSnippets = true },
                            onOpenKeyboards = { showKeyboards = true },
                            onOpenPrivacy = { showPrivacy = true },
                            onExport = { showExport = true },
                            onImport = {
                                importLoaded = null
                                importError = null
                                showImport = true
                            },
                            onFeedbackLog = { note ->
                                scope.launch {
                                    dismissToasts()
                                    toast(tr(UiR.string.toast_feedback_uploading), force = true)
                                    val result = LogHubClient.uploadFeedback(ctx, appVersionName, note)
                                    result.onSuccess { up ->
                                        LogHubClient.markUploaded(ctx, up.id)
                                        DiagLog.i(
                                            "diag",
                                            "feedback uploaded id=${up.id} status=${up.status}",
                                        )
                                        val ref = up.id.take(8).ifBlank { "ok" }
                                        toast(tr(UiR.string.toast_feedback_ok, ref), force = true)
                                    }.onFailure { err ->
                                        DiagLog.e("diag", "feedback upload fail", err)
                                        val msg = when {
                                            err is LogHubCooldownException ->
                                                tr(UiR.string.toast_feedback_wait, err.waitSeconds)
                                            err !is LogHubHttpException -> tr(UiR.string.toast_feedback_fail)
                                            err.code == 401 || err.code == 403 -> tr(UiR.string.toast_feedback_auth)
                                            err.code == 429 -> tr(UiR.string.toast_feedback_rate_limited)
                                            err.code == 503 -> tr(UiR.string.toast_feedback_server_busy)
                                            else -> tr(UiR.string.toast_feedback_fail)
                                        }
                                        toast(msg, force = true)
                                    }
                                }
                            },
                            logHubAvailable = LogHubClient.isConfigured,
                            onFeedbackGitHub = { note -> exportRedactedDiag(note) },
                            onExportDiagLog = { exportRedactedDiag(null) },
                            onOpenGitHubIssues = { openUrl(GitHubIssue.ISSUES_URL) },
                            onClearDiagLog = {
                                DiagLog.clear()
                                // Short toast only; dismiss any leftover so it won't reappear after terminal
                                dismissToasts()
                                toast(tr(UiR.string.toast_diag_cleared))
                            },
                        )
                    }
                }
            }
        }
    }
}

/** InputStream.readNBytes is API 33+; read at most [limit] bytes. */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (out.size() < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n <= 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
