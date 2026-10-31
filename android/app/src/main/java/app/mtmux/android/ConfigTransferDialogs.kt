package app.mtmux.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mtmux.core.data.export.ConfigExport
import app.mtmux.core.ui.R as UiR
import app.mtmux.core.ui.theme.LocalMtmuxTheme

/** Where an encrypted export goes. */
enum class ExportTarget { File, Clipboard }

/**
 * Asks for the export password (twice) and whether to include saved
 * passwords and keys. [busy] disables the buttons while encrypting.
 */
@Composable
fun ExportConfigDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onExport: (password: CharArray, includeSecrets: Boolean, target: ExportTarget) -> Unit,
) {
    var pwd by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var includeSecrets by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val tooShort = stringResource(UiR.string.export_err_short, ConfigExport.MIN_PASSWORD_LENGTH)
    val mismatch = stringResource(UiR.string.export_err_mismatch)

    fun submit(target: ExportTarget) {
        error = when {
            pwd.length < ConfigExport.MIN_PASSWORD_LENGTH -> tooShort
            pwd != confirm -> mismatch
            else -> null
        }
        if (error == null) onExport(pwd.toCharArray(), includeSecrets, target)
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(UiR.string.export_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PasswordField(pwd, { pwd = it; error = null }, stringResource(UiR.string.export_password), !busy)
                PasswordField(confirm, { confirm = it; error = null }, stringResource(UiR.string.export_password_confirm), !busy)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Checkbox(checked = includeSecrets, onCheckedChange = { includeSecrets = it }, enabled = !busy)
                    Text(stringResource(UiR.string.export_include_secrets))
                }
                Text(
                    stringResource(
                        if (includeSecrets) UiR.string.export_secrets_hint_on else UiR.string.export_secrets_hint_off,
                    ),
                    fontSize = 12.sp,
                    color = LocalMtmuxTheme.current.colors.onSurfaceMuted,
                )
                error?.let {
                    Text(it, color = LocalMtmuxTheme.current.colors.warning, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(enabled = !busy, onClick = { submit(ExportTarget.Clipboard) }) {
                    Text(stringResource(UiR.string.export_clipboard))
                }
                TextButton(enabled = !busy, onClick = { submit(ExportTarget.File) }) {
                    Text(stringResource(if (busy) UiR.string.export_working else UiR.string.export_file))
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(UiR.string.cancel)) }
        },
    )
}

/**
 * Takes an encrypted export pasted or loaded from a file, plus its password.
 * [loadedText] fills the field after [onPickFile] returns a file.
 */
@Composable
fun ImportConfigDialog(
    busy: Boolean,
    loadedText: String?,
    error: String?,
    onDismiss: () -> Unit,
    onPickFile: () -> Unit,
    onImport: (blob: String, password: CharArray) -> Unit,
) {
    var blob by remember(loadedText) { mutableStateOf(loadedText.orEmpty()) }
    var pwd by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(UiR.string.import_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = blob,
                    onValueChange = { blob = it },
                    label = { Text(stringResource(UiR.string.import_json)) },
                    minLines = 3,
                    maxLines = 6,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(enabled = !busy, onClick = onPickFile) { Text(stringResource(UiR.string.import_pick_file)) }
                PasswordField(pwd, { pwd = it }, stringResource(UiR.string.import_password), !busy)
                error?.let {
                    Text(it, color = LocalMtmuxTheme.current.colors.warning, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && blob.isNotBlank() && pwd.isNotEmpty(),
                onClick = { onImport(blob, pwd.toCharArray()) },
            ) { Text(stringResource(if (busy) UiR.string.export_working else UiR.string.import_action)) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(UiR.string.cancel)) }
        },
    )
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String, enabled: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    )
}
