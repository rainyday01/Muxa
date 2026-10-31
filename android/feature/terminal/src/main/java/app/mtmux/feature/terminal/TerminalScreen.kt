@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.mtmux.feature.terminal

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import app.mtmux.core.ui.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import app.mtmux.core.data.diag.DiagLog
import app.mtmux.core.model.ComposeHistory
import app.mtmux.core.model.KeyActionType
import app.mtmux.core.model.KeyboardKey
import app.mtmux.core.model.KeyboardLayout
import app.mtmux.core.model.ShortcutOrder
import app.mtmux.core.model.Snippet
import app.mtmux.core.model.Suggestion
import app.mtmux.core.ui.terminal.BuiltinSchemes
import app.mtmux.core.ui.terminal.TerminalTypefaceCache
import app.mtmux.core.ui.theme.LocalMtmuxTheme
import app.mtmux.core.ui.theme.SystemBarContrast
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView

/**
 * Terminal UI powered by **Termux terminal-view** (GPLv3, actively maintained).
 *
 * - Correct VT100/xterm + wcwidth (CJK/emoji) rendering
 * - Soft keyboard: [stableBottomInsetPadding] snaps to max(IME,nav); no mid-screen jump
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    title: String,
    termuxSession: TerminalSession?,
    onBack: () -> Unit,
    onKeyPress: (KeyboardKey) -> Unit,
    suggestions: List<Suggestion> = emptyList(),
    onSuggestion: (Suggestion) -> Unit = {},
    keyboardLayout: KeyboardLayout,
    availableLayouts: List<KeyboardLayout> = emptyList(),
    onKeyboardLayoutChange: ((String) -> Unit)? = null,
    ctrlLatched: Boolean = false,
    shiftLatched: Boolean = false,
    altLatched: Boolean = false,
    /**
     * Font size in **px** for Termux [TerminalView.setTextSize] (Termux KEY_FONTSIZE).
     * Min 13 matches 0.1.22 hardcoded size.
     */
    fontSizePx: Int = 16,
    /** Builtin typeface id → assets/fonts/… */
    typefaceId: String = "jetbrains_mono",
    /** Builtin color scheme id (ANSI + fg/bg). Default Pulse Mono black/gray. */
    schemeId: String = "pulse_mono",
    onSurfaceSize: ((cols: Int, rows: Int) -> Unit)? = null,
    onDisconnect: (() -> Unit)? = null,
    onToggleAutocomplete: (() -> Unit)? = null,
    autocompleteEnabled: Boolean = false,
    /** Saved snippets offered from the ⋮ menu; picking one calls [onSnippet]. */
    snippets: List<Snippet> = emptyList(),
    onSnippet: ((Snippet) -> Unit)? = null,
    onPaste: (() -> Unit)? = null,
    onOpenKeyboardSettings: (() -> Unit)? = null,
    connecting: Boolean = false,
    /** Shown while [connecting] is true (e.g. install shell phase). */
    connectingMessage: String = "",
    onResize: ((cols: Int, rows: Int) -> Unit)? = null,
    onScrollChanged: (() -> Unit)? = null,
    onCtrlLatch: ((Boolean) -> Unit)? = null,
    onShiftLatch: ((Boolean) -> Unit)? = null,
    onAltLatch: ((Boolean) -> Unit)? = null,
    /** Alt-screen finger page (tmux copy-mode for inline CLIs). */
    onAltFingerScroll: ((Boolean) -> Boolean)? = null,
    shortcutOrder: List<String> = emptyList(),
    onShortcutOrderChange: (List<String>) -> Unit = {},
    composeEnabled: Boolean = false,
    onComposeEnabledChange: (Boolean) -> Unit = {},
    composeHistory: List<String> = emptyList(),
    onComposeHistoryChange: (List<String>) -> Unit = {},
    onSendDraft: (String) -> Boolean = { false },
) {
    var expanded by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var reorderOpen by remember { mutableStateOf(false) }
    var snippetsOpen by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var historyOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val appliedFontPx = fontSizePx.coerceIn(13, 52)
    val scheme = remember(schemeId) { BuiltinSchemes.byId(schemeId) }
    val typeface = remember(typefaceId) { TerminalTypefaceCache.get(context, typefaceId) }
    // Avoid thrashing setTextSize / size callbacks on every recomposition
    val applied = remember { intArrayOf(-1, -1, -1) } // fontPx, cols, rows
    var appliedTypefaceId by remember { mutableStateOf<String?>(null) }
    var appliedSchemeId by remember { mutableStateOf<String?>(null) }

    // Apply scheme to Termux palette whenever id or session changes
    DisposableEffect(schemeId, termuxSession) {
        TerminalSchemeApplier.apply(scheme, termuxSession)
        appliedSchemeId = schemeId
        onDispose { }
    }
    // Latest listeners (avoid stale captures inside remember'd clients)
    val sizeListeners = remember {
        object {
            var onSurface: ((Int, Int) -> Unit)? = null
            var onResizeCb: ((Int, Int) -> Unit)? = null
            var onPasteCb: (() -> Unit)? = null
            var ctrlLatched: Boolean = false
            var onCtrlConsumed: (() -> Unit)? = null
            var shiftLatched: Boolean = false
            var onShiftConsumed: (() -> Unit)? = null
            var altLatched: Boolean = false
            var onAltConsumed: (() -> Unit)? = null
            var onAltScroll: ((Boolean) -> Boolean)? = null
        }
    }
    sizeListeners.onSurface = onSurfaceSize
    sizeListeners.onResizeCb = onResize
    sizeListeners.onPasteCb = onPaste
    sizeListeners.ctrlLatched = ctrlLatched
    sizeListeners.shiftLatched = shiftLatched
    sizeListeners.onCtrlConsumed = {
        // One-shot: clear sticky Ctrl after soft/hardware key used it.
        sizeListeners.ctrlLatched = false
        onCtrlLatch?.invoke(false)
    }
    sizeListeners.onShiftConsumed = {
        sizeListeners.shiftLatched = false
        onShiftLatch?.invoke(false)
    }
    sizeListeners.altLatched = altLatched
    sizeListeners.onAltConsumed = {
        sizeListeners.altLatched = false
        onAltLatch?.invoke(false)
    }
    sizeListeners.onAltScroll = onAltFingerScroll

    fun reportGeometry(cols: Int, rows: Int) {
        if (cols == applied[1] && rows == applied[2]) return
        applied[1] = cols
        applied[2] = rows
        sizeListeners.onSurface?.invoke(cols, rows)
        sizeListeners.onResizeCb?.invoke(cols, rows)
    }

    val barKeys = keyboardLayout.barKeys
    val orderedBarKeys = ShortcutOrder.apply(barKeys, shortcutOrder)
    val moreKeys = keyboardLayout.expandedKeys
    val expandedRows = moreKeys.chunked(4)

    // Neutral chrome (Glass Sequoia B) — not green phosphor pills
    val hostPillBg = Color(0xFF2C2C2E)
    val hostPillFg = Color(0xFFE5E5EA)
    // Terminal surface follows **settings scheme** (default pulse_mono black)
    val termBg = Color(scheme.background)
    val specialBarHeight = 44.dp

    var terminalViewRef by remember { mutableStateOf<TerminalView?>(null) }

    val sessionClient = remember {
        MtmuxSessionClient(
            onInvalidate = { terminalViewRef?.onScreenUpdated() },
            onCopy = { text ->
                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("terminal", text))
            },
            onPaste = { sizeListeners.onPasteCb?.invoke() },
        )
    }
    // Compose field owns the IME while focused. Termux must not keep claiming
    // it is the selected editor, or the system keyboard never binds to the field
    // and later refuses to open on the terminal.
    val composeEditor = remember { ComposeEditorFlag() }
    val focusManager = LocalFocusManager.current
    val viewClient = remember {
        MtmuxViewClient(
            viewProvider = { terminalViewRef },
            onSingleTap = {
                expanded = false
                composeEditor.focused = false
                terminalViewRef?.requestFocus()
            },
            onGeometry = { cols, rows -> reportGeometry(cols, rows) },
            isCtrlLatched = { sizeListeners.ctrlLatched },
            onCtrlLatchConsumed = { sizeListeners.onCtrlConsumed?.invoke() },
            isShiftLatched = { sizeListeners.shiftLatched },
            onShiftLatchConsumed = { sizeListeners.onShiftConsumed?.invoke() },
            isAltLatched = { sizeListeners.altLatched },
            onAltLatchConsumed = { sizeListeners.onAltConsumed?.invoke() },
            altFingerScrollHandler = { up -> sizeListeners.onAltScroll?.invoke(up) ?: false },
            terminalSelected = { !composeEditor.focused },
        )
    }
    LaunchedEffect(composeEnabled) {
        if (!composeEnabled) composeEditor.focused = false
    }

    // Keep Termux session client in sync
    DisposableEffect(termuxSession) {
        termuxSession?.updateTerminalSessionClient(sessionClient)
        onDispose { }
    }

    // Shared bottom chrome controller (bar / panel / ime slot).
    val imeBottom = remember { ImeBottomController() }
    val densityForBar = LocalDensity.current
    imeBottom.specialBarPx = with(densityForBar) { specialBarHeight.toPx().toInt() }

    // Wire freeze ↔ Termux grid.
    // While still connecting, do NOT freeze — first paint must use the post-IME
    // footprint (field: goIme mid-attach → paint 82 rows then WINCH 46 hid Grok
    // input). When the reserved slot actually changes, apply rows on the *next
    // layout* so chrome and grid move together (no crop-then-WINCH flash).
    DisposableEffect(imeBottom, connecting) {
        imeBottom.onGridFreezeChanged = { frozen ->
            if (connecting && frozen) {
                terminalViewRef?.setFreezeGridUpdates(false)
            } else {
                terminalViewRef?.setFreezeGridUpdates(frozen)
            }
        }
        imeBottom.onImeVisualSettled = {
            terminalViewRef?.setFreezeGridUpdates(false)
        }
        imeBottom.onApplyGridNow = {
            terminalViewRef?.requestApplyGridOnNextLayout()
        }
        onDispose {
            imeBottom.onGridFreezeChanged = null
            imeBottom.onImeVisualSettled = null
            imeBottom.onApplyGridNow = null
        }
    }

    fun imm(): android.view.inputmethod.InputMethodManager =
        context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager

    fun hideIme() {
        val v = terminalViewRef ?: return
        ViewCompat.getWindowInsetsController(v)?.hide(WindowInsetsCompat.Type.ime())
        imm().hideSoftInputFromWindow(v.windowToken, 0)
    }

    /** Drop the compose editor so the terminal can take the IME. */
    fun releaseComposeEditor() {
        composeEditor.focused = false
        focusManager.clearFocus(force = true)
    }

    /**
     * Show the system keyboard on the terminal. The ⋯ menu still owns the window
     * in the click frame, so [InputMethodManager.showSoftInput] returns false
     * unless this runs after that popup has released focus.
     */
    fun showIme() {
        val v = terminalViewRef ?: return
        composeEditor.focused = false
        fun attempt(left: Int) {
            if (!v.isAttachedToWindow) return
            if (!v.hasWindowFocus() && left > 0) {
                v.postDelayed({ attempt(left - 1) }, 40L)
                return
            }
            v.requestFocus()
            val input = imm()
            input.restartInput(v)
            ViewCompat.getWindowInsetsController(v)?.show(WindowInsetsCompat.Type.ime())
            val shown = input.showSoftInput(
                v,
                android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT,
            )
            if (!shown) {
                DiagLog.i("ime", "showSoftInput returned false; insets controller requested")
            }
        }
        v.post { attempt(6) }
    }

    /**
     * Poll until IME height is safe to unfreeze (logs: chrome).
     * First tick delayed — early force_watchdog (~200ms) used to WINCH mid-animation.
     */
    fun scheduleImeSettleWatch() {
        val tv = terminalViewRef ?: return
        var attempts = 0
        fun tick() {
            attempts++
            imeBottom.forceSettleIfNeeded()
            // Up to ~2s for slow OEM keyboards; interval 220ms.
            if (imeBottom.gridFrozen && attempts < 10) {
                tv.postDelayed({ tick() }, 220L)
            }
        }
        tv.postDelayed({ tick() }, 420L)
    }

    /** Bar only: ride IME hide animation; grid frozen until keyboard fully gone. */
    fun goBarOnly() {
        expanded = false
        releaseComposeEditor()
        imeBottom.goBar()
        hideIme()
        scheduleImeSettleWatch()
    }

    /**
     * Shortcut panel under the special bar.
     * Same reserved slot as the system IME — no WINCH. Stay frozen until the
     * keyboard is gone so a mid-hide unfreeze cannot resize the grid.
     */
    fun goShortcutPanel() {
        expanded = true
        releaseComposeEditor()
        hideIme()
        imeBottom.goPanel()
        scheduleImeSettleWatch()
    }

    /**
     * System IME: follow keyboard animation for slot height (Termius-like).
     * Character grid frozen until height is stable → single WINCH.
     */
    fun goSystemIme() {
        expanded = false
        releaseComposeEditor()
        imeBottom.goIme()
        showIme()
        scheduleImeSettleWatch()
    }

    /** Compose field focused: reserve the keyboard slot without focusing the terminal. */
    fun onComposeFocused(focused: Boolean) {
        composeEditor.focused = focused
        if (!focused) return
        expanded = false
        imeBottom.goIme()
        scheduleImeSettleWatch()
    }

    /** One tap: system keyboard up, or down if it is already up. */
    fun toggleSystemIme() {
        if (imeBottom.mode == ImeBottomController.Mode.Ime && !expanded) goBarOnly() else goSystemIme()
    }

    /** One tap: shortcut panel in the keyboard slot, or back to the bar. */
    fun toggleShortcutPanel() {
        if (expanded) goBarOnly() else goShortcutPanel()
    }

    fun pressSpecial(id: String, key: KeyboardKey? = null) {
        when (id) {
            "ime_focus", "touch" -> goSystemIme()
            "hide" -> goBarOnly()
            "more" -> toggleShortcutPanel()
            "settings", "gear" -> onOpenKeyboardSettings?.invoke()
            "paste" -> onPaste?.invoke()
            else -> key?.let { onKeyPress(it) }
        }
    }

    // Terminal chrome stays dark. It does not follow the system theme or the
    // in-app palette: the VT window is black, and a light bar or light IME
    // sits on it as a separate surface.
    val keyBarBg = Color(0xFF2C2C2E)
    val keyBarDivider = Color(0xFF3A3A3C)
    val keyBarActive = Color(0xFF48484A)
    val keyText = Color(0xFFE5E5EA)
    val expandedBg = keyBarBg
    TerminalImeNight.bind()

    // —— Status bar icons while in terminal ——
    // Terminal content + top chrome under the status bar is always near-black
    // (termBg / Grok alt), even when the phone is in day mode and the app shell
    // is light (dark status icons). Force light icons for the whole terminal stay;
    // restore app-theme contrast on leave. See SystemBarContrast KDoc combos.
    val view = LocalView.current
    val appTheme = LocalMtmuxTheme.current
    DisposableEffect(view, appTheme.id, termBg) {
        fun forceTerminalBars() {
            // Always dark surface under the bar on this screen.
            SystemBarContrast.applyFromContext(view.context, view, darkSurface = true)
        }
        fun restoreAppBars() {
            val roleDark = appTheme.role.name.contains("DARK") ||
                appTheme.id in setOf("sequoia", "space", "terminal", "vision")
            val dark = SystemBarContrast.isDarkSurface(appTheme, roleDark)
            SystemBarContrast.applyFromContext(view.context, view, dark)
        }
        forceTerminalBars()
        val owner = view.findViewTreeLifecycleOwner()
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) forceTerminalBars()
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose {
            owner?.lifecycle?.removeObserver(obs)
            restoreAppBars()
        }
    }

    // Bar at bottom; slot under bar for system IME reserve OR expanded shortcut keys.
    // statusBars ∪ cutout: keep title/back out of the system bar when Column overflows
    // from a bad IME slot (phantom 928px used to shove chrome under the status bar).
    MaterialTheme(colorScheme = terminalChromeScheme) {
    Column(
        Modifier
            .fillMaxSize()
            .background(termBg)
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
            .stableBottomInsetPadding(imeBottom),
    ) {
        // Top chrome
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(onClick = onBack, bg = Color(0xFF2A2A2E)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(hostPillBg)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    title,
                    color = hostPillFg,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box {
                CircleIconButton(onClick = { menuOpen = true }, bg = Color(0xFF2A2A2E)) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.term_menu), tint = Color.White, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    availableLayouts.forEach { layout ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (layout.id == keyboardLayout.id) "✓ ${layout.name}" else layout.name,
                                )
                            },
                            onClick = {
                                onKeyboardLayoutChange?.invoke(layout.id)
                                menuOpen = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                (if (composeEnabled) "✓ " else "") +
                                    stringResource(R.string.term_compose_toggle),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            if (composeEnabled) releaseComposeEditor()
                            // The draft row changes the grid height: cover and apply it in one step.
                            terminalViewRef?.requestApplyGridOnNextLayout()
                            onComposeEnabledChange(!composeEnabled)
                        },
                    )
                    if (onOpenKeyboardSettings != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.term_custom_keyboard)) },
                            onClick = {
                                onOpenKeyboardSettings()
                                menuOpen = false
                            },
                        )
                    }
                    if (onSnippet != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.term_snippets)) },
                            onClick = {
                                menuOpen = false
                                snippetsOpen = true
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                (if (autocompleteEnabled) "✓ " else "") +
                                    stringResource(R.string.term_toggle_complete),
                            )
                        },
                        onClick = {
                            onToggleAutocomplete?.invoke()
                            menuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.term_disconnect)) },
                        onClick = {
                            onDisconnect?.invoke()
                            menuOpen = false
                        },
                    )
                }
            }
        }

        // —— Termux TerminalView (the real VT) ——
        // clipToBounds: VT / rogue scrollY must not paint over title / status chrome.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .background(Color.Black),
        ) {
            if (termuxSession != null) {
                AndroidView(
                    factory = { ctx ->
                        TerminalView(ctx, null).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            // Terminals are LTR grid; avoid RTL reordering of CJK/Latin mixes
                            layoutDirection = android.view.View.LAYOUT_DIRECTION_LTR
                            textDirection = android.view.View.TEXT_DIRECTION_LTR
                            // Termux: setTextSize(px) — same entry point as TermuxTerminalViewClient
                            setTextSize(appliedFontPx)
                            applied[0] = appliedFontPx
                            setTypeface(typeface)
                            appliedTypefaceId = typefaceId
                            setTerminalViewClient(viewClient)
                            isFocusable = true
                            isFocusableInTouchMode = true
                            terminalViewRef = this
                            attachSession(termuxSession)
                            TerminalSchemeApplier.apply(scheme, termuxSession)
                        }
                    },
                    update = { view ->
                        terminalViewRef = view
                        // Live font size from settings — only when value actually changes
                        if (applied[0] != appliedFontPx) {
                            view.setTextSize(appliedFontPx)
                            applied[0] = appliedFontPx
                        }
                        if (appliedTypefaceId != typefaceId) {
                            view.setTypeface(typeface)
                            appliedTypefaceId = typefaceId
                        }
                        if (appliedSchemeId != schemeId) {
                            TerminalSchemeApplier.apply(scheme, termuxSession)
                            appliedSchemeId = schemeId
                            view.onScreenUpdated()
                        }
                        if (view.mTermSession !== termuxSession) {
                            view.attachSession(termuxSession)
                            TerminalSchemeApplier.apply(scheme, termuxSession)
                            applied[1] = -1
                            applied[2] = -1
                        }
                        val em = termuxSession.getEmulator()
                        if (em != null) {
                            reportGeometry(em.mColumns, em.mRows)
                        } else {
                            view.post {
                                view.updateSize()
                                termuxSession.getEmulator()?.let {
                                    reportGeometry(it.mColumns, it.mRows)
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds(),
                )
            }
            // Show while connecting even after emulator exists (install shell used to go black).
            if (connecting) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.88f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        connectingMessage.ifBlank { stringResource(R.string.term_connecting) },
                        color = hostPillFg,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // Bottom chrome:
        //   special bar  (always)
        //   slot under bar  (height = keyboard footprint OR 0)
        //     - IME mode: empty reserve (system keyboard paints there)
        //     - Expanded: shortcut keys fill the *same* slot (replace keyboard, no double-count)
        // Outer padding is nav-only — never stack keyboard padding + expanded height.
        val density = LocalDensity.current
        val slotDp = with(density) { imeBottom.slotPx.toDp() }

        Column(Modifier.fillMaxWidth().background(keyBarBg)) {
            if (composeEnabled) {
                ComposeDraftBar(
                    draft = draft,
                    onDraftChange = { draft = it },
                    history = composeHistory,
                    historyOpen = historyOpen,
                    onHistoryOpenChange = { historyOpen = it },
                    keyText = keyText,
                    fieldFill = Color.White.copy(alpha = 0.08f),
                    fieldStroke = Color(0xFF636366),
                    barHeight = specialBarHeight,
                    onFocusChanged = { onComposeFocused(it) },
                    onSend = {
                        val text = draft
                        if (text.isBlank()) return@ComposeDraftBar
                        onComposeHistoryChange(ComposeHistory.remember(composeHistory, text))
                        if (onSendDraft(ComposeHistory.payload(text))) draft = ""
                    },
                )
            }
            HorizontalDivider(color = keyBarDivider, thickness = 0.5.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(specialBarHeight)
                    .background(keyBarBg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Keyboard and panel toggles sit at fixed ends of the bar so the
                // shortcut keys never shift when the keyboard state changes.
                val imeOpen = imeBottom.mode == ImeBottomController.Mode.Ime && !expanded
                SpecialBarCell(
                    Modifier
                        .widthIn(min = 48.dp)
                        .background(if (imeOpen) keyBarActive else Color.Transparent)
                        .testTag("term_bar_ime"),
                    onClick = { toggleSystemIme() },
                    divider = true,
                    dividerColor = keyBarDivider,
                ) {
                    Icon(
                        if (imeOpen) Icons.Default.KeyboardHide else Icons.Default.Keyboard,
                        stringResource(if (imeOpen) R.string.term_hide_keyboard else R.string.term_shortcut_ime),
                        tint = keyText,
                        modifier = Modifier.size(22.dp),
                    )
                }
                // Completion chips lead the scrolling keys inside the bar: a separate
                // row would change the grid height (and send WINCH) on every keystroke.
                val barScroll = rememberScrollState()
                val showChips = suggestions.isNotEmpty() && !expanded
                LaunchedEffect(showChips, suggestions) {
                    if (showChips) barScroll.scrollTo(0)
                }
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(barScroll),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showChips) {
                        suggestions.forEach { sug ->
                            Text(
                                sug.label,
                                color = keyText,
                                fontSize = 13.sp,
                                maxLines = 1,
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF3A3A6A))
                                    .clickable { onSuggestion(sug) }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    orderedBarKeys.forEach { key ->
                        val label = app.mtmux.core.ui.keyboard.BuiltinKeyLabels.of(
                            key,
                            keyboardLayout.builtin,
                        )
                        val latched = key.action.type == KeyActionType.MODIFIER_LATCH &&
                            when (key.action.modifier?.lowercase()) {
                                "ctrl", "control" -> ctrlLatched
                                "shift" -> shiftLatched
                                "alt", "meta" -> altLatched
                                else -> false
                            }
                        SpecialBarCell(
                            Modifier.widthIn(min = 52.dp),
                            onClick = {
                                when (key.id) {
                                    "paste", "hide", "settings", "more" -> pressSpecial(key.id, key)
                                    else -> onKeyPress(key)
                                }
                            },
                            onLongClick = { reorderOpen = true },
                            divider = true,
                            dividerColor = keyBarDivider,
                        ) {
                            Text(
                                label,
                                color = keyText,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                maxLines = 1,
                                textDecoration = if (latched) {
                                    androidx.compose.ui.text.style.TextDecoration.Underline
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
                SpecialBarCell(
                    Modifier
                        .widthIn(min = 48.dp)
                        .background(if (expanded) keyBarActive else Color.Transparent)
                        .testTag("term_bar_panel"),
                    onClick = { toggleShortcutPanel() },
                    divider = false,
                    dividerColor = keyBarDivider,
                ) {
                    Icon(
                        Icons.Default.Apps,
                        stringResource(R.string.term_shortcut_panel),
                        tint = keyText,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            // Slot under special bar:
            //   - Expanded: shortcut key grid (⋯ 第二次 / 从键盘切过来)
            //   - IME mode: empty reserve so bar sits above system keyboard
            if (slotDp > 0.dp) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(slotDp)
                        .background(if (expanded) expandedBg else Color.Transparent),
                ) {
                    if (expanded) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 6.dp),
                        ) {
                            TextButton(
                                onClick = { goBarOnly() },
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text(stringResource(R.string.term_hide_keyboard), color = keyText)
                            }
                            if (expandedRows.isEmpty()) {
                                TermiusDefaultExpandedRows(
                                    ctrlLatched = ctrlLatched,
                                    shiftLatched = shiftLatched,
                                    keyText = keyText,
                                    onKeyPress = onKeyPress,
                                    onPaste = { pressSpecial("paste") },
                                    onHide = { pressSpecial("hide") },
                                )
                            } else {
                                expandedRows.forEach { row ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                    ) {
                                        row.forEach { k ->
                                            ExpandedKeyLabel(
                                                label = app.mtmux.core.ui.keyboard.BuiltinKeyLabels.of(
                                                    k,
                                                    keyboardLayout.builtin,
                                                ),
                                                color = keyText,
                                                selected = k.action.type == KeyActionType.MODIFIER_LATCH &&
                                                    when (k.action.modifier?.lowercase()) {
                                                        "ctrl", "control" -> ctrlLatched
                                                        "shift" -> shiftLatched
                                                        "alt", "meta" -> altLatched
                                                        else -> false
                                                    },
                                                onClick = {
                                                    when (k.id) {
                                                        "paste", "hide", "settings", "more" -> pressSpecial(k.id, k)
                                                        else -> onKeyPress(k)
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (snippetsOpen && onSnippet != null) {
            SnippetPickerDialog(
                snippets = snippets,
                onPick = {
                    snippetsOpen = false
                    onSnippet(it)
                },
                onDismiss = { snippetsOpen = false },
            )
        }
        if (reorderOpen) {
            ShortcutReorderDialog(
                keys = orderedBarKeys,
                builtin = keyboardLayout.builtin,
                onMove = { index, delta ->
                    onShortcutOrderChange(
                        ShortcutOrder.move(orderedBarKeys, shortcutOrder, index, delta),
                    )
                },
                onDismiss = { reorderOpen = false },
            )
        }
    }
    }
}

@Composable
private fun TermiusDefaultExpandedRows(
    ctrlLatched: Boolean,
    shiftLatched: Boolean,
    keyText: Color,
    onKeyPress: (KeyboardKey) -> Unit,
    onPaste: () -> Unit,
    onHide: () -> Unit,
) {
    fun k(id: String, label: String, code: String? = null, latch: String? = null) =
        KeyboardKey(
            id = id,
            label = label,
            action = when {
                latch != null -> app.mtmux.core.model.KeyAction(KeyActionType.MODIFIER_LATCH, modifier = latch)
                else -> app.mtmux.core.model.KeyAction(KeyActionType.KEY, keyCode = code)
            },
        )
    fun chord(id: String, label: String, vararg keys: String) =
        KeyboardKey(
            id = id,
            label = label,
            action = app.mtmux.core.model.KeyAction(KeyActionType.CHORD, keys = keys.toList()),
        )
    val rows = listOf(
        listOf(
            "Esc" to { onKeyPress(k("esc", "Esc", "Escape")) },
            "Ctrl-C" to { onKeyPress(chord("ctrl_c", "Ctrl-C", "Control", "c")) },
            "Ctrl-X" to { onKeyPress(chord("ctrl_x", "Ctrl-X", "Control", "x")) },
            "⇧Tab" to { onKeyPress(chord("stab", "⇧Tab", "Shift", "Tab")) },
        ),
        listOf(
            "Enter" to { onKeyPress(k("enter", "Enter", "Enter")) },
            "Paste" to onPaste,
            "Ctrl-J" to { onKeyPress(chord("ctrl_j", "Ctrl-J", "Control", "j")) },
            "Hide" to onHide,
        ),
        listOf(
            "↑" to { onKeyPress(k("up", "↑", "ArrowUp")) },
            "↓" to { onKeyPress(k("down", "↓", "ArrowDown")) },
            "Pg Up" to { onKeyPress(k("pgup", "Pg Up", "PageUp")) },
            "Pg Dn" to { onKeyPress(k("pgdn", "Pg Dn", "PageDown")) },
        ),
        listOf(
            "←" to { onKeyPress(k("left", "←", "ArrowLeft")) },
            "→" to { onKeyPress(k("right", "→", "ArrowRight")) },
            "Home" to { onKeyPress(k("home", "Home", "Home")) },
            "End" to { onKeyPress(k("end", "End", "End")) },
        ),
    )
    rows.forEach { row ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            row.forEach { (label, action) ->
                ExpandedKeyLabel(
                    label = label,
                    color = keyText,
                    selected = (label == "Ctrl" && ctrlLatched) || (label == "Shift" && shiftLatched),
                    onClick = action,
                )
            }
        }
    }
}

@Composable
private fun ExpandedKeyLabel(
    label: String,
    color: Color,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = color,
        fontSize = if (label.contains('\n')) 12.sp else 15.sp,
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Center,
        lineHeight = 18.sp,
        textDecoration = if (selected) {
            androidx.compose.ui.text.style.TextDecoration.Underline
        } else {
            null
        },
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    )
}

/** Mutable so the remembered Termux client reads the latest compose focus. */
private class ComposeEditorFlag {
    var focused: Boolean = false
}

@Composable
private fun ComposeDraftBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    history: List<String>,
    historyOpen: Boolean,
    onHistoryOpenChange: (Boolean) -> Unit,
    keyText: Color,
    fieldFill: Color,
    fieldStroke: Color,
    barHeight: androidx.compose.ui.unit.Dp,
    onFocusChanged: (Boolean) -> Unit,
    onSend: () -> Unit,
) {
    val hint = stringResource(R.string.term_compose_hint)
    val fieldShape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(barHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .clickable(enabled = history.isNotEmpty()) { onHistoryOpenChange(true) }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.term_compose_history),
                color = keyText.copy(alpha = if (history.isEmpty()) 0.35f else 0.75f),
                fontSize = 13.sp,
                maxLines = 1,
            )
            DropdownMenu(
                expanded = historyOpen && history.isNotEmpty(),
                onDismissRequest = { onHistoryOpenChange(false) },
            ) {
                history.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Text(item, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        onClick = {
                            onDraftChange(item)
                            onHistoryOpenChange(false)
                        },
                    )
                }
            }
        }
        BasicTextField(
            value = draft,
            onValueChange = onDraftChange,
            singleLine = true,
            textStyle = TextStyle(color = keyText, fontSize = 15.sp),
            cursorBrush = SolidColor(keyText),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(vertical = 6.dp)
                .border(1.dp, fieldStroke, fieldShape)
                .background(fieldFill, fieldShape)
                .padding(horizontal = 10.dp)
                .onFocusChanged { onFocusChanged(it.isFocused) },
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    if (draft.isEmpty()) {
                        Text(
                            hint,
                            color = keyText.copy(alpha = 0.4f),
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
        Box(
            Modifier
                .fillMaxHeight()
                .semantics { role = Role.Button }
                .pointerInput(draft.isNotBlank()) {
                    if (draft.isBlank()) return@pointerInput
                    // Fire on press. A click-up is cancelled when leaving the
                    // field hides the IME and the bar moves under the finger.
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        onSend()
                    }
                }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.term_compose_send),
                color = keyText.copy(alpha = if (draft.isBlank()) 0.35f else 1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SnippetPickerDialog(
    snippets: List<Snippet>,
    onPick: (Snippet) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.term_snippets)) },
        text = {
            if (snippets.isEmpty()) {
                Text(stringResource(R.string.term_snippets_empty))
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    snippets.forEach { sn ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(sn) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                (if (sn.runOnSelect) "⏎ " else "") + sn.name,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                sn.body.lineSequence().firstOrNull().orEmpty(),
                                fontSize = 12.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun ShortcutReorderDialog(
    keys: List<KeyboardKey>,
    builtin: Boolean,
    onMove: (index: Int, delta: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.term_reorder_shortcuts)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.term_reorder_hint),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                keys.forEachIndexed { index, key ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            app.mtmux.core.ui.keyboard.BuiltinKeyLabels.of(key, builtin),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onMove(index, -1) }, enabled = index > 0) {
                            Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.term_up))
                        }
                        IconButton(
                            onClick = { onMove(index, 1) },
                            enabled = index < keys.lastIndex,
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.term_down))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun SpecialBarCell(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    divider: Boolean,
    dividerColor: Color = Color(0xFFE0E0E4),
    content: @Composable () -> Unit,
) {
    val gesture = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Box(
        modifier
            .fillMaxHeight()
            .then(gesture)
            .then(
                if (divider) {
                    Modifier.border(0.5.dp, dividerColor, RoundedCornerShape(0))
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun CircleIconButton(
    onClick: () -> Unit,
    bg: Color,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private val terminalChromeScheme = darkColorScheme(
    surface = Color(0xFF2C2C2E),
    onSurface = Color(0xFFE5E5EA),
    surfaceContainer = Color(0xFF2C2C2E),
    primary = Color(0xFFE5E5EA),
)

/**
 * Ask the system IME for a dark keyboard while the terminal is open.
 * Candidate chips and the shortcut bar are painted dark themselves.
 * This does not follow the system night flag or the in-app palette.
 * Restored when the terminal leaves composition.
 */
private object TerminalImeNight {
    private var saved: Configuration? = null

    @Composable
    fun bind() {
        val view = LocalView.current
        DisposableEffect(view) {
            val activity = view.context.findActivity()
            apply(activity, view)
            onDispose { restore(activity, view) }
        }
    }

    private fun apply(activity: Activity?, anchor: View) {
        val resources = activity?.resources ?: return
        if (saved == null) saved = Configuration(resources.configuration)
        val updated = Configuration(resources.configuration)
        updated.uiMode =
            (updated.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        @Suppress("DEPRECATION")
        runCatching { resources.updateConfiguration(updated, resources.displayMetrics) }
        restart(activity, anchor)
    }

    private fun restore(activity: Activity?, anchor: View) {
        val previous = saved ?: return
        val resources = activity?.resources ?: return
        @Suppress("DEPRECATION")
        runCatching { resources.updateConfiguration(previous, resources.displayMetrics) }
        saved = null
        restart(activity, anchor)
    }

    private fun restart(activity: Activity, anchor: View) {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val target = activity.currentFocus ?: anchor
        target.post { imm?.restartInput(target) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
