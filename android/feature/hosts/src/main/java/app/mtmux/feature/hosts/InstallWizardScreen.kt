package app.mtmux.feature.hosts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.mtmux.core.ui.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mtmux.core.ui.theme.LocalMtmuxTheme
import app.mtmux.ssh.EnvProbe
import app.mtmux.ssh.TmuxControl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallWizardScreen(
    hostAlias: String,
    probe: EnvProbe,
    onBack: () -> Unit,
    onOpenInstallPty: (command: String) -> Unit,
    onReprobe: () -> Unit,
) {
    val theme = LocalMtmuxTheme.current
    val ctx = LocalContext.current
    val cmd = probe.installHint?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.env_pkg_ok)

    val needInstall = !probe.tmux
    val packagesReady = probe.packagesReady

    Column(
        Modifier
            .fillMaxSize()
            .background(theme.colors.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.env_title, hostAlias),
                    fontWeight = FontWeight.Bold,
                    color = theme.colors.onSurface,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = theme.colors.primary)
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
            StatusRow("tmux", probe.tmux, okLabel = stringResource(R.string.env_installed), badLabel = stringResource(R.string.env_missing))
            StatusRow("SSH", true, okLabel = stringResource(R.string.host_ssh_only), badLabel = "")

            val meta = buildString {
                append(probe.osPretty ?: stringResource(R.string.env_unknown_os))
                probe.packageManager?.let { append(" · $it") }
                probe.latencyMs?.let { append(" · ${it}ms") }
            }
            Text(
                meta,
                color = theme.colors.onSurfaceMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )

            // One-line tip only — no long step essays.
            val tip = when {
                packagesReady -> stringResource(R.string.env_ready_ssh)
                needInstall -> stringResource(R.string.env_need_pkg)
                else -> stringResource(R.string.env_recheck_hint)
            }
            Text(
                tip,
                color = theme.colors.onSurfaceMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            if (TmuxControl.isDarwin(probe.osPretty, probe.uname)) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = theme.colors.outline)
                Text(
                    stringResource(R.string.env_takeover_title),
                    fontWeight = FontWeight.SemiBold,
                    color = theme.colors.onSurface,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Text(
                    stringResource(R.string.env_takeover_body),
                    color = theme.colors.onSurfaceMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    TmuxControl.DESKTOP_TAKEOVER_SNIPPET,
                    fontFamily = FontFamily.Monospace,
                    color = theme.colors.terminalForeground,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.colors.terminalBackground)
                        .padding(10.dp),
                )
                OutlinedButton(
                    onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(
                            ClipData.newPlainText("tmux", TmuxControl.DESKTOP_TAKEOVER_SNIPPET),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) { Text(stringResource(R.string.env_takeover_copy)) }
            }

            if (needInstall) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = theme.colors.outline)
                Text(
                    stringResource(R.string.env_install_cmd),
                    fontWeight = FontWeight.SemiBold,
                    color = theme.colors.onSurface,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Text(
                    cmd,
                    fontFamily = FontFamily.Monospace,
                    color = theme.colors.terminalForeground,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.colors.terminalBackground)
                        .padding(10.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            if (needInstall && cmd.isNotBlank() && !cmd.startsWith("#")) {
                Button(
                    onClick = { onOpenInstallPty(cmd) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Text(stringResource(R.string.env_open_pty))
                }
            }
            if (needInstall) {
                OutlinedButton(
                    onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("install", cmd))
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) { Text(stringResource(R.string.env_copy_cmd)) }
            }
            OutlinedButton(
                onClick = onReprobe,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Text(stringResource(R.string.env_recheck))
            }
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Text(
                    when {
                        packagesReady -> stringResource(R.string.env_done)
                        else -> stringResource(R.string.env_back)
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    ready: Boolean,
    okLabel: String = stringResource(R.string.env_installed),
    badLabel: String = stringResource(R.string.env_missing),
) {
    val theme = LocalMtmuxTheme.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusDot(ready)
        Text(
            "$label  ${if (ready) okLabel else badLabel}",
            color = theme.colors.onSurface,
            fontSize = 15.sp,
        )
    }
}

@Composable
fun StatusDot(ready: Boolean, modifier: Modifier = Modifier) {
    val color = if (ready) Color(0xFF34C759) else Color(0xFFFF3B30)
    Spacer(
        modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}
