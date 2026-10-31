package app.mtmux.feature.terminal

import android.view.View
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import app.mtmux.core.data.diag.DiagLog
import kotlin.math.abs
import kotlin.math.max

/**
 * Bottom chrome under the special bar.
 *
 * ## Field failures
 * 1. (0.1.82) [forceSettleIfNeeded] unfroze mid-open → extra WINCH.
 * 2. (0.1.82) Hide treated as show → holdFinal 928 with IME gone → black band +
 *    title shoved under the status bar.
 * 3. (0.1.82) Transient liveIme=0 during open cleared slot → keyboard covered input.
 * 4. (0.2.0 log 2026-08-12) Bar / 系统键盘 / 快捷条 cycle still *flashed*:
 *    slot jumped first (crop or black band) then WINCH 82↔46 ~65–280ms later.
 *    Xiaomi hide reports liveIme=0 at animStart; wantImeOpen forced animDir=show
 *    and keepSlot=928 with the keyboard already gone.
 *
 * ## Rules (0.2.2)
 * - **Hold** the known IME footprint for the whole show/hide animation. Never ride
 *   live IME height (that resizes the grid every frame).
 * - **Apply the character grid once** when the slot actually changes (same layout
 *   as the new height). No second WINCH after settle if rows already match.
 * - Open: pre-reserve; ignore live=0 glitches while [recentlyShow].
 * - Hide / system dismiss: keep reserve until live IME is gone, then slot=0 +
 *   one grid apply. Never re-inflate 928 after IME is gone.
 * - Ime ↔ Expanded: same slot → no grid apply.
 * - Nav with IME gone: slot must be 0 (no phantom reserve).
 */
@Stable
class ImeBottomController {
    var bottomPx by mutableIntStateOf(0)
        internal set

    var slotPx by mutableIntStateOf(0)
        internal set

    var lastImePx: Int = 0
        internal set

    var navPx: Int = 0
        internal set

    var mode: Mode by mutableStateOf(Mode.Nav)
        private set

    var screenHeightPx: Int = 0
    var specialBarPx: Int = 0

    /** True while we intentionally track live IME height (open or hide animation). */
    var followImeVisual: Boolean = false
        private set

    var gridFrozen: Boolean = false
        private set

    var onGridFreezeChanged: ((Boolean) -> Unit)? = null
    var onImeVisualSettled: (() -> Unit)? = null

    /**
     * Slot just changed to a stable target — apply Termux rows on the next layout
     * (same frame as the new view height). Avoids crop-then-WINCH flash.
     */
    var onApplyGridNow: (() -> Unit)? = null

    var layoutGeneration by mutableIntStateOf(0)
        private set

    private var lastLiveImePx: Int = 0
    private var lastLiveImeChangeAtMs: Long = 0L
    private var settledThisGesture: Boolean = false
    private var deferLogCounter: Int = 0

    /** +1 showing, -1 hiding, 0 idle (from WindowInsetsAnimation). */
    private var animDir: Int = 0
    private var animActive: Boolean = false

    /**
     * Intent: we called [goIme] and still want the keyboard footprint.
     * Field log 0.1.82: goIme preReserve slot=928 then a transient liveIme=0 inset
     * triggered ime_collapsed → slot=0 while the keyboard was actually up (covers input).
     * While true, never collapse on live=0 *glitches*; system dismiss is handled
     * separately via [recentlyShow] timeout.
     */
    private var wantImeOpen: Boolean = false

    private var showRequestedAtMs: Long = 0L
    private var hideRequestedAtMs: Long = 0L

    enum class Mode { Nav, Ime, Expanded }

    companion object {
        /**
         * Last keyboard inset seen per screen height, kept for the process.
         * A new terminal screen otherwise pre-reserves an estimate (956) and then
         * snaps to the real keyboard (928) mid-animation: the bar twitches once
         * on every first open (log 0.3.16).
         */
        private val rememberedImePx = HashMap<Int, Int>()
    }

    private fun rememberIme() {
        if (screenHeightPx > 0 && lastImePx >= solidImeMin()) rememberedImePx[screenHeightPx] = lastImePx
    }

    private fun nowMs(): Long = System.currentTimeMillis()

    private fun recentlyShow(windowMs: Long = 700L): Boolean =
        showRequestedAtMs > 0L && nowMs() - showRequestedAtMs < windowMs

