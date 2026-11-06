package app.mtmux.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mtmux.core.data.prefs.UserPreferences
import app.mtmux.core.model.AppLanguage
import app.mtmux.core.model.AppearanceMode
import app.mtmux.core.ui.R
import app.mtmux.core.ui.theme.AllThemes
import app.mtmux.core.ui.theme.LocalMtmuxTheme

/** Termux-style px bounds; min = 0.1.22 hardcoded setTextSize(13). */
private const val FONT_MIN = 13
private const val FONT_MAX = 72
private const val FONT_DEFAULT = 16

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun keepDurationLabel(minutes: Int): String = when {
    minutes <= 0 -> stringResource(R.string.settings_bg_keep_off_short)
    minutes % 60 == 0 -> stringResource(R.string.settings_bg_keep_hours, minutes / 60)
    else -> stringResource(R.string.settings_bg_keep_minutes, minutes)
}

@Composable
fun SettingsScreen(
    appearance: AppearanceMode,
    appLanguage: AppLanguage = AppLanguage.SYSTEM,
    customThemeId: String?,
    autocompleteEnabled: Boolean,
    openLastSession: Boolean,
    /** Terminal font size in px (shown as summary; edit via [onOpenTerminalAppearance]). */
    terminalFontSizePx: Int = FONT_DEFAULT,
    terminalTermType: String = UserPreferences.DEFAULT_TERM_TYPE,
    terminalBufferLines: Int = UserPreferences.DEFAULT_BUFFER_LINES,
    preventSleeping: Boolean = true,
    backgroundKeepMinutes: Int = UserPreferences.DEFAULT_BACKGROUND_KEEP_MINUTES,
    appVersion: String,
    onAppearance: (AppearanceMode) -> Unit,
    onAppLanguage: (AppLanguage) -> Unit = {},
    onCustomTheme: (String) -> Unit,
    onAutocomplete: (Boolean) -> Unit,
    onOpenLast: (Boolean) -> Unit,
    onTerminalFontSize: (Int) -> Unit = {},
    onOpenTerminalAppearance: () -> Unit = {},
    onTerminalTermType: (String) -> Unit = {},
    onTerminalBufferLines: (Int) -> Unit = {},
    onPreventSleeping: (Boolean) -> Unit = {},
    onBackgroundKeepMinutes: (Int) -> Unit = {},
    onOpenSnippets: () -> Unit,
    onOpenKeyboards: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onExport: () -> Unit,
    onImport: () -> Unit,
    /**
     * Feedback log → LogHub. Called with user note (may be blank) after dialog submit.
     * Implementer should upload then toast result; may be slow (network).
     */
    onFeedbackLog: ((note: String) -> Unit)? = null,
    onClearDiagLog: (() -> Unit)? = null,
    /** Official builds carry a LogHub token; community builds offer GitHub only. */
    logHubAvailable: Boolean = onFeedbackLog != null,
    /** Save the redacted log, then open a prefilled GitHub issue. */
    onFeedbackGitHub: ((note: String) -> Unit)? = null,
    /** Save the redacted diagnostic zip without filing anything. */
    onExportDiagLog: (() -> Unit)? = null,
    onOpenGitHubIssues: (() -> Unit)? = null,
) {
    val theme = LocalMtmuxTheme.current
    var showTermType by remember { mutableStateOf(false) }
    var showBuffer by remember { mutableStateOf(false) }
    var bufferDraft by remember { mutableStateOf(terminalBufferLines.toString()) }
    var showBackgroundKeep by remember { mutableStateOf(false) }
    var backgroundKeepDraft by remember { mutableStateOf("") }
    var showFeedback by remember { mutableStateOf(false) }
    var feedbackNote by remember { mutableStateOf("") }
    var feedbackSubmitting by remember { mutableStateOf(false) }

    // Material You: large title + grouped surface cards (scheme 04).
    Column(
        Modifier
            .fillMaxSize()
            .background(theme.colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("screen_settings"),
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = theme.colors.onSurface,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            SectionTitle(stringResource(R.string.section_language))
            SettingsGroup {
                AppLanguage.entries.forEach { lang ->
                    val title = when (lang) {
                        AppLanguage.SYSTEM -> stringResource(R.string.lang_system)
                        AppLanguage.CHINESE -> stringResource(R.string.lang_zh)
                        AppLanguage.ENGLISH -> stringResource(R.string.lang_en)
                    }
                    val sub = when (lang) {
                        AppLanguage.SYSTEM -> stringResource(R.string.lang_subtitle_system)
                        AppLanguage.CHINESE -> stringResource(R.string.lang_subtitle_zh)
                        AppLanguage.ENGLISH -> stringResource(R.string.lang_subtitle_en)
                    }
                    SubtitleRow(
                        title = if (appLanguage == lang) "✓ $title" else title,
                        subtitle = sub,
                        onClick = { onAppLanguage(lang) },
                        testTag = "settings_lang_${lang.name}",
                    )
                }
            }
            SectionTitle(stringResource(R.string.section_appearance))
            SettingsGroup {
                AppearanceMode.entries.forEach { mode ->
                    val title = when (mode) {
                        AppearanceMode.SYSTEM -> stringResource(R.string.appearance_system)
                        AppearanceMode.LIGHT -> stringResource(R.string.appearance_light)
                        AppearanceMode.DARK -> stringResource(R.string.appearance_dark)
                        AppearanceMode.CUSTOM -> stringResource(R.string.appearance_custom)
                    }
                    RowClick(
                        title = title,
                        selected = appearance == mode,
                        onClick = { onAppearance(mode) },
                        testTag = "settings_appearance_${mode.name}",
                    )
                }
            }
            if (appearance == AppearanceMode.CUSTOM) {
                SectionTitle(stringResource(R.string.section_theme))
                SettingsGroup {
                    AllThemes.forEach { spec ->
                        RowClick(
                            title = spec.displayName,
                            selected = customThemeId == spec.id,
                            onClick = { onCustomTheme(spec.id) },
                            testTag = "settings_theme_${spec.id}",
                        )
                    }
                }
            }
            SectionTitle(stringResource(R.string.section_terminal))
            SettingsGroup {
                SubtitleRow(
                    title = stringResource(R.string.settings_font_colors),
                    subtitle = stringResource(R.string.settings_font_colors_sub, terminalFontSizePx),
                    onClick = onOpenTerminalAppearance,
                    testTag = "settings_terminal_appearance",
                )
                SubtitleRow(
                    title = stringResource(R.string.settings_term_type),
                    subtitle = terminalTermType,
                    onClick = { showTermType = true },
                    testTag = "settings_term_type",
                )
                SubtitleRow(
                    title = stringResource(R.string.settings_buffer),
                    subtitle = stringResource(R.string.settings_buffer_sub, terminalBufferLines),
                    onClick = {
                        bufferDraft = terminalBufferLines.toString()
                        showBuffer = true
                    },
                    testTag = "settings_buffer_size",
                )
                SubtitleRow(
                    title = stringResource(R.string.settings_bg_keep),
                    subtitle = if (backgroundKeepMinutes == 0) {
                        stringResource(R.string.settings_bg_keep_off)
                    } else {
                        stringResource(R.string.settings_bg_keep_sub, keepDurationLabel(backgroundKeepMinutes))
                    },
                    onClick = {
                        backgroundKeepDraft = backgroundKeepMinutes
                            .takeIf { it !in UserPreferences.BACKGROUND_KEEP_PRESETS }
                            ?.toString()
                            .orEmpty()
                        showBackgroundKeep = true
                    },
                    testTag = "settings_background_keep",
                )
                SwitchRow(
                    title = stringResource(R.string.settings_keep_awake),
                    subtitle = stringResource(R.string.settings_keep_awake_sub),
                    checked = preventSleeping,
                    onChange = onPreventSleeping,
                    testTag = "settings_prevent_sleep",
                )
                SwitchRow(stringResource(R.string.settings_autocomplete), autocompleteEnabled, onAutocomplete, testTag = "settings_autocomplete")
                SwitchRow(stringResource(R.string.settings_open_last), openLastSession, onOpenLast, testTag = "settings_open_last")
                RowClick(stringResource(R.string.settings_snippets), selected = false, onClick = onOpenSnippets, testTag = "settings_snippets")
                RowClick(stringResource(R.string.settings_keyboards), selected = false, onClick = onOpenKeyboards, testTag = "settings_keyboards")
            }
            SectionTitle(stringResource(R.string.section_backup))
            SettingsGroup {
                RowClick(stringResource(R.string.settings_export), selected = false, onClick = onExport, testTag = "settings_export")
                RowClick(stringResource(R.string.settings_import), selected = false, onClick = onImport, testTag = "settings_import")
            }
            if (onFeedbackLog != null || onFeedbackGitHub != null) {
                SectionTitle(stringResource(R.string.section_diagnostics))
                SettingsGroup {
                    RowClick(
                        stringResource(R.string.settings_feedback),
                        selected = false,
                        onClick = {
                            feedbackNote = ""
                            feedbackSubmitting = false
                            showFeedback = true
                        },
                        testTag = "settings_feedback_log",
                    )
                    if (onExportDiagLog != null) {
                        RowClick(
                            stringResource(R.string.settings_export_diag),
                            selected = false,
                            onClick = onExportDiagLog,
                            testTag = "settings_export_diag",
                        )
                    }
                    if (onClearDiagLog != null) {
                        RowClick(
                            stringResource(R.string.settings_clear_diag),
                            selected = false,
                            onClick = onClearDiagLog,
                            testTag = "settings_clear_diag",
                        )
                    }
                }
            }
            SectionTitle(stringResource(R.string.section_about))
            SettingsGroup {
                Text(
                    "Muxa $appVersion",
                    color = theme.colors.onSurfaceMuted,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("settings_about"),
                )
                SubtitleRow(
                    title = stringResource(R.string.settings_privacy),
                    subtitle = stringResource(R.string.settings_privacy_sub),
                    onClick = onOpenPrivacy,
                    testTag = "settings_privacy",
                )
                if (onOpenGitHubIssues != null) {
                    SubtitleRow(
                        title = stringResource(R.string.settings_github_issues),
                        subtitle = "github.com/rainyday01/Muxa",
                        onClick = onOpenGitHubIssues,
                        testTag = "settings_github_issues",
                    )
                }
            }
        }
    }

    if (showTermType) {
        AlertDialog(
            onDismissRequest = { showTermType = false },
            title = { Text(stringResource(R.string.settings_term_type)) },
            text = {
                Column {
                    UserPreferences.TERM_TYPES.forEach { t ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = t == terminalTermType,
                                    onClick = {
                                        onTerminalTermType(t)
                                        showTermType = false
                                    },
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = t == terminalTermType,
                                onClick = {
                                    onTerminalTermType(t)
                                    showTermType = false
                                },
                            )
                            Text(t, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTermType = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showBuffer) {
        AlertDialog(
            onDismissRequest = { showBuffer = false },
            title = { Text(stringResource(R.string.settings_buffer)) },
            text = {
                OutlinedTextField(
                    value = bufferDraft,
                    onValueChange = { bufferDraft = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text(stringResource(R.string.settings_buffer_lines)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val n = bufferDraft.toIntOrNull()
                            ?: UserPreferences.DEFAULT_BUFFER_LINES
                        onTerminalBufferLines(
                            n.coerceIn(UserPreferences.MIN_BUFFER_LINES, UserPreferences.MAX_BUFFER_LINES),
                        )
                        showBuffer = false
                    },
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showBuffer = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showBackgroundKeep) {
        AlertDialog(
            onDismissRequest = { showBackgroundKeep = false },
            title = { Text(stringResource(R.string.settings_bg_keep)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    UserPreferences.BACKGROUND_KEEP_PRESETS.forEach { minutes ->
                        val choose = {
                            onBackgroundKeepMinutes(minutes)
                            showBackgroundKeep = false
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = minutes == backgroundKeepMinutes, onClick = choose)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = minutes == backgroundKeepMinutes, onClick = choose)
                            Text(keepDurationLabel(minutes), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    OutlinedTextField(
                        value = backgroundKeepDraft,
                        onValueChange = { backgroundKeepDraft = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text(stringResource(R.string.settings_bg_keep_custom_label)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("settings_background_keep_custom"),
                    )
                    Text(
                        stringResource(R.string.settings_bg_keep_note),
                        color = theme.colors.onSurfaceMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = backgroundKeepDraft.toIntOrNull()?.let { it > 0 } == true,
                    onClick = {
                        onBackgroundKeepMinutes(
                            backgroundKeepDraft.toInt()
                                .coerceAtMost(UserPreferences.MAX_BACKGROUND_KEEP_MINUTES),
                        )
                        showBackgroundKeep = false
                    },
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundKeep = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showFeedback && (onFeedbackLog != null || onFeedbackGitHub != null)) {
        val sendToDev = onFeedbackLog?.takeIf { logHubAvailable }
        AlertDialog(
            onDismissRequest = {
                if (!feedbackSubmitting) showFeedback = false
            },
            title = { Text(stringResource(R.string.feedback_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.feedback_body),
                        color = theme.colors.onSurfaceMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    OutlinedTextField(
                        value = feedbackNote,
                        onValueChange = { if (it.length <= 500) feedbackNote = it },
                        label = { Text(stringResource(R.string.feedback_note_label)) },
                        placeholder = { Text(stringResource(R.string.feedback_note_hint)) },
                        minLines = 3,
                        maxLines = 5,
                        enabled = !feedbackSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_feedback_note"),
                    )
                    if (onFeedbackGitHub != null) {
                        Text(
                            stringResource(R.string.feedback_github_hint),
                            color = theme.colors.onSurfaceMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    if (onFeedbackGitHub != null) {
                        TextButton(
                            enabled = !feedbackSubmitting,
                            onClick = {
                                showFeedback = false
                                onFeedbackGitHub(feedbackNote.trim())
                            },
                            modifier = Modifier.testTag("settings_feedback_github"),
                        ) { Text(stringResource(R.string.feedback_send_github)) }
                    }
                    if (sendToDev != null) {
                        TextButton(
                            enabled = !feedbackSubmitting,
                            onClick = {
                                feedbackSubmitting = true
                                sendToDev(feedbackNote.trim())
                                showFeedback = false
                                feedbackSubmitting = false
                            },
                            modifier = Modifier.testTag("settings_feedback_submit"),
                        ) {
                            Text(
                                if (feedbackSubmitting) stringResource(R.string.submitting)
                                else stringResource(R.string.feedback_send_dev),
                            )
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !feedbackSubmitting,
                    onClick = { showFeedback = false },
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    val theme = LocalMtmuxTheme.current
    Text(
        text,
        color = theme.colors.primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    val theme = LocalMtmuxTheme.current
    Card(
        shape = RoundedCornerShape(theme.cardRadiusDp.dp),
        colors = CardDefaults.cardColors(containerColor = theme.colors.cardGlass),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(content = content)
    }
}

@Composable
private fun RowClick(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String? = null,
) {
    val theme = LocalMtmuxTheme.current
    Text(
        text = if (selected) "✓ $title" else title,
        color = if (selected) theme.colors.primary else theme.colors.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .then(
                if (testTag != null) {
                    Modifier
                        .testTag(testTag)
                        .semantics { contentDescription = testTag }
                } else {
                    Modifier
                },
            ),
    )
}

@Composable
private fun SubtitleRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String? = null,
) {
    val theme = LocalMtmuxTheme.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .then(
                if (testTag != null) {
                    Modifier
                        .testTag(testTag)
                        .semantics { contentDescription = testTag }
                } else {
                    Modifier
                },
            ),
    ) {
        Text(title, color = theme.colors.onSurface, fontSize = 16.sp)
        Text(
            subtitle,
            color = theme.colors.onSurfaceMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    testTag: String? = null,
    subtitle: String? = null,
) {
    val theme = LocalMtmuxTheme.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(
                if (testTag != null) {
                    Modifier
                        .testTag(testTag)
                        .semantics { contentDescription = testTag }
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = theme.colors.onSurface)
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = theme.colors.onSurfaceMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Termux-style font size control: ±2 px steps, monospace preview.
 * Termux stores px and calls TerminalView.setTextSize(px) — same here (min 13 = 0.1.22).
 */
@Composable
private fun FontSizeRow(
    sizePx: Int,
    minPx: Int,
    maxPx: Int,
    onChange: (Int) -> Unit,
) {
    val theme = LocalMtmuxTheme.current
    // Preview uses sp-equivalent so Compose Text matches paint px roughly on this density
    val density = androidx.compose.ui.platform.LocalDensity.current
    val previewSp = with(density) { sizePx.toFloat().toSp() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("settings_font_size")
            .semantics { contentDescription = "settings_font_size" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_font_size), color = theme.colors.onSurface)
            Text(
                stringResource(R.string.settings_font_preview, sizePx),
                color = theme.colors.onSurfaceMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = previewSp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { onChange((sizePx - 2).coerceAtLeast(minPx)) },
                enabled = sizePx > minPx,
                modifier = Modifier.testTag("settings_font_minus"),
            ) { Text("A−") }
            Text(
                "$sizePx",
                color = theme.colors.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            TextButton(
                onClick = { onChange((sizePx + 2).coerceAtMost(maxPx)) },
                enabled = sizePx < maxPx,
                modifier = Modifier.testTag("settings_font_plus"),
            ) { Text("A+") }
        }
    }
}
