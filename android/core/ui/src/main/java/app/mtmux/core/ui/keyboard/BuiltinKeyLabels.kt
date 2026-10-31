package app.mtmux.core.ui.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.mtmux.core.model.KeyboardKey
import app.mtmux.core.ui.R

/** Localized labels for built-in shortcut keys (tmux / CLI hints). */
object BuiltinKeyLabels {
    fun resId(keyId: String): Int? = when (keyId) {
        "ctrl_j" -> R.string.kb_ctrl_j
        "c" -> R.string.kb_tmux_new
        "n" -> R.string.kb_tmux_next
        "p" -> R.string.kb_tmux_prev
        "d" -> R.string.kb_tmux_detach
        "pct" -> R.string.kb_tmux_vsplit
        "quot" -> R.string.kb_tmux_hsplit
        "z" -> R.string.kb_tmux_zoom
        "x" -> R.string.kb_tmux_kill
        "stab" -> R.string.kb_shift_tab
        else -> null
    }

    @Composable
    fun of(key: KeyboardKey, builtinLayout: Boolean): String {
        val id = if (builtinLayout) resId(key.id) else null
        return if (id != null) stringResource(id) else key.label
    }
}
