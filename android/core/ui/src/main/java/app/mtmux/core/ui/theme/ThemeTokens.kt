package app.mtmux.core.ui.theme

import androidx.compose.ui.graphics.Color
import app.mtmux.core.model.ThemeRole

data class MtmuxColors(
    val background: Color,
    val surface: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val outline: Color,
    val terminalBackground: Color,
    val terminalForeground: Color,
    val success: Color,
    val warning: Color,
    /** Elevated list cards / settings groups (M3 surface-container). */
    val cardGlass: Color,
)

data class MtmuxThemeSpec(
    val id: String,
    val displayName: String,
    val role: ThemeRole,
    val colors: MtmuxColors,
    val cardRadiusDp: Float,
)

/**
 * System dark — Material You (design scheme 04).
 * Purple tonal primary, surface-container cards, large-title chrome.
 * Id stays [sequoia] so existing appearance prefs keep resolving.
 */
val MaterialYouDark = MtmuxThemeSpec(
    id = "sequoia",
    displayName = "Material You",
    role = ThemeRole.SYSTEM_DARK,
    cardRadiusDp = 16f,
    colors = MtmuxColors(
        background = Color(0xFF131318),
        surface = Color(0xFF1D1B20),
        onSurface = Color(0xFFE4E1E9),
        onSurfaceMuted = Color(0xFFCAC4D0),
        primary = Color(0xFFD0BCFF),
        onPrimary = Color(0xFF381E72),
        primaryContainer = Color(0xFF4F378B),
        onPrimaryContainer = Color(0xFFEADDFF),
        secondaryContainer = Color(0xFF4A4458),
        onSecondaryContainer = Color(0xFFE8DEF8),
        outline = Color(0xFF938F99),
        terminalBackground = Color(0xFF0E0E12),
        terminalForeground = Color(0xFFE4E1E9),
        success = Color(0xFFA8F0B8),
        warning = Color(0xFFF9DE6C),
        cardGlass = Color(0xFF1D1B20),
    ),
)

/** @deprecated Use [MaterialYouDark]; alias for call sites / git history. */
val SequoiaGlass: MtmuxThemeSpec get() = MaterialYouDark

/** System light — Material You light tonal. Id stays [bigsur] for prefs. */
val MaterialYouLight = MtmuxThemeSpec(
    id = "bigsur",
    displayName = "Material You Light",
    role = ThemeRole.SYSTEM_LIGHT,
    cardRadiusDp = 16f,
    colors = MtmuxColors(
        background = Color(0xFFFEF7FF),
        surface = Color(0xFFFFFBFE),
        onSurface = Color(0xFF1D1B20),
        onSurfaceMuted = Color(0xFF49454F),
        primary = Color(0xFF6750A4),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEADDFF),
        onPrimaryContainer = Color(0xFF21005D),
        secondaryContainer = Color(0xFFE8DEF8),
        onSecondaryContainer = Color(0xFF1D192B),
        outline = Color(0xFF79747E),
        terminalBackground = Color(0xFF1A1B26),
        terminalForeground = Color(0xFFA9B1D6),
        success = Color(0xFF146C2E),
        warning = Color(0xFF7D5700),
        cardGlass = Color(0xFFFFFBFE),
    ),
)

/** @deprecated Use [MaterialYouLight]. */
val BigSurSoft: MtmuxThemeSpec get() = MaterialYouLight

val SpaceGrayPro = MtmuxThemeSpec(
    id = "space",
    displayName = "Space Gray Pro",
    role = ThemeRole.PERSONAL,
    cardRadiusDp = 12f,
    colors = MtmuxColors(
        background = Color.Black,
        surface = Color(0xFF1C1C1E),
        onSurface = Color(0xFFF5F5F7),
        onSurfaceMuted = Color(0xFF8E8E93),
        primary = Color(0xFF0A84FF),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF003A70),
        onPrimaryContainer = Color(0xFFD0E4FF),
        secondaryContainer = Color(0xFF2C2C2E),
        onSecondaryContainer = Color(0xFFE5E5EA),
        outline = Color(0xFF2C2C2E),
        terminalBackground = Color(0xFF0A0A0A),
        terminalForeground = Color(0xFFE0E0E0),
        success = Color(0xFF30D158),
        warning = Color(0xFFFFD60A),
        cardGlass = Color(0xFF1C1C1E),
    ),
)