    private fun recentlyHide(windowMs: Long = 700L): Boolean =
        hideRequestedAtMs > 0L && nowMs() - hideRequestedAtMs < windowMs

    private fun log(msg: String) {
        DiagLog.i(
            "chrome",
            "$msg | mode=$mode follow=$followImeVisual frozen=$gridFrozen " +
                "slot=$slotPx lastIme=$lastImePx nav=$navPx liveIme=$lastLiveImePx " +
                "anim=${if (animActive) (if (animDir < 0) "hide" else "show") else "idle"}",
        )
    }

    private fun logDefer(msg: String) {
        deferLogCounter++
        if (deferLogCounter % 3 == 1) log(msg)
    }

    private fun setGridFrozen(frozen: Boolean, reason: String) {
        if (gridFrozen == frozen) return
        gridFrozen = frozen
        log("freeze=$frozen ($reason)")
        onGridFreezeChanged?.invoke(frozen)
    }

    private fun bumpLayout() {
        layoutGeneration++
    }

    private fun noteLiveIme(ime: Int) {
        if (ime != lastLiveImePx) {
            lastLiveImePx = ime
            lastLiveImeChangeAtMs = nowMs()
        }
    }

    private fun imeStable(stableMs: Long = 120L): Boolean {
        if (lastLiveImeChangeAtMs == 0L) return false
        return nowMs() - lastLiveImeChangeAtMs >= stableMs
    }

    private fun requestApplyGrid(reason: String) {
        log("applyGrid $reason")
        onApplyGridNow?.invoke()
    }

    private fun setSlot(target: Int, reason: String, applyGrid: Boolean) {
        val next = max(0, target)
        if (abs(slotPx - next) <= 2) return
        slotPx = next
        bumpLayout()
        if (applyGrid) requestApplyGrid(reason)
    }

    /** Keep the known keyboard footprint; do not follow live IME. */
    private fun holdKnownSlot() {
        val target = when {
            heightKnown() -> keyboardSlotHeight()
            slotPx > 0 -> slotPx
            else -> defaultSlotEstimate()
        }
        if (target > 0 && slotPx == 0) {
            slotPx = target
        } else if (target > 0 && slotPx < (target * 0.85f).toInt()) {
            // Only grow if we badly under-reserved. Never shrink 956→928 mid-anim.
            slotPx = target
        }
    }

    /**
     * True only when we are intentionally closing the keyboard.
     * Must NOT treat transient liveIme=0 during open as hide (0.1.82 field bug).
     */
    private fun isHidingGesture(): Boolean {
        if (recentlyShow() && animDir >= 0) return false
        if (recentlyHide()) return true
        if (animActive && animDir < 0) return true
        if (mode == Mode.Nav && followImeVisual) return true
        if (!wantImeOpen && mode == Mode.Ime && animDir < 0) return true
        return false
    }

    fun applyInsets(insets: WindowInsetsCompat) {
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        val tappable = insets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom
        navPx = max(nav, tappable)
        bottomPx = navPx
        noteLiveIme(ime)
        // Do not keep the tallest IME inset ever seen. A one-frame spike
        // (field log: 980 → 1318) used to stick and leave a gap of about
        // a third of the keyboard above the real keys.

        when (mode) {
            Mode.Ime -> {
                val hiding = !wantImeOpen || (animActive && animDir < 0) ||
                    (recentlyHide() && !recentlyShow())
                if (hiding) {
                    applyImeHiding(ime)
                    return
                }
                applyImeOpening(ime)
            }
            Mode.Nav -> {
                if (animActive) {
                    // Hide/show is still running. Xiaomi reports liveIme=0 on the
                    // first frame while the keyboard is still covering the screen.
                    holdKnownSlot()
                } else if (ime >= solidImeMin()) {
                    adoptVisibleIme(ime, "nav_saw_ime")
                } else if (followImeVisual) {
                    // Hold reserve until IME is actually gone, then one collapse.
                    if (ime == 0 && !recentlyShow()) {
                        commitNavSlotZero("ime_zero_nav")
                    }
                } else if (slotPx != 0 && ime == 0 && !recentlyShow()) {
                    setSlot(0, "phantom_clear", applyGrid = true)
                }
            }
            Mode.Expanded -> {
                if (heightKnown()) {
                    val t = chromeSlotHeight()
                    if (t > 0 && abs(slotPx - t) > 2 && slotPx < t) {
                        // Grow only; never collapse the panel under the user's fingers.
                        slotPx = t
                    }
                }
                if (ime == 0 && gridFrozen && !recentlyShow()) {
                    settledThisGesture = true
                    settleGrid("panel_ime_gone")
                }
            }
        }
    }

