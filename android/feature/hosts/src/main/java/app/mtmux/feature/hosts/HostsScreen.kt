package app.mtmux.feature.hosts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.mtmux.core.ui.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mtmux.core.model.Host
import app.mtmux.core.ui.theme.LocalMtmuxTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HostsScreen(
    hosts: List<Host>,
    onAdd: () -> Unit,
    onOpen: (Host) -> Unit,
    onProbe: (Host) -> Unit,
    onInstall: (Host) -> Unit,
) {
    val theme = LocalMtmuxTheme.current
    var showQuickInstall by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(theme.colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("screen_hosts"),
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.hosts_title),
                style = MaterialTheme.typography.headlineMedium,
                color = theme.colors.onSurface,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.testTag("host_list"),
            ) {
                items(hosts, key = { it.id }) { host ->
                    Card(
                        shape = RoundedCornerShape(theme.cardRadiusDp.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.colors.cardGlass),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("host_card_${host.alias}")
                            .semantics { contentDescription = "host_card_${host.alias}" },
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(theme.colors.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        host.alias.take(1).uppercase(),
                                        color = theme.colors.onPrimaryContainer,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp,
                                    )
                                }
                                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                    Text(
                                        host.alias,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontSize = 17.sp,
                                        color = theme.colors.onSurface,
                                    )
                                    Text(
                                        "${host.username}@${host.hostname}:${host.port}",
                                        color = theme.colors.onSurfaceMuted,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                            Row(
                                Modifier.padding(top = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                EnvDotLabel("tmux", host.tmuxInstalled)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "SSH" +
                                        (host.lastLatencyMs?.let { " · ${it}ms" } ?: ""),
                                    color = theme.colors.onSurfaceMuted,
                                    fontSize = 12.sp,
                                )
                            }
                            FlowRow {
                                TextButton(
                                    onClick = { onOpen(host) },
                                    modifier = Modifier.testTag("host_edit_${host.alias}"),
                                ) { Text(stringResource(R.string.host_edit)) }
                                TextButton(
                                    onClick = { onProbe(host) },
                                    modifier = Modifier.testTag("host_probe_${host.alias}"),
                                ) { Text(stringResource(R.string.host_probe)) }
                                TextButton(
                                    onClick = { showQuickInstall = true },
                                    modifier = Modifier.testTag("host_tools_${host.alias}"),
                                ) { Text(stringResource(R.string.host_quick_tools)) }
                                if (host.tmuxInstalled == false) {
                                    TextButton(onClick = { onInstall(host) }) {
                                        Text(stringResource(R.string.host_install))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("host_add")
                .semantics { contentDescription = "host_add" },
            containerColor = theme.colors.primaryContainer,
            contentColor = theme.colors.onPrimaryContainer,
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "host_add")
        }
        if (showQuickInstall) {
            QuickInstallToolsDialog(onDismiss = { showQuickInstall = false })
        }
    }
}

@Composable
private fun EnvDotLabel(name: String, ready: Boolean?) {
    val theme = LocalMtmuxTheme.current
    val color = when (ready) {
        true -> theme.colors.success
        false -> Color(0xFFFFB4AB)
        null -> theme.colors.onSurfaceMuted
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(5.dp))
        Text(name, color = theme.colors.onSurfaceMuted, fontSize = 12.sp)
    }
}
