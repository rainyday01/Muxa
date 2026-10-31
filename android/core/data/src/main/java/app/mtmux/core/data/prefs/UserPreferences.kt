package app.mtmux.core.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.mtmux.core.model.AppLanguage
import app.mtmux.core.model.AppearanceMode
import app.mtmux.core.model.ComposeHistory
import app.mtmux.core.model.SessionListMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("mtmux_prefs")

class UserPreferences(private val context: Context) {
    private val appearanceKey = stringPreferencesKey("appearance")
    private val appLanguageKey = stringPreferencesKey("app_language")
    private val customThemeKey = stringPreferencesKey("custom_theme_id")
    private val openLastSessionKey = booleanPreferencesKey("open_last_session")
    private val autocompleteKey = booleanPreferencesKey("autocomplete_enabled")
    private val lastHostKey = stringPreferencesKey("last_host_id")
    private val lastSessionKey = stringPreferencesKey("last_session_name")
    private val sessionListModeKey = stringPreferencesKey("session_list_mode")
    /** Comma-separated host ids (user order). */
    private val hostOrderKey = stringPreferencesKey("host_order")
    /** Newline-separated session keys "hostId\\tname" (user order). */
    private val sessionOrderKey = stringPreferencesKey("session_order")
    /**
     * Terminal font size in **pixels** (Termux KEY_FONTSIZE style).
     * Applied directly to [com.termux.view.TerminalView.setTextSize].
     * Min 13 matches 0.1.22 hardcoded size.
     */
    private val terminalFontSizePxKey = intPreferencesKey("terminal_font_size_px")
    /** Legacy sp key — read once for migration, then ignored. */
    private val legacyFontSizeSpKey = intPreferencesKey("terminal_font_size_sp")
    /** Builtin scheme id (e.g. termius_dark, dracula). */
    private val terminalSchemeIdKey = stringPreferencesKey("terminal_scheme_id")
    /** Builtin typeface id (e.g. source_code_pro, jetbrains_mono). */
    private val terminalTypefaceIdKey = stringPreferencesKey("terminal_typeface_id")
    /** PTY TERM string: xterm / xterm-256color / vt100 / vanilla / linux. */
    private val terminalTermTypeKey = stringPreferencesKey("terminal_term_type")
    /** Scrollback transcript lines (Termius "back buffer size"). */
    private val terminalBufferLinesKey = intPreferencesKey("terminal_buffer_lines")
    /** Keep display on while terminal session is open (Termius "Prevent sleeping"). */
    private val preventSleepingKey = booleanPreferencesKey("terminal_prevent_sleeping")
    /**
     * Host ids where Mosh UDP was **positively** verified (env check or live session with bytes).
     * AUTO prefers Mosh only for these — never on "binary installed" alone.
     * Comma-separated.
     */
    private val moshUdpOkHostsKey = stringPreferencesKey("mosh_udp_ok_hosts")
    /** Legacy skip list (pre-0.1.35); still read so failed hosts stay off AUTO. */
    private val moshUdpSkipHostsKey = stringPreferencesKey("mosh_udp_skip_hosts")
    /** Comma-separated shortcut bar key ids, left to right. */
    private val shortcutBarOrderKey = stringPreferencesKey("shortcut_bar_order")
    private val composeBoxEnabledKey = booleanPreferencesKey("compose_box_enabled")
    private val composeHistoryKey = stringPreferencesKey("compose_history")

    val appearance: Flow<AppearanceMode> = context.dataStore.data.map { prefs ->
        runCatching {
            AppearanceMode.valueOf(prefs[appearanceKey] ?: AppearanceMode.SYSTEM.name)
        }.getOrDefault(AppearanceMode.SYSTEM)
    }