    private fun applyImeHiding(ime: Int) {
        if (!gridFrozen) setGridFrozen(true, "ime_hiding")
        followImeVisual = true
        if (animActive || recentlyShow()) {
            // Collapse only after the animation ends at height 0.
            // A mid-animation 0 used to drop the terminal behind the keyboard.
            holdKnownSlot()
            return
        }
        wantImeOpen = false
        if (ime == 0) {
            mode = Mode.Nav
            followImeVisual = false
            animActive = false
            animDir = 0
            settledThisGesture = true
            setSlot(0, "ime_collapsed", applyGrid = true)
            settleGrid("ime_collapsed")
        } else {
            // Keyboard slides out inside the reserved slot — no per-frame WINCH.
            holdKnownSlot()
        }
    }

    /** Keyboard is on screen. Slot is the inset above the nav padding, not a historical max. */
    private fun adoptVisibleIme(ime: Int, reason: String) {
        lastImePx = ime
        rememberIme()
        mode = Mode.Ime
        wantImeOpen = true
        followImeVisual = false
        val target = max(0, ime - navPx)
        setSlot(target, reason, applyGrid = true)
    }

    private fun applyImeOpening(ime: Int) {
        if (heightKnown()) {
            holdKnownSlot()
        } else if (ime > navPx) {
            val live = max(0, ime - navPx)
            val floor = defaultSlotEstimate()
            val next = max(slotPx, max(live, floor))
            if (next != slotPx) slotPx = next
        } else if (slotPx == 0) {
            slotPx = defaultSlotEstimate()
        }
    }

    private fun heightKnown(): Boolean = lastImePx >= solidImeMin()

    fun onImeAnimationProgress(insets: WindowInsetsCompat) = applyInsets(insets)

    fun onImeAnimationStart(
        insets: WindowInsetsCompat,
        bounds: WindowInsetsAnimationCompat.BoundsCompat? = null,
    ) {
        settledThisGesture = false
        deferLogCounter = 0
        animActive = true
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom

        // Xiaomi often reports liveIme=0 at hide-start and liveIme=peak at show-start.
        // Never infer direction from the current inset alone — use explicit intent,
        // then animation bounds, then last resort.
        animDir = when {
            recentlyHide() && !recentlyShow() -> -1
            recentlyShow() -> 1
            mode == Mode.Expanded -> -1
            ime < solidImeMin() / 2 && !recentlyShow() &&
                (mode == Mode.Ime || followImeVisual || wantImeOpen) -> -1
            bounds != null -> {
                val lo = bounds.lowerBound.bottom
                val hi = bounds.upperBound.bottom
                if (hi <= lo) {
                    if (recentlyShow()) 1 else if (ime < solidImeMin() / 2) -1 else 1
                } else if (ime <= (lo + hi) / 2) {
                    1
                } else {
                    -1
                }
            }
            else -> 1
        }

        if (animDir < 0) {
            followImeVisual = true
            setGridFrozen(true, "ime_anim_start")
            holdKnownSlot()
            applyInsets(insets)
            log("animStart hideHold slot=$slotPx")
            return
        }

        if (mode == Mode.Ime || (mode == Mode.Nav && followImeVisual)) {
            setGridFrozen(true, "ime_anim_start")
            if (mode == Mode.Ime) followImeVisual = true
        }

        if (ime >= solidImeMin()) {
            // Show-start on Xiaomi is already the settled keyboard height.
            // Replace a previously poisoned taller value instead of keeping max().
            lastImePx = ime
            rememberIme()
            val target = max(0, ime - navPx)
            if (slotPx > target + 2) setSlot(target, "ime_show_exact", applyGrid = true)
        }
        applyInsets(insets)

        if (mode == Mode.Ime && heightKnown()) {
            holdKnownSlot()
            log("animStart holdFinal slot=$slotPx")
        } else {
            log("animStart")
        }
    }

