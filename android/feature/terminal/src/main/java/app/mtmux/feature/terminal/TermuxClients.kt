package app.mtmux.feature.terminal

import android.view.KeyEvent
import android.view.MotionEvent
import app.mtmux.core.data.keyboard.KeyboardActionResolver
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

/**
 * Minimal Termux client wiring for m-tmux remote sessions.
 */
class MtmuxSessionClient(
    private val onInvalidate: (TerminalSession) -> Unit = {},
    private val onCopy: (String) -> Unit = {},
    private val onPaste: () -> Unit = {},
) : TerminalSessionClient {
    override fun onTextChanged(changedSession: TerminalSession) = onInvalidate(changedSession)
    override fun onTitleChanged(changedSession: TerminalSession) {}
    override fun onSessionFinished(finishedSession: TerminalSession) {}
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) = onCopy(text)
    override fun onPasteTextFromClipboard(session: TerminalSession?) = onPaste()
    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) = onInvalidate(session)
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
    override fun getTerminalCursorStyle(): Int? = null
    override fun logError(tag: String, message: String) {}
    override fun logWarn(tag: String, message: String) {}
    override fun logInfo(tag: String, message: String) {}
    override fun logDebug(tag: String, message: String) {}
    override fun logVerbose(tag: String, message: String) {}
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
    override fun logStackTrace(tag: String, e: Exception) {}
}

class MtmuxViewClient(
    private val viewProvider: () -> TerminalView?,
    private val onSingleTap: () -> Unit = {},
    /** Fired after emulator (re)size settles — use for attach geometry / host tracking. */
    private val onGeometry: ((cols: Int, rows: Int) -> Unit)? = null,
    /** Sticky Ctrl from the special key bar (one-shot with soft/hardware keys). */
    private val isCtrlLatched: () -> Boolean = { false },
    /**
     * Called when a latched Ctrl was applied to an input code point so the latch
     * can clear and the Ctrl underline can update.
     */
    private val onCtrlLatchConsumed: (() -> Unit)? = null,
    /** Sticky Shift from the special key bar (one-shot with soft/hardware keys). */
    private val isShiftLatched: () -> Boolean = { false },
    private val onShiftLatchConsumed: (() -> Unit)? = null,
    /** Sticky Alt from the special key bar: the next character goes out ESC-prefixed. */
    private val isAltLatched: () -> Boolean = { false },
    private val onAltLatchConsumed: (() -> Unit)? = null,
    /** Alt-screen finger page. Return true if handled (tmux copy-mode). */
    private val altFingerScrollHandler: ((Boolean) -> Boolean)? = null,
    /** tmux pane known to be on its main screen; see [TerminalViewClient.isAltPaneInline]. */
    private val altPaneInline: () -> Boolean = { false },
    /**
     * False while the compose field is the editor. Termux only advertises the
     * terminal input type when this is true, so a focused text field can own the IME.
     */
    private val terminalSelected: () -> Boolean = { true },
) : TerminalViewClient {
    override fun onScale(scale: Float): Float = 1.0f
    override fun onSingleTapUp(e: MotionEvent) = onSingleTap()
    override fun onAltFingerScroll(up: Boolean, rows: Int): Boolean =
        altFingerScrollHandler?.invoke(up) ?: false
    override fun isAltPaneInline(): Boolean = altPaneInline()
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = terminalSelected()
    override fun copyModeChanged(copyMode: Boolean) {}
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false
    override fun readControlKey(): Boolean = isCtrlLatched()
    override fun readAltKey(): Boolean = isAltLatched()
    override fun readShiftKey(): Boolean = isShiftLatched()
    override fun readFnKey(): Boolean = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        // TerminalView already ORs event-ctrl with [readControlKey]. When our bar
        // latch is on, map the next character as Ctrl+key then release (one-shot).
        if (ctrlDown && isCtrlLatched()) {
            onCtrlLatchConsumed?.invoke()
        }
        // TerminalView read the Alt latch before this call and writes ESC + char.
        val altDown = isAltLatched()
        if (altDown) onAltLatchConsumed?.invoke()
        if (isShiftLatched()) {
            onShiftLatchConsumed?.invoke()
            // Termux only uppercases letters for IME; map 1→! etc. ourselves.
            if (!ctrlDown) {
                val mapped = KeyboardActionResolver.applyUsShift(codePoint)
                if (mapped != codePoint) {
                    session.writeCodePoint(altDown, mapped)
                    return true
                }
            }
        }
        return false
    }
    override fun onEmulatorSet() {
        val view = viewProvider()
        view?.onScreenUpdated()
        val em = view?.mEmulator
        if (em != null) onGeometry?.invoke(em.mColumns, em.mRows)
    }
    override fun logError(tag: String, message: String) {
        app.mtmux.core.data.diag.DiagLog.e(tag, message)
    }
    override fun logWarn(tag: String, message: String) {
        app.mtmux.core.data.diag.DiagLog.w(tag, message)
    }
    override fun logInfo(tag: String, message: String) {
        app.mtmux.core.data.diag.DiagLog.i(tag, message)
    }
    override fun logDebug(tag: String, message: String) {}
    override fun logVerbose(tag: String, message: String) {}
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
    override fun logStackTrace(tag: String, e: Exception) {}
}
