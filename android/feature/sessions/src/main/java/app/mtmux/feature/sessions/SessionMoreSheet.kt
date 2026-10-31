package app.mtmux.feature.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mtmux.core.ui.R
import app.mtmux.core.model.Host
import app.mtmux.core.model.TmuxSession
import app.mtmux.core.ui.theme.LocalMtmuxTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionMoreSheet(
    session: TmuxSession,
    onDismiss: () -> Unit,
    onEnter: () -> Unit,
    onRefreshSnapshot: () -> Unit,
    onRename: (String) -> Unit,
    onKill: () -> Unit,
    onCopyCommand: () -> Unit,
    onShowWindows: () -> Unit,
    onMoveSessionUp: (() -> Unit)? = null,
    onMoveSessionDown: (() -> Unit)? = null,
    onMoveHostUp: (() -> Unit)? = null,
    onMoveHostDown: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var renaming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(session.name) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(session.hostAlias ?: session.hostId)
            Text(session.name, modifier = Modifier.padding(bottom = 8.dp), fontWeight = FontWeight.SemiBold)
            if (renaming) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.more_rename_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                TextButton(onClick = {
                    if (newName.isNotBlank()) onRename(newName.trim())
                    renaming = false
                }) { Text(stringResource(R.string.more_rename_confirm)) }
            } else {
                TextButton(onClick = onEnter, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.session_enter)) }
                TextButton(onClick = onRefreshSnapshot, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.more_refresh_snap)) }
                TextButton(onClick = onShowWindows, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.more_windows)) }
                TextButton(onClick = { renaming = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.more_rename)) }
                TextButton(onClick = onCopyCommand, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.more_copy_attach)) }

                if (onMoveSessionUp != null || onMoveSessionDown != null ||
                    onMoveHostUp != null || onMoveHostDown != null
                ) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text(
                        stringResource(R.string.more_sort),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                    if (onMoveSessionUp != null) {
                        TextButton(
                            onClick = onMoveSessionUp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("session_move_up"),
                        ) { Text(stringResource(R.string.more_session_up)) }
                    }
                    if (onMoveSessionDown != null) {
                        TextButton(
                            onClick = onMoveSessionDown,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("session_move_down"),
                        ) { Text(stringResource(R.string.more_session_down)) }
                    }
                    if (onMoveHostUp != null) {
                        TextButton(
                            onClick = onMoveHostUp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("host_move_up"),
                        ) { Text(stringResource(R.string.more_host_up)) }
                    }
                    if (onMoveHostDown != null) {
                        TextButton(
                            onClick = onMoveHostDown,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("host_move_down"),
                        ) { Text(stringResource(R.string.more_host_down)) }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                TextButton(onClick = onKill, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.more_kill)) }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.close)) }
        }
    }
}