    fun onImeAnimationEnd(insets: WindowInsetsCompat) {
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val dir = animDir
        animActive = false
        animDir = 0
        noteLiveIme(ime)
        if (mode != Mode.Expanded && ime >= solidImeMin()) {
            // End inset is the keyboard that is actually on screen.
            adoptVisibleIme(ime, "ime_anim_end")
            log("animEnd keep ime=$ime slot=$slotPx")
            settledThisGesture = true
            settleGrid("ime_anim_end")
            return
        }
        if (mode != Mode.Expanded && ime == 0 && dir > 0 && wantImeOpen) {
            // A show that never reported a height is not a dismiss.
            log("animEnd show ignored ime=0 keep slot=$slotPx")
            settledThisGesture = true
            settleGrid("ime_show_noop")
            return
        }
        applyInsets(insets)
        log("animEnd ime=$ime")
        tryCommitSettled("anim_end")
    }

    /**
     * Watchdog: only complete a settle when it is safe.
     * Must NOT unfreeze while open animation still has liveIme ≪ lastIme.
     */
    fun forceSettleIfNeeded() {
        if (settledThisGesture) return
        if (!gridFrozen && !followImeVisual) return

        val live = lastLiveImePx
        val solid = solidImeMin()

        if (mode == Mode.Ime && animDir >= 0 && lastImePx >= solid &&
            live > 0 && live < (lastImePx * 0.92f).toInt()
        ) {
            logDefer("settle defer force_watchdog mid_open live=$live last=$lastImePx")
            return
        }
        if (isHidingGesture() && live > 0) {
            logDefer("settle defer force_watchdog mid_hide live=$live")
            return
        }
        if (mode == Mode.Ime && live >= solid && !imeStable(180L) && animActive) {
            logDefer("settle defer force_watchdog anim_active_unstable")
            return
        }

        tryCommitSettled("force_watchdog")
    }

    private fun tryCommitSettled(reason: String) {
        if (settledThisGesture) return
        when (mode) {
            Mode.Ime -> {
                val live = lastLiveImePx
                val solid = solidImeMin()

                if (live == 0 && lastImePx >= solid && (!wantImeOpen || animDir < 0) &&
                    !recentlyShow()
                ) {
                    log("settle $reason ime zero → collapse")
                    wantImeOpen = false
                    mode = Mode.Nav
                    followImeVisual = false
                    animActive = false
                    animDir = 0
                    settledThisGesture = true
                    setSlot(0, "ime_down_$reason", applyGrid = true)
                    settleGrid("ime_down_$reason")
                    return
                }
                if (wantImeOpen && live == 0) {
                    if (recentlyShow() || (animActive && animDir > 0)) {
                        if (slotPx <= 0) {
                            slotPx = keyboardSlotHeight().takeIf { it > 0 } ?: defaultSlotEstimate()
                        }
                        logDefer("settle defer $reason want_open_live0 keepSlot=$slotPx")
                        return
                    }
                    // Xiaomi: hide already at 0; we still had wantImeOpen (system dismiss).
                    log("settle $reason system_dismiss collapse")
                    wantImeOpen = false
                    mode = Mode.Nav
                    followImeVisual = false
                    animActive = false
                    animDir = 0
                    settledThisGesture = true
                    setSlot(0, "system_dismiss", applyGrid = true)
                    settleGrid("ime_dismiss_$reason")
                    return
                }
                if (isHidingGesture()) {
                    logDefer("settle defer $reason still_hiding live=$live")
                    return
                }
                if (!heightKnown()) {
                    if (live > 0 && live < solid) {
                        if (slotPx < defaultSlotEstimate()) slotPx = defaultSlotEstimate()
                        logDefer("settle defer $reason live_too_small")
                        return
                    }
                    if (live > 0 && !imeStable(150L)) {
                        logDefer("settle defer $reason not_stable")
                        return
                    }
                    if (live >= solid) lastImePx = max(lastImePx, live)
                }
                if (animActive && animDir > 0 && live > 0 &&
                    lastImePx >= solid && live < (lastImePx * 0.92f).toInt()
                ) {
                    logDefer("settle defer $reason open_not_peak")
                    return
                }
                val target = keyboardSlotHeight().takeIf { it > 0 } ?: defaultSlotEstimate()
                followImeVisual = false
                // Do not snap 956→928 here — that is a 1-row chrome twitch. Grid
                // already matches the reserved slot from goIme.
                if (slotPx <= 0) slotPx = target
                log("settle $reason commitIme slot=$slotPx")
                if (lastImePx < target + navPx) lastImePx = target + navPx
                settledThisGesture = true
                settleGrid("ime_$reason")
            }
            Mode.Nav -> {
                val live = lastLiveImePx
                if (live > solidImeMin() / 2) {
                    if (followImeVisual) {
                        holdKnownSlot()
                        logDefer("settle defer $reason hide_ime_still_up live=$live")
                        return
                    }
                    if (wantImeOpen) {
                        mode = Mode.Ime
                        followImeVisual = false
                        val target = keyboardSlotHeight().takeIf { it > 0 } ?: defaultSlotEstimate()
                        setSlot(target, "recover_ime", applyGrid = true)
                        log("settle $reason recover Ime slot=$slotPx")
                        settledThisGesture = true
                        settleGrid("ime_recover_$reason")
                        return
                    }
                }
                followImeVisual = false
                wantImeOpen = false
                log("settle $reason commitNav slot→0")
                settledThisGesture = true
                setSlot(0, "nav_$reason", applyGrid = true)
                settleGrid("nav_$reason")
            }
            Mode.Expanded -> {
                followImeVisual = false
                val target = chromeSlotHeight()
                if (target > 0 && slotPx == 0) slotPx = target
                settledThisGesture = true
                settleGrid("panel_$reason")
            }
        }
    }

