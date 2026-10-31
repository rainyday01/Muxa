package app.mtmux.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import app.mtmux.core.model.AppearanceMode

val LocalMtmuxTheme = staticCompositionLocalOf { MaterialYouDark }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MtmuxTheme(
    appearance: AppearanceMode = AppearanceMode.SYSTEM,
    customThemeId: String? = null,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val spec = when (appearance) {
        AppearanceMode.SYSTEM -> if (systemDark) MaterialYouDark else MaterialYouLight
        AppearanceMode.DARK -> MaterialYouDark
        AppearanceMode.LIGHT -> MaterialYouLight
        AppearanceMode.CUSTOM -> themeById(customThemeId ?: "sequoia")
    }
    val roleSaysDark = spec.role.name.contains("DARK") ||
        spec.id in setOf("sequoia", "space", "terminal", "vision")
    // App chrome darkness for system bar icons — NOT system day/night.
    // See SystemBarContrast KDoc for all OS×app combinations that must stay legible.
    val darkSurface = SystemBarContrast.isDarkSurface(spec, roleSaysDark)
    val dark = darkSurface // Material3 scheme selection follows surface

    // Re-apply whenever appearance / resolved surface changes, and on ON_RESUME
    // (OEMs / enableEdgeToEdge can reset bar appearance when system is still day).
    val view = LocalView.current
    DisposableEffect(darkSurface, appearance, customThemeId, systemDark) {
        if (view.isInEditMode) return@DisposableEffect onDispose { }
        fun apply() = SystemBarContrast.applyFromContext(view.context, view, darkSurface)
        apply()
        val owner = view.findViewTreeLifecycleOwner()
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) apply()
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }

    val c = spec.colors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.onPrimaryContainer,
            secondary = c.primary,
            onSecondary = c.onPrimary,
            secondaryContainer = c.secondaryContainer,
            onSecondaryContainer = c.onSecondaryContainer,
            tertiary = c.primary,
            background = c.background,
            onBackground = c.onSurface,
            surface = c.surface,
            onSurface = c.onSurface,
            surfaceVariant = c.secondaryContainer,
            onSurfaceVariant = c.onSurfaceMuted,
            outline = c.outline,
            surfaceContainer = c.cardGlass,
            surfaceContainerHigh = c.surface,
            surfaceContainerHighest = c.secondaryContainer,
            // Default Snackbar uses inverse*: always dark bar + light text (both themes).
            inverseSurface = Color(0xFF32303A),
            inverseOnSurface = Color(0xFFFFFFFF),
        )
    } else {
        lightColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.onPrimaryContainer,
            secondary = c.primary,
            onSecondary = c.onPrimary,
            secondaryContainer = c.secondaryContainer,
            onSecondaryContainer = c.onSecondaryContainer,
            tertiary = c.primary,
            background = c.background,
            onBackground = c.onSurface,
            surface = c.surface,
            onSurface = c.onSurface,
            surfaceVariant = c.secondaryContainer,
            onSurfaceVariant = c.onSurfaceMuted,
            outline = c.outline,
            surfaceContainer = c.cardGlass,
            surfaceContainerHigh = c.surface,
            surfaceContainerHighest = c.secondaryContainer,
            // Same high-contrast toast on light UI (never pale lavender + pale text).
            inverseSurface = Color(0xFF32303A),
            inverseOnSurface = Color(0xFFFFFFFF),
        )
    }

    // M3-ish type scale. Do NOT bake `color` into TextStyle — Compose Text prefers
    // style.color over LocalContentColor, which made Snackbar contentColor useless
    // (light lavender bar + wrong/invisible text on export-log toast).
    val typography = Typography(
        displaySmall = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = 0.sp,
        ),
        headlineMedium = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 28.sp,
            lineHeight = 36.sp,
        ),
        titleLarge = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
        ),
        bodyLarge = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
        bodyMedium = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        labelLarge = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
    )

    CompositionLocalProvider(LocalMtmuxTheme provides spec) {
        MaterialTheme(colorScheme = scheme, typography = typography) {
            Box(
                Modifier
                    .fillMaxSize()
                    .semantics { testTagsAsResourceId = true },
            ) {
                content()
            }
        }
    }
}