    val appLanguage: Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        AppLanguage.fromPref(prefs[appLanguageKey])
    }

    val customThemeId: Flow<String?> = context.dataStore.data.map { it[customThemeKey] }

    val autocompleteEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[autocompleteKey] ?: true }

    val openLastSessionOnLaunch: Flow<Boolean> =
        context.dataStore.data.map { it[openLastSessionKey] ?: false }

    val sessionListMode: Flow<SessionListMode> = context.dataStore.data.map { prefs ->
        runCatching {
            SessionListMode.valueOf(prefs[sessionListModeKey] ?: SessionListMode.GroupedByHost.name)
        }.getOrDefault(SessionListMode.GroupedByHost)
    }

    val lastSession: Flow<Pair<String, String>?> = context.dataStore.data.map { prefs ->
        val h = prefs[lastHostKey]
        val s = prefs[lastSessionKey]
        if (h != null && s != null) h to s else null
    }

    val hostOrder: Flow<List<String>> = context.dataStore.data.map { prefs ->
        parseCsv(prefs[hostOrderKey])
    }

    val sessionOrder: Flow<List<String>> = context.dataStore.data.map { prefs ->
        parseLines(prefs[sessionOrderKey])
    }

    /**
     * Terminal monospace size in px for Termux [com.termux.view.TerminalView.setTextSize].
     * Default 16px; minimum 13px (= 0.1.22 fixed size).
     */
    val terminalFontSizePx: Flow<Int> = context.dataStore.data.map { prefs ->
        val px = prefs[terminalFontSizePxKey]
        if (px != null) {
            px.coerceIn(MIN_FONT_PX, MAX_FONT_PX)
        } else {
            // Fresh install or pre-px builds: use 0.1.22-class default (not legacy sp×density).
            DEFAULT_FONT_PX
        }
    }

    val terminalSchemeId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[terminalSchemeIdKey] ?: DEFAULT_SCHEME_ID
    }

    val terminalTypefaceId: Flow<String> = context.dataStore.data.map { prefs ->
        migrateTypefaceId(prefs[terminalTypefaceIdKey] ?: DEFAULT_TYPEFACE_ID)
    }

    val terminalTermType: Flow<String> = context.dataStore.data.map { prefs ->
        val t = prefs[terminalTermTypeKey] ?: DEFAULT_TERM_TYPE
        if (t in TERM_TYPES) t else DEFAULT_TERM_TYPE
    }

    val terminalBufferLines: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[terminalBufferLinesKey] ?: DEFAULT_BUFFER_LINES).coerceIn(MIN_BUFFER_LINES, MAX_BUFFER_LINES)
    }

    val preventSleeping: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[preventSleepingKey] ?: true
    }

    val shortcutBarOrder: Flow<List<String>> =
        context.dataStore.data.map { parseCsv(it[shortcutBarOrderKey]) }

    val composeBoxEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[composeBoxEnabledKey] ?: false }

    val composeHistory: Flow<List<String>> =
        context.dataStore.data.map { ComposeHistory.decode(it[composeHistoryKey]) }

    /** Hosts where Mosh UDP path was confirmed working (AUTO may use Mosh). */
    val moshUdpOkHosts: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        parseCsv(prefs[moshUdpOkHostsKey]).toSet()
    }

    suspend fun setAppearance(mode: AppearanceMode) {
        context.dataStore.edit { it[appearanceKey] = mode.name }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        context.dataStore.edit { it[appLanguageKey] = language.prefValue }
    }

    suspend fun setCustomThemeId(id: String?) {
        context.dataStore.edit {
            if (id == null) it.remove(customThemeKey) else it[customThemeKey] = id
        }
    }

    suspend fun setAutocompleteEnabled(enabled: Boolean) {
        context.dataStore.edit { it[autocompleteKey] = enabled }
    }

    suspend fun setOpenLastSession(enabled: Boolean) {
        context.dataStore.edit { it[openLastSessionKey] = enabled }
    }

    suspend fun setLastSession(hostId: String, sessionName: String) {
        context.dataStore.edit {
            it[lastHostKey] = hostId
            it[lastSessionKey] = sessionName
        }
    }

    suspend fun setSessionListMode(mode: SessionListMode) {
        context.dataStore.edit { it[sessionListModeKey] = mode.name }
    }

    suspend fun setHostOrder(ids: List<String>) {
        context.dataStore.edit { it[hostOrderKey] = ids.joinToString(",") }
    }

    suspend fun setSessionOrder(keys: List<String>) {
        context.dataStore.edit { it[sessionOrderKey] = keys.joinToString("\n") }
    }

    suspend fun setTerminalFontSizePx(px: Int) {
        context.dataStore.edit {
            it[terminalFontSizePxKey] = px.coerceIn(MIN_FONT_PX, MAX_FONT_PX)
            it.remove(legacyFontSizeSpKey)
        }
    }

    suspend fun setTerminalSchemeId(id: String) {
        context.dataStore.edit { it[terminalSchemeIdKey] = id }
    }

    suspend fun setTerminalTypefaceId(id: String) {
        context.dataStore.edit { it[terminalTypefaceIdKey] = migrateTypefaceId(id) }
    }

    suspend fun setTerminalTermType(term: String) {
        val t = if (term in TERM_TYPES) term else DEFAULT_TERM_TYPE
        context.dataStore.edit { it[terminalTermTypeKey] = t }
    }

    suspend fun setTerminalBufferLines(lines: Int) {
        context.dataStore.edit {
            it[terminalBufferLinesKey] = lines.coerceIn(MIN_BUFFER_LINES, MAX_BUFFER_LINES)
        }
    }

    suspend fun setPreventSleeping(enabled: Boolean) {
        context.dataStore.edit { it[preventSleepingKey] = enabled }
    }

    suspend fun setShortcutBarOrder(ids: List<String>) {
        context.dataStore.edit { it[shortcutBarOrderKey] = ids.joinToString(",") }
    }

    suspend fun setComposeBoxEnabled(enabled: Boolean) {
        context.dataStore.edit { it[composeBoxEnabledKey] = enabled }
    }

    suspend fun setComposeHistory(items: List<String>) {
        context.dataStore.edit { it[composeHistoryKey] = ComposeHistory.encode(items) }
    }

    /**
     * Mark Mosh UDP path verified (or not). AUTO only uses Mosh when [ready] is true.
     * Also clears legacy skip entry when marking ready.
     */
    suspend fun setMoshUdpReady(hostId: String, ready: Boolean) {
        context.dataStore.edit { prefs ->
            val ok = parseCsv(prefs[moshUdpOkHostsKey]).toMutableSet()
            val skip = parseCsv(prefs[moshUdpSkipHostsKey]).toMutableSet()
            if (ready) {
                ok.add(hostId)
                skip.remove(hostId)
            } else {
                ok.remove(hostId)
                skip.add(hostId)
            }
            if (ok.isEmpty()) prefs.remove(moshUdpOkHostsKey)
            else prefs[moshUdpOkHostsKey] = ok.joinToString(",")
            if (skip.isEmpty()) prefs.remove(moshUdpSkipHostsKey)
            else prefs[moshUdpSkipHostsKey] = skip.joinToString(",")
        }
    }

    /** True only after positive UDP verification (env check or successful Mosh session). */
    suspend fun isMoshUdpReady(hostId: String): Boolean =
        hostId in moshUdpOkHosts.first()

    /** Termux-style ±2 px step. */
    suspend fun changeTerminalFontSize(increase: Boolean) {
        val cur = terminalFontSizePx.first()
        val next = cur + if (increase) 2 else -2
        setTerminalFontSizePx(next)
    }

    /** Move a host one step up/down in the saved order (missing hosts appended first). */
    suspend fun moveHost(hostId: String, up: Boolean, allHostIds: List<String>) {
        val current = mergeOrder(hostOrder.first(), allHostIds)
        setHostOrder(swapNeighbor(current, hostId, up))
    }

    /** Move a session one step up/down within its host group (and global order list). */
    suspend fun moveSession(sessionKey: String, up: Boolean, allSessionKeys: List<String>) {
        val current = mergeOrder(sessionOrder.first(), allSessionKeys)
        // Prefer moving within same host if possible
        val hostId = sessionKey.substringBefore('\t')
        val sameHost = current.filter { it.startsWith("$hostId\t") }
        if (sameHost.size >= 2 && sessionKey in sameHost) {
            val local = swapNeighbor(sameHost, sessionKey, up)
            val others = current.filterNot { it.startsWith("$hostId\t") }
            // Keep relative host blocks: replace same-host subsequence
            val result = mutableListOf<String>()
            var inserted = false
            for (k in current) {
                if (k.startsWith("$hostId\t")) {
                    if (!inserted) {
                        result += local
                        inserted = true
                    }
                } else {
                    result += k
                }
            }
            if (!inserted) result += local
            // Append any keys not yet present
            others.filter { it !in result }.forEach { result += it }
            setSessionOrder(result.distinct())
        } else {
            setSessionOrder(swapNeighbor(current, sessionKey, up))
        }
    }

    companion object {
        /** Matches 0.1.22 hardcoded TerminalView.setTextSize(13). */
        const val MIN_FONT_PX = 13
        /** Upper bound for the appearance slider (72 was excessive on phones). */
        const val MAX_FONT_PX = 52
        /** Slightly above min for first-run readability; still Termux-small. */
        const val DEFAULT_FONT_PX = 16
        /** Pulse Mono (black/gray) — not green; settings still list Termius Dark etc. */
        const val DEFAULT_SCHEME_ID = "pulse_mono"
        /** Default mono (CJK falls back via system font stack). */
        const val DEFAULT_TYPEFACE_ID = "jetbrains_mono"

        /** Dropped catalog ids → current default (e.g. former microsoft_yahei). */
        fun migrateTypefaceId(id: String): String = when (id) {
            "microsoft_yahei" -> DEFAULT_TYPEFACE_ID
            else -> id
        }
        /** Termius Terminal type options (TERM env / SSH PTY). */
        val TERM_TYPES: List<String> = listOf(
            "xterm",
            "xterm-256color",
            "vt100",
            "vanilla",
            "linux",
        )
        const val DEFAULT_TERM_TYPE = "xterm-256color"
        const val MIN_BUFFER_LINES = 100
        const val MAX_BUFFER_LINES = 50000
        const val DEFAULT_BUFFER_LINES = 1000

        fun parseCsv(raw: String?): List<String> =
            raw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

        fun parseLines(raw: String?): List<String> =
            raw?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

        fun mergeOrder(saved: List<String>, all: List<String>): List<String> {
            val set = all.toSet()
            val ordered = saved.filter { it in set }
            val missing = all.filter { it !in ordered }
            return ordered + missing
        }

        fun swapNeighbor(list: List<String>, id: String, up: Boolean): List<String> {
            val idx = list.indexOf(id)
            if (idx < 0) return list
            val other = if (up) idx - 1 else idx + 1
            if (other !in list.indices) return list
            val m = list.toMutableList()
            val tmp = m[idx]
            m[idx] = m[other]
            m[other] = tmp
            return m
        }
    }
}