    private fun settleGrid(reason: String) {
        setGridFrozen(false, "settle:$reason")
        onImeVisualSettled?.invoke()
        bumpLayout()
    }

    private fun commitNavSlotZero(reason: String) {
        if (slotPx != 0 || followImeVisual || gridFrozen) {
            followImeVisual = false
            settledThisGesture = true
            animActive = false
            animDir = 0
            setSlot(0, reason, applyGrid = true)
            settleGrid(reason)
        }
    }

    fun keyboardSlotHeight(): Int {
        if (lastImePx <= 0) return 0
        return max(0, lastImePx - navPx)
    }

    fun chromeSlotHeight(): Int {
        val known = keyboardSlotHeight()
        if (known > 0) return known
        return defaultSlotEstimate()
    }

    private fun solidImeMin(): Int {
        val fromScreen = if (screenHeightPx > 0) (screenHeightPx * 0.18f).toInt() else 200
        return max(fromScreen, 200)
    }

    private fun defaultSlotEstimate(): Int {
        val fromScreen = if (screenHeightPx > 0) (screenHeightPx * 0.36f).toInt() else 0
        return max(fromScreen, 400)
    }

    fun seedNav(navBottomPx: Int) {
        navPx = max(navPx, navBottomPx)
        bottomPx = navPx
    }

    fun goBar() {
        hideRequestedAtMs = nowMs()
        wantImeOpen = false
        mode = Mode.Nav
        bottomPx = navPx
        settledThisGesture = false
        deferLogCounter = 0
        log("goBar")
        if (lastLiveImePx < solidImeMin() / 2) {
            followImeVisual = false
            animActive = false
            animDir = 0
            settledThisGesture = true
            setSlot(0, "goBar_no_ime", applyGrid = true)
            settleGrid("goBar_no_ime")
            return
        }
        // IME still up: hold footprint until it is gone, then one collapse.
        followImeVisual = true
        animDir = -1
        setGridFrozen(true, "goBar")
        holdKnownSlot()
        log("goBar holdUntilGone slot=$slotPx")
    }

    fun goPanel() {
        hideRequestedAtMs = nowMs()
        wantImeOpen = false
        mode = Mode.Expanded
        bottomPx = navPx
        followImeVisual = false
        settledThisGesture = false
        animDir = 0
        val target = chromeSlotHeight()
        val changed = abs(slotPx - target) > 2
        if (target > 0) slotPx = target
        log("goPanel slot=$slotPx")
        if (lastLiveImePx >= solidImeMin() / 2) {
            // Keyboard still up — freeze until it is gone. Same slot → no WINCH.
            setGridFrozen(true, "goPanel")
        } else {
            settledThisGesture = true
            if (changed) requestApplyGrid("goPanel")
            settleGrid("goPanel")
        }
    }

