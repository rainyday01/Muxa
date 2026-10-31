package app.mtmux.feature.terminal

import app.mtmux.core.model.TerminalScheme
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import java.util.Properties

/**
 * Push a [TerminalScheme] into Termux's global [TerminalColors.COLOR_SCHEME]
 * and refresh a live session emulator.
 */
object TerminalSchemeApplier {
    fun apply(scheme: TerminalScheme, session: TerminalSession? = null) {
        val props = Properties()
        props.setProperty("foreground", toHash(scheme.foreground))
        props.setProperty("background", toHash(scheme.background))
        props.setProperty("cursor", toHash(scheme.cursor))
        for (i in 0 until 16) {
            props.setProperty("color$i", toHash(scheme.ansi[i]))
        }
        TerminalColors.COLOR_SCHEME.updateWith(props)
        val em: TerminalEmulator? = session?.getEmulator()
        em?.mColors?.reset()
        session?.refresh()
    }

    private fun toHash(argb: Int): String {
        val rgb = argb and 0xFFFFFF
        return String.format("#%06X", rgb)
    }
}