/**
 * Create a new tmux session on an explicitly chosen host.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewSessionDialog(
    hosts: List<Host>,
    /** Pre-select this host id when opening (e.g. only one host). */
    initialHostId: String? = null,
    onDismiss: () -> Unit,
    onCreate: (host: Host, name: String, template: String?) -> Unit,
) {
    val theme = LocalMtmuxTheme.current
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("work") }
    var dir by remember { mutableStateOf("") }
    var dirMode by remember { mutableStateOf(NewSessionCommand.DirMode.CreateAndEnter) }
    var agentArgv by remember { mutableStateOf("") }
    var skipDanger by remember { mutableStateOf(false) }
    var yolo by remember { mutableStateOf(false) }
    var template by remember { mutableStateOf("") }
    var selectedHostId by remember {
        mutableStateOf(
            initialHostId
                ?: hosts.firstOrNull()?.id
                ?: "",
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    var nameLocked by remember { mutableStateOf(false) }

    fun syncFromChips() {
        template = NewSessionCommand.compose(dir, dirMode, agentArgv, skipDanger, yolo)
        if (!nameLocked) {
            name = NewSessionCommand.suggestedName(dir, agentArgv, name)
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_session_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.new_session_pick_host),
                    fontWeight = FontWeight.SemiBold,
                    color = theme.colors.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                if (hosts.isEmpty()) {
                    Text(
                        stringResource(R.string.new_session_no_hosts),
                        color = theme.colors.warning,
                        fontSize = 13.sp,
                    )
                } else {
                    hosts.forEach { h ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selectedHostId = h.id }
                                .padding(vertical = 2.dp)
                                .testTag("new_session_host_${h.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedHostId == h.id,
                                onClick = { selectedHostId = h.id },
                            )
                            Column(Modifier.padding(start = 4.dp)) {
                                Text(h.alias, fontWeight = FontWeight.Medium)
                                Text(
                                    "${h.username}@${h.hostname}:${h.port}",
                                    fontSize = 11.sp,
                                    color = theme.colors.onSurfaceMuted,
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameLocked = true
                    },
                    label = { Text(stringResource(R.string.new_session_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("new_session_name"),
                )
                OutlinedTextField(
                    value = dir,
                    onValueChange = {
                        dir = it
                        syncFromChips()
                    },
                    label = { Text(stringResource(R.string.new_session_dir)) },
                    placeholder = { Text(stringResource(R.string.new_session_dir_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("new_session_dir"),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    FilterChip(
                        selected = dirMode == NewSessionCommand.DirMode.CreateAndEnter,
                        onClick = {
                            dirMode = NewSessionCommand.DirMode.CreateAndEnter
                            syncFromChips()
                        },
                        label = { Text(stringResource(R.string.new_session_dir_mkdir)) },
                        modifier = Modifier.testTag("new_session_dir_mkdir"),
                    )
                    FilterChip(
                        selected = dirMode == NewSessionCommand.DirMode.EnterExisting,
                        onClick = {
                            dirMode = NewSessionCommand.DirMode.EnterExisting
                            syncFromChips()
                        },
                        label = { Text(stringResource(R.string.new_session_dir_cd)) },
                        modifier = Modifier.testTag("new_session_dir_cd"),
                    )
                }
                Text(
                    stringResource(R.string.new_session_agent),
                    fontWeight = FontWeight.SemiBold,
                    color = theme.colors.onSurface,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NewSessionCommand.Agent.all.forEach { ag ->
                        FilterChip(
                            selected = agentArgv == ag.argv,
                            onClick = {
                                agentArgv = if (agentArgv == ag.argv) "" else ag.argv
                                if (agentArgv !in setOf("claude", "agy")) skipDanger = false
                                if (agentArgv != "codex") yolo = false
                                syncFromChips()
                            },
                            label = {
                                Text(
                                    when (ag.id) {
                                        "claude" -> stringResource(R.string.new_session_tpl_claude)
                                        "codex" -> stringResource(R.string.new_session_tpl_codex)
                                        "agy" -> stringResource(R.string.new_session_tpl_agy)
                                        else -> stringResource(R.string.new_session_tpl_grok)
                                    },
                                )
                            },
                            modifier = Modifier.testTag("new_session_agent_${ag.id}"),
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    FilterChip(
                        selected = skipDanger,
                        onClick = {
                            skipDanger = !skipDanger
                            if (skipDanger) yolo = false
                            syncFromChips()
                        },
                        enabled = agentArgv in setOf("claude", "agy"),
                        label = { Text(stringResource(R.string.new_session_flag_skip)) },
                        modifier = Modifier.testTag("new_session_flag_skip"),
                    )
                    FilterChip(
                        selected = yolo,
                        onClick = {
                            yolo = !yolo
                            if (yolo) skipDanger = false
                            syncFromChips()
                        },
                        enabled = agentArgv == "codex",
                        label = { Text(stringResource(R.string.new_session_flag_yolo)) },
                        modifier = Modifier.testTag("new_session_flag_yolo"),
                    )
                }
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    label = { Text(stringResource(R.string.new_session_cmd)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("new_session_template"),
                )
                if (error != null) {
                    Text(
                        error!!,
                        color = theme.colors.warning,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val host = hosts.find { it.id == selectedHostId }
                    when {
                        hosts.isEmpty() -> error = ctx.getString(R.string.err_add_host_first)
                        host == null -> error = ctx.getString(R.string.err_pick_host)
                        name.isBlank() -> error = ctx.getString(R.string.err_session_name)
                        else -> onCreate(host, name.trim(), template.takeIf { it.isNotBlank() })
                    }
                },
                modifier = Modifier
                    .testTag("new_session_create")
                    .semantics { contentDescription = "new_session_create" },
            ) { Text(stringResource(R.string.create)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("new_session_cancel"),
            ) { Text(stringResource(R.string.cancel)) }
        },
    )
}