    fun goIme() {
        showRequestedAtMs = nowMs()
        wantImeOpen = true
        mode = Mode.Ime
        bottomPx = navPx
        settledThisGesture = false
        deferLogCounter = 0
        followImeVisual = true
        animDir = 1
        animActive = false
        setGridFrozen(true, "goIme")
        if (!heightKnown()) rememberedImePx[screenHeightPx]?.let { lastImePx = it }
        val target = if (heightKnown()) {
            keyboardSlotHeight().takeIf { it > 0 } ?: defaultSlotEstimate()
        } else {
            max(slotPx, defaultSlotEstimate())
        }
        val changed = abs(slotPx - target) > 2 || slotPx == 0
        slotPx = target
        log("goIme preReserve slot=$slotPx")
        if (changed) requestApplyGrid("goIme")
    }

    fun preemptShowIme() = goIme()
    fun preemptHideIme() = goBar()
    fun setExpanded(expanded: Boolean, imeWasVisible: Boolean = false) {
        if (expanded) goPanel()
        else if (imeWasVisible) goIme()
        else goBar()
    }
    fun preemptExpandedOverIme() = goPanel()
}

fun Modifier.stableBottomInsetPadding(
    controller: ImeBottomController,
): Modifier = composed {
    val view = LocalView.current
    val density = LocalDensity.current
    val navComposePx = WindowInsets.navigationBars.getBottom(density)

    remember(navComposePx) {
        controller.seedNav(navComposePx)
        true
    }

    DisposableEffect(view) {
        if (controller.screenHeightPx <= 0) {
            controller.screenHeightPx = view.rootView?.height?.takeIf { it > 0 }
                ?: view.resources.displayMetrics.heightPixels
        }
        onDispose { }
    }

    DisposableEffect(view, controller) {
        ViewCompat.getRootWindowInsets(view)?.let { controller.applyInsets(it) }

        val animCallback = object : WindowInsetsAnimationCompat.Callback(
            DISPATCH_MODE_CONTINUE_ON_SUBTREE,
        ) {
            override fun onStart(
                animation: WindowInsetsAnimationCompat,
                bounds: WindowInsetsAnimationCompat.BoundsCompat,
            ): WindowInsetsAnimationCompat.BoundsCompat {
                if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                    ViewCompat.getRootWindowInsets(view)?.let {
                        controller.onImeAnimationStart(it, bounds)
                    }
                }
                return bounds
            }

            override fun onProgress(
                insets: WindowInsetsCompat,
                runningAnimations: MutableList<WindowInsetsAnimationCompat>,
            ): WindowInsetsCompat {
                if (runningAnimations.any { it.typeMask and WindowInsetsCompat.Type.ime() != 0 }) {
                    controller.onImeAnimationProgress(insets)
                } else {
                    controller.applyInsets(insets)
                }
                return insets
            }

            override fun onEnd(animation: WindowInsetsAnimationCompat) {
                if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                    ViewCompat.getRootWindowInsets(view)?.let { controller.onImeAnimationEnd(it) }
                }
            }
        }

        val insetsListener = OnApplyWindowInsetsListener { _: View, insets: WindowInsetsCompat ->
            controller.applyInsets(insets)
            insets
        }

        ViewCompat.setWindowInsetsAnimationCallback(view, animCallback)
        ViewCompat.setOnApplyWindowInsetsListener(view, insetsListener)
        ViewCompat.requestApplyInsets(view)

        onDispose {
            ViewCompat.setWindowInsetsAnimationCallback(view, null)
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val slotWatch = controller.slotPx
    @Suppress("UNUSED_VARIABLE")
    val genWatch = controller.layoutGeneration
    val bottomDp = with(density) { controller.bottomPx.toDp() }
    this.padding(bottom = bottomDp)
}

/** @deprecated */
fun Modifier.fastImePadding(
    durationMs: Int = 50,
    onImeBottomPx: ((Int) -> Unit)? = null,
): Modifier = composed {
    val c = remember { ImeBottomController() }
    DisposableEffect(c.bottomPx) {
        onImeBottomPx?.invoke(c.bottomPx)
        onDispose { }
    }
    stableBottomInsetPadding(c)
}
