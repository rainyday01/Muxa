package app.mtmux.feature.settings

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.mtmux.core.ui.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mtmux.core.data.prefs.UserPreferences
import app.mtmux.core.ui.terminal.BuiltinSchemes
import app.mtmux.core.ui.terminal.BuiltinTypefaces
import app.mtmux.core.ui.terminal.TERMINAL_PREVIEW_LINES
import app.mtmux.core.ui.terminal.TerminalTypefaceCache
import app.mtmux.core.ui.theme.LocalMtmuxTheme
import kotlin.math.roundToInt

/**
 * Termius-style "Select scheme & font size" page:
 * typeface + scheme dropdowns, font size slider, live terminal preview below.
 *
 * Changes are held in local draft state until the user taps ✓ (save) or ✕ (discard).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalAppearanceScreen(
    initialTypefaceId: String,
    initialSchemeId: String,
    initialFontSizePx: Int,
    onSave: (typefaceId: String, schemeId: String, fontSizePx: Int) -> Unit,
    onCancel: () -> Unit,
) {
    val theme = LocalMtmuxTheme.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val fieldBg = appearanceFieldBackground()

    var typefaceId by remember { mutableStateOf(initialTypefaceId) }
    var schemeId by remember { mutableStateOf(initialSchemeId) }
    var fontPx by remember {
        mutableFloatStateOf(
            initialFontSizePx.coerceIn(UserPreferences.MIN_FONT_PX, UserPreferences.MAX_FONT_PX).toFloat(),
        )
    }
    var typefaceMenu by remember { mutableStateOf(false) }
    var schemeMenu by remember { mutableStateOf(false) }

    val typeface = BuiltinTypefaces.byId(typefaceId)
    val scheme = BuiltinSchemes.byId(schemeId)
    val androidTf: Typeface = remember(typefaceId) {
        TerminalTypefaceCache.get(context, typefaceId)
    }
    val composeFont = remember(androidTf) {
        FontFamily(androidx.compose.ui.text.font.Typeface(androidTf))
    }
    val previewSp = with(density) { fontPx.toInt().toFloat().toSp() }
    val bg = Color(scheme.background)
    val fg = Color(scheme.foreground)

    Column(
        Modifier
            .fillMaxSize()
            .background(theme.colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("screen_terminal_appearance"),
    ) {
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.appearance_title),
                    fontWeight = FontWeight.SemiBold,
                    color = theme.colors.onSurface,
                )
            },
            navigationIcon = {
                IconButton(onClick = onCancel, modifier = Modifier.testTag("appearance_cancel")) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel), tint = theme.colors.onSurface)
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        onSave(
                            typefaceId,
                            schemeId,
                            fontPx.roundToInt().coerceIn(UserPreferences.MIN_FONT_PX, UserPreferences.MAX_FONT_PX),
                        )
                    },
                    modifier = Modifier.testTag("appearance_save"),
                ) {
                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save), tint = theme.colors.primary)
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.colors.background),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            FieldLabel(stringResource(R.string.appearance_font))
            DropdownField(
                value = typeface.displayName,
                expanded = typefaceMenu,
                onExpand = { typefaceMenu = true },
                onDismiss = { typefaceMenu = false },
                testTag = "appearance_typeface",
                fieldBg = fieldBg,
            ) {
                BuiltinTypefaces.all.forEach { tf ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                tf.displayName,
                                color = theme.colors.onSurface,
                                fontWeight = if (tf.id == typefaceId) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            typefaceId = tf.id
                            typefaceMenu = false
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = theme.colors.onSurface,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            FieldLabel(stringResource(R.string.appearance_scheme))
            DropdownField(
                value = scheme.displayName,
                expanded = schemeMenu,
                onExpand = { schemeMenu = true },
                onDismiss = { schemeMenu = false },
                testTag = "appearance_scheme",
                fieldBg = fieldBg,
            ) {
                BuiltinSchemes.all.forEach { sc ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(14.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color(sc.background))
                                        .border(
                                            0.5.dp,
                                            Color(sc.foreground).copy(alpha = 0.4f),
                                            RoundedCornerShape(3.dp),
                                        ),
                                )
                                Text(
                                    sc.displayName,
                                    color = theme.colors.onSurface,
                                    fontWeight = if (sc.id == schemeId) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        },
                        onClick = {
                            schemeId = sc.id
                            schemeMenu = false
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = theme.colors.onSurface,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            FieldLabel(stringResource(R.string.appearance_size))
            val pxInt = fontPx.roundToInt().coerceIn(
                UserPreferences.MIN_FONT_PX,
                UserPreferences.MAX_FONT_PX,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${UserPreferences.MIN_FONT_PX}", color = theme.colors.onSurfaceMuted, fontSize = 12.sp)
                Text(
                    "${pxInt}px",
                    color = theme.colors.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Text("${UserPreferences.MAX_FONT_PX}", color = theme.colors.onSurfaceMuted, fontSize = 12.sp)
            }
            // Horizontal padding keeps thumb within track at min/max (13 was past left edge).
            Slider(
                value = fontPx.coerceIn(
                    UserPreferences.MIN_FONT_PX.toFloat(),
                    UserPreferences.MAX_FONT_PX.toFloat(),
                ),
                onValueChange = {
                    fontPx = it.coerceIn(
                        UserPreferences.MIN_FONT_PX.toFloat(),
                        UserPreferences.MAX_FONT_PX.toFloat(),
                    )
                },
                valueRange = UserPreferences.MIN_FONT_PX.toFloat()..UserPreferences.MAX_FONT_PX.toFloat(),
                steps = (UserPreferences.MAX_FONT_PX - UserPreferences.MIN_FONT_PX) - 1,
                colors = SliderDefaults.colors(
                    thumbColor = theme.colors.primary,
                    activeTrackColor = theme.colors.primary,
                    inactiveTrackColor = theme.colors.outline,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .testTag("appearance_font_slider"),
            )

            Spacer(Modifier.height(8.dp))
            FieldLabel(stringResource(R.string.appearance_preview))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .padding(12.dp)
                    .testTag("appearance_preview"),
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TERMINAL_PREVIEW_LINES.forEach { line ->
                        Text(
                            text = line.ifEmpty { " " },
                            color = fg,
                            fontFamily = composeFont,
                            fontSize = previewSp,
                            lineHeight = previewSp * 1.25f,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Solid elevated chip color for light/dark (glass surface.alpha is too low for fields). */