val TerminalPure = MtmuxThemeSpec(
    id = "terminal",
    displayName = "Terminal Pure",
    role = ThemeRole.PERSONAL,
    cardRadiusDp = 8f,
    colors = MtmuxColors(
        background = Color(0xFF0C0C0C),
        surface = Color(0xFF161B22),
        onSurface = Color(0xFFE6EDF3),
        onSurfaceMuted = Color(0xFF7D8590),
        primary = Color(0xFF3FB950),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF0D3B1A),
        onPrimaryContainer = Color(0xFFA7F0B0),
        secondaryContainer = Color(0xFF21262D),
        onSecondaryContainer = Color(0xFFC9D1D9),
        outline = Color(0xFF30363D),
        terminalBackground = Color(0xFF0D1117),
        terminalForeground = Color(0xFF3FB950),
        success = Color(0xFF3FB950),
        warning = Color(0xFFD29922),
        cardGlass = Color(0xFF161B22),
    ),
)

val VisionLayers = MtmuxThemeSpec(
    id = "vision",
    displayName = "Vision Layers",
    role = ThemeRole.PERSONAL,
    cardRadiusDp = 18f,
    colors = MtmuxColors(
        background = Color(0xFF0A0A12),
        surface = Color(0xFF16162A),
        onSurface = Color.White,
        onSurfaceMuted = Color(0x99FFFFFF),
        primary = Color(0xFF78A0FF),
        onPrimary = Color(0xFF111111),
        primaryContainer = Color(0xFF2A3A6A),
        onPrimaryContainer = Color(0xFFD6E3FF),
        secondaryContainer = Color(0xFF2A2A40),
        onSecondaryContainer = Color(0xFFE0E0FF),
        outline = Color(0x29FFFFFF),
        terminalBackground = Color(0xFF050510),
        terminalForeground = Color(0xFFB8F0FF),
        success = Color(0xFF50DCC8),
        warning = Color(0xFFFF64B4),
        cardGlass = Color(0xFF16162A),
    ),
)

val PaperEditorial = MtmuxThemeSpec(
    id = "paper",
    displayName = "Paper Editorial",
    role = ThemeRole.PERSONAL,
    cardRadiusDp = 4f,
    colors = MtmuxColors(
        background = Color(0xFFF7F4EF),
        surface = Color.White,
        onSurface = Color(0xFF1A1A1A),
        onSurfaceMuted = Color(0xFF8A8278),
        primary = Color(0xFF1A1A1A),
        onPrimary = Color(0xFFF7F4EF),
        primaryContainer = Color(0xFFE5DDD0),
        onPrimaryContainer = Color(0xFF1A1A1A),
        secondaryContainer = Color(0xFFEFEAE2),
        onSecondaryContainer = Color(0xFF1A1A1A),
        outline = Color(0xFFE5DDD0),
        terminalBackground = Color(0xFF1A1A1A),
        terminalForeground = Color(0xFFE8E0D4),
        success = Color(0xFF2D6A4F),
        warning = Color(0xFFBC6C25),
        cardGlass = Color.White,
    ),
)

val AllThemes: List<MtmuxThemeSpec> = listOf(
    MaterialYouDark,
    MaterialYouLight,
    SpaceGrayPro,
    TerminalPure,
    VisionLayers,
    PaperEditorial,
)

fun themeById(id: String): MtmuxThemeSpec =
    AllThemes.firstOrNull { it.id == id } ?: MaterialYouDark
