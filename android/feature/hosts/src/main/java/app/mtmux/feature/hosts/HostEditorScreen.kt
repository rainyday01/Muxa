package app.mtmux.feature.hosts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.mtmux.core.ui.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mtmux.core.model.Host
import app.mtmux.core.model.TransportPreference
import app.mtmux.core.ui.theme.LocalMtmuxTheme

data class HostEditorResult(
    val host: Host,
    val password: CharArray?,
    val privateKeyPem: String?,
    val passphrase: CharArray?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostEditorScreen(
    initial: Host?,
    /**
     * Previously saved password (loaded from secure store). Shown with password mask
     * so the user can change transport / other fields and save without retyping.
     */
    savedPassword: String? = null,
    /** Saved private-key passphrase, if any. */
    savedPassphrase: String? = null,
    onBack: () -> Unit,
    onSave: (HostEditorResult) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val theme = LocalMtmuxTheme.current
    val ctx = LocalContext.current
    var alias by remember { mutableStateOf(initial?.alias.orEmpty()) }
    var hostname by remember { mutableStateOf(initial?.hostname.orEmpty()) }
    var port by remember { mutableStateOf((initial?.port ?: 22).toString()) }
    var username by remember { mutableStateOf(initial?.username.orEmpty()) }
    // Prefill with saved secret so PasswordVisualTransformation shows bullets (密文外观).
    var password by remember { mutableStateOf(savedPassword.orEmpty()) }
    var privateKey by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf(savedPassphrase.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    val editing = initial != null
    val hasSavedAuth = initial?.hasPassword == true ||
        initial?.hasPrivateKey == true ||
        !savedPassword.isNullOrEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .background(theme.colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("screen_host_editor"),
    ) {
        TopAppBar(
            title = {
                Text(
                    if (initial == null) stringResource(R.string.host_add_title) else stringResource(R.string.host_edit_title),
                    fontWeight = FontWeight.Bold,
                    color = theme.colors.onSurface,
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .testTag("host_editor_back")
                        .semantics { contentDescription = "host_editor_back" },
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "host_editor_back",
                        tint = theme.colors.primary,
                    )
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.colors.background),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Field(stringResource(R.string.host_alias), alias, testTag = "host_alias") { alias = it }
            Field(stringResource(R.string.host_hostname), hostname, testTag = "host_hostname") { newVal ->
                val p = HostFieldSanitizer.parseHostname(newVal)
                hostname = p.host
                if (!p.username.isNullOrBlank() && username.isBlank()) {
                    username = p.username
                }
                if (p.port != null) {
                    port = p.port.toString()
                }
                if (!p.password.isNullOrBlank() && password.isBlank()) {
                    password = p.password
                }
                p.localizedNote(ctx)?.let { error = it }
            }
            Field(stringResource(R.string.host_port), port, testTag = "host_port") {
                port = it.filter { ch -> ch.isDigit() }.ifEmpty { "22" }
            }
            Field(stringResource(R.string.host_username), username, testTag = "host_username") { username = it }
            Spacer(Modifier.height(8.dp))
            Text(
                if (editing && hasSavedAuth) {
                    stringResource(R.string.host_auth_saved)
                } else {
                    stringResource(R.string.host_auth_need)
                },
                color = theme.colors.onSurfaceMuted,
                fontSize = 12.sp,
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = {
                    Text(
                        when {
                            !savedPassword.isNullOrEmpty() || initial?.hasPassword == true ->
                                stringResource(R.string.host_password_saved)
                            else -> stringResource(R.string.host_password_optional)
                        },
                    )
                },
                placeholder = {
                    if (editing && password.isEmpty() && (initial?.hasPassword == true)) {
                        Text(stringResource(R.string.host_password_keep))
                    }
                },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .testTag("host_password")
                    .semantics { contentDescription = "host_password" },
                singleLine = true,
            )
            OutlinedTextField(
                value = privateKey,
                onValueChange = { privateKey = it },
                label = {
                    Text(
                        if (initial?.hasPrivateKey == true) {
                            stringResource(R.string.host_key_saved)
                        } else {
                            stringResource(R.string.host_key_paste)
                        },
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(vertical = 6.dp)
                    .testTag("host_private_key")
                    .semantics { contentDescription = "host_private_key" },
            )
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text(stringResource(R.string.host_passphrase)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .testTag("host_passphrase")
                    .semantics { contentDescription = "host_passphrase" },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))

            if (error != null) {
                Text(error!!, color = theme.colors.warning, modifier = Modifier.padding(bottom = 8.dp))
            }
            Button(
                onClick = {
                    if (alias.isBlank() || hostname.isBlank() || username.isBlank()) {
                        error = ctx.getString(R.string.host_err_required)
                        return@Button
                    }
                    val parsed = HostFieldSanitizer.parseHostname(hostname)
                    hostname = parsed.host
                    if (!parsed.username.isNullOrBlank() && username.isBlank()) {
                        username = parsed.username
                    }
                    if (parsed.port != null) {
                        port = parsed.port.toString()
                    }
                    var pw = password
                    if (!parsed.password.isNullOrBlank() && pw.isBlank()) {
                        pw = parsed.password
                        password = pw
                    }
                    if (parsed.host.isBlank() || !HostFieldSanitizer.isPlausibleHost(parsed.host)) {
                        error = ctx.getString(R.string.host_err_hostname, parsed.host, parsed.host.length)
                        return@Button
                    }
                    // Edit: keep existing secrets when fields left blank / unchanged.
                    val keepPassword = editing && (
                        initial?.hasPassword == true || !savedPassword.isNullOrEmpty()
                    )
                    val keepKey = editing && initial?.hasPrivateKey == true
                    if (pw.isBlank() && privateKey.isBlank() && !keepPassword && !keepKey) {
                        error = ctx.getString(R.string.host_err_auth)
                        return@Button
                    }
                    error = null
                    // Only rewrite password store when user actually entered something.
                    // If prefilled with savedPassword and unchanged → still pass it (or null both OK).
                    // Prefer: blank on edit with keepPassword → null (repo keeps existing).
                    val passwordToSave: CharArray? = when {
                        pw.isNotEmpty() -> pw.toCharArray()
                        else -> null // keep existing in repository
                    }
                    val host = Host(
                        id = initial?.id.orEmpty(),
                        alias = alias.trim(),
                        hostname = parsed.host,
                        port = port.toIntOrNull() ?: 22,
                        username = username.trim(),
                        transport = TransportPreference.SSH,
                        hasPrivateKey = privateKey.isNotBlank() || keepKey,
                        hasPassword = passwordToSave != null || keepPassword,
                        tmuxInstalled = initial?.tmuxInstalled,
                        moshInstalled = initial?.moshInstalled,
                        lastLatencyMs = initial?.lastLatencyMs,
                        lastCheckedAtEpochMs = initial?.lastCheckedAtEpochMs,
                        lastMemAvailKb = initial?.lastMemAvailKb,
                        lastMemTotalKb = initial?.lastMemTotalKb,
                        lastDiskAvailKb = initial?.lastDiskAvailKb,
                        lastDiskTotalKb = initial?.lastDiskTotalKb,
                        lastStatsAtEpochMs = initial?.lastStatsAtEpochMs,
                    )
                    onSave(
                        HostEditorResult(
                            host = host,
                            password = passwordToSave,
                            privateKeyPem = privateKey.takeIf { it.isNotBlank() },
                            passphrase = passphrase.takeIf { it.isNotEmpty() }?.toCharArray(),
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("host_save")
                    .semantics { contentDescription = "host_save" },
            ) { Text(stringResource(R.string.save)) }
            if (onDelete != null) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onDelete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("host_delete"),
                ) {
                    Text(stringResource(R.string.host_delete))
                }
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    testTag: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag(testTag)
            .semantics { contentDescription = testTag },
        singleLine = true,
    )
}
