package app.mtmux.core.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.View
import android.view.Window
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Status / navigation bar **icon** contrast for edge-to-edge.
 *
 * ## Why this exists
 * [androidx.activity.enableEdgeToEdge] + [SystemBarStyle.auto] follow the **phone**
 * day/night setting. When the user forces the **app** to dark while the system is
 * still light (or the reverse), system-driven bar styles put dark clock/battery
 * glyphs on near-black chrome (or light glyphs on pale chrome) → unreadable.
 *
 * Field: 0.1.88 fixed once; regressed when edge-to-edge re-applied system auto
 * after our SideEffect, or when only `spec.id` / role was used without luminance.
 *
 * ## Combinations that MUST stay legible when you change appearance code
 * | System | App appearance / theme        | Bar icons (clock/battery) |
 * |--------|-------------------------------|---------------------------|
 * | Light  | App dark / sequoia / space…   | **Light** (white/light)   |
 * | Dark   | App light / bigsur / paper    | **Dark** (black)          |
 * | Light  | App light                     | Dark                      |
 * | Dark   | App dark                      | Light                     |
 * | *      | CUSTOM dark surface           | Light                     |
 * | *      | CUSTOM light surface          | Dark                      |
 * | *      | **TerminalScreen** (any)      | **Always light** — VT is  |
 * |        |                               | near-black under the bar  |
 *
 * Rule of thumb: icons contrast against **what is drawn under the status bar**,
 * not against the OS theme. Terminal (Grok) is always dark content even when the
 * rest of the app is light (system day + app light) — force light icons there.
 */
object SystemBarContrast {

    /**
     * @param darkSurface true when the app content under the system bars is dark
     *   (needs light status/nav icons). Prefer [isDarkSurface] from the resolved
     *   [MtmuxThemeSpec] rather than [androidx.compose.foundation.isSystemInDarkTheme].
     */
    fun apply(window: Window, decorView: View, darkSurface: Boolean) {
        // Transparent bars so app background shows edge-to-edge; icons only via controller.
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val bars: WindowInsetsControllerCompat =
            WindowCompat.getInsetsController(window, decorView)
        // isAppearanceLightStatusBars = true  → dark icons (for light surfaces)
        // isAppearanceLightStatusBars = false → light icons (for dark surfaces)
        bars.isAppearanceLightStatusBars = !darkSurface
        bars.isAppearanceLightNavigationBars = !darkSurface
    }

    fun applyFromContext(context: Context, decorView: View, darkSurface: Boolean) {
        val activity = context.findActivity() ?: return
        apply(activity.window, decorView, darkSurface)
    }

    /**
     * Whether chrome under the status bar is dark enough to need **light** system icons.
     * Uses both theme role/id and background luminance so mis-tagged customs still work.
     */
    fun isDarkSurface(spec: MtmuxThemeSpec, roleSaysDark: Boolean): Boolean {
        val lum = relativeLuminance(spec.colors.background)
        // Luminance threshold ~0.4: near-black MaterialYou / terminal → dark surface.
        val lumSaysDark = lum < 0.40
        // Prefer luminance when it clearly disagrees with role (safety net).
        return if (kotlin.math.abs(lum - 0.5) > 0.15) lumSaysDark else roleSaysDark
    }

    private fun relativeLuminance(c: Color): Double {
        return ColorUtils.calculateLuminance(c.toArgb())
    }

    fun Context.findActivity(): Activity? {
        var ctx: Context = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