@Composable
private fun appearanceFieldBackground(): Color {
    val theme = LocalMtmuxTheme.current
    val solid = theme.colors.surface
    if (solid.alpha >= 0.85f) return solid
    // Translucent glass themes: pick a solid elevated surface by background luminance.
    return if (theme.colors.background.luminance() < 0.5f) {
        Color(0xFF2C2C2E)
    } else {
        Color(0xFFF2F2F7)
    }
}

@Composable
private fun FieldLabel(text: String) {
    val theme = LocalMtmuxTheme.current
    Text(
        text,
        color = theme.colors.primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun DropdownField(
    value: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    testTag: String,
    fieldBg: Color,
    menuContent: @Composable () -> Unit,
) {
    val theme = LocalMtmuxTheme.current
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(fieldBg)
                .border(0.5.dp, theme.colors.outline, RoundedCornerShape(10.dp))
                .clickable(onClick = onExpand)
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .testTag(testTag),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, color = theme.colors.onSurface, fontSize = 16.sp)
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = theme.colors.onSurfaceMuted,
            )
        }
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                surface = fieldBg,
                onSurface = theme.colors.onSurface,
            ),
        ) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = onDismiss,
                modifier = Modifier.background(fieldBg),
            ) {
                menuContent()
            }
        }
    }
}
