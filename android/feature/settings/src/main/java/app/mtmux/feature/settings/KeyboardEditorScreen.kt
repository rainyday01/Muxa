package app.mtmux.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.mtmux.core.ui.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mtmux.core.model.KeyAction
import app.mtmux.core.model.KeyActionType
import app.mtmux.core.model.KeyboardKey
import app.mtmux.core.model.KeyboardLayout
import app.mtmux.core.ui.theme.LocalMtmuxTheme
import java.util.UUID

/**
 * AND-051/052: list + edit keyboard layouts, JSON import/export.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardListScreen(
    layouts: List<KeyboardLayout>,
    activeId: String,
    onBack: () -> Unit,
    onSelectActive: (String) -> Unit,
    onEdit: (KeyboardLayout) -> Unit,
    onDuplicate: (KeyboardLayout) -> Unit,
    onDelete: (KeyboardLayout) -> Unit,
    onExport: (KeyboardLayout) -> Unit,
    onImport: () -> Unit,
    onNew: () -> Unit,
) {
    val theme = LocalMtmuxTheme.current
    var pendingDelete by remember { mutableStateOf<KeyboardLayout?>(null) }
    pendingDelete?.let { layout ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.kb_delete_confirm, layout.name)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(layout)
                }) { Text(stringResource(R.string.delete), color = theme.colors.warning) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(theme.colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("screen_keyboards"),
    ) {
        TopAppBar(
            title = {
                Text(stringResource(R.string.keyboards_title), fontWeight = FontWeight.Bold, color = theme.colors.onSurface)
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("keyboard_list_back"),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = theme.colors.primary)
                }
            },
            actions = {
                TextButton(
                    onClick = onImport,
                    modifier = Modifier
                        .testTag("keyboard_import")
                        .semantics { contentDescription = "keyboard_import" },
                ) { Text(stringResource(R.string.import_action)) }
                IconButton(
                    onClick = onNew,
                    modifier = Modifier
                        .testTag("keyboard_new")
                        .semantics { contentDescription = "keyboard_new" },
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.kb_new), tint = theme.colors.primary)
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.colors.background),
        )
        Text(
            stringResource(R.string.kb_list_hint),
            color = theme.colors.onSurfaceMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(layouts, key = { _, l -> l.id }) { _, layout ->
                LayoutRow(
                    layout = layout,
                    selected = layout.id == activeId,
                    onSelect = { onSelectActive(layout.id) },
                    onEdit = { onEdit(layout) },
                    onDuplicate = { onDuplicate(layout) },
                    onDelete = { pendingDelete = layout },
                    onExport = { onExport(layout) },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun LayoutRow(
    layout: KeyboardLayout,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    val theme = LocalMtmuxTheme.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.colors.cardGlass)
            .clickable(onClick = onSelect)
            .padding(12.dp)
            .testTag("keyboard_layout_${layout.id}")
            .semantics { contentDescription = "keyboard_layout_${layout.id}" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f)) {
                Text(
                    layout.name,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.colors.onSurface,
                )
                Text(
                    stringResource(
                        R.string.kb_keys_meta,
                        layout.id,
                        layout.barKeys.size + layout.expandedKeys.size,
                        stringResource(if (layout.builtin) R.string.kb_builtin else R.string.kb_custom),
                    ),
                    color = theme.colors.onSurfaceMuted,
                    fontSize = 11.sp,
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            layout.barKeys.take(8).forEach { key ->
                Text(
                    app.mtmux.core.ui.keyboard.BuiltinKeyLabels.of(key, layout.builtin),
                    fontSize = 11.sp,
                    color = theme.colors.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(theme.colors.outline)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Row(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(
                onClick = onDuplicate,
                modifier = Modifier.testTag("keyboard_dup_${layout.id}"),
            ) { Text(stringResource(R.string.duplicate)) }
            TextButton(
                onClick = onExport,
                modifier = Modifier.testTag("keyboard_export_${layout.id}"),
            ) { Text(stringResource(R.string.export)) }
            if (!layout.builtin) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
            } else {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.view)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardEditorScreen(
    initial: KeyboardLayout,
    readOnly: Boolean,
    onBack: () -> Unit,
    onSave: (KeyboardLayout) -> Unit,
) {
    val theme = LocalMtmuxTheme.current
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var barKeys by remember(initial.id) { mutableStateOf(initial.barKeys) }
    var expandedKeys by remember(initial.id) { mutableStateOf(initial.expandedKeys) }
    var editingSection by remember { mutableStateOf("bar") } // bar | expanded
    var addDialog by remember { mutableStateOf(false) }
    var editIndex by remember { mutableStateOf<Int?>(null) }

    val currentList = if (editingSection == "bar") barKeys else expandedKeys
    fun setCurrentList(list: List<KeyboardKey>) {
        if (editingSection == "bar") barKeys = list else expandedKeys = list
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(theme.colors.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        TopAppBar(
            title = {
                Text(
                    stringResource(if (readOnly) R.string.kb_view_layout else R.string.kb_edit_layout),
                    fontWeight = FontWeight.Bold,
                    color = theme.colors.onSurface,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = theme.colors.primary)
                }
            },
            actions = {
                if (!readOnly) {
                    TextButton(
                        onClick = {
                            onSave(
                                initial.copy(
                                    name = name.trim().ifBlank { initial.id },
                                    barKeys = barKeys,
                                    expandedKeys = expandedKeys,
                                    builtin = false,
                                ),
                            )
                        },
                    ) { Text(stringResource(R.string.save)) }
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.colors.background),
        )
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("id: ${initial.id}", color = theme.colors.onSurfaceMuted, fontSize = 12.sp)
            OutlinedTextField(
                value = name,
                onValueChange = { if (!readOnly) name = it },
                label = { Text(stringResource(R.string.kb_display_name)) },
                enabled = !readOnly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = editingSection == "bar",
                    onClick = { editingSection = "bar" },
                    label = { Text(stringResource(R.string.kb_bar_n, barKeys.size)) },
                )
                FilterChip(
                    selected = editingSection == "expanded",
                    onClick = { editingSection = "expanded" },
                    label = { Text(stringResource(R.string.kb_expanded_n, expandedKeys.size)) },
                )
                if (!readOnly) {
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { editIndex = null; addDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.kb_add_key), tint = theme.colors.primary)
                    }
                }
            }
        }
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(currentList, key = { i, k -> "${k.id}_$i" }) { index, key ->
                KeyRow(
                    key = key,
                    builtinLayout = initial.builtin,
                    readOnly = readOnly,
                    onUp = {
                        if (index > 0) {
                            val m = currentList.toMutableList()
                            val t = m[index - 1]
                            m[index - 1] = m[index]
                            m[index] = t
                            setCurrentList(m)
                        }
                    },
                    onDown = {
                        if (index < currentList.lastIndex) {
                            val m = currentList.toMutableList()
                            val t = m[index + 1]
                            m[index + 1] = m[index]
                            m[index] = t
                            setCurrentList(m)
                        }
                    },
                    onEdit = {
                        editIndex = index
                        addDialog = true
                    },
                    onDelete = {
                        setCurrentList(currentList.toMutableList().also { it.removeAt(index) })
                    },
                )
            }
        }
    }

    if (addDialog) {
        val existing = editIndex?.let { currentList.getOrNull(it) }
        KeyEditDialog(
            initial = existing,
            onDismiss = { addDialog = false; editIndex = null },
            onConfirm = { newKey ->
                val m = currentList.toMutableList()
                val idx = editIndex
                if (idx != null && idx in m.indices) m[idx] = newKey
                else m.add(newKey)
                setCurrentList(m)
                addDialog = false
                editIndex = null
            },
        )
    }
}

@Composable
private fun KeyRow(
    key: KeyboardKey,
    builtinLayout: Boolean,
    readOnly: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val theme = LocalMtmuxTheme.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(theme.colors.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                app.mtmux.core.ui.keyboard.BuiltinKeyLabels.of(key, builtinLayout),
                fontWeight = FontWeight.Medium,
                color = theme.colors.onSurface,
            )
            Text(
                actionSummary(key.action),
                color = theme.colors.onSurfaceMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        if (!readOnly) {
            IconButton(onClick = onUp) {
                Icon(Icons.Default.KeyboardArrowUp, null, tint = theme.colors.primary)
            }
            IconButton(onClick = onDown) {
                Icon(Icons.Default.KeyboardArrowDown, null, tint = theme.colors.primary)
            }
            TextButton(onClick = onEdit) { Text(stringResource(R.string.kb_change)) }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = theme.colors.warning)
            }
        }
    }
}

@Composable
private fun KeyEditDialog(
    initial: KeyboardKey?,
    onDismiss: () -> Unit,
    onConfirm: (KeyboardKey) -> Unit,
) {
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var type by remember {
        mutableStateOf(initial?.action?.type ?: KeyActionType.TEXT)
    }
    var keyCode by remember { mutableStateOf(initial?.action?.keyCode.orEmpty().ifBlank { "Escape" }) }
    var text by remember { mutableStateOf(initial?.action?.text.orEmpty().ifBlank { "" }) }
    var chord by remember {
        mutableStateOf(initial?.action?.keys?.joinToString(",") ?: "Control,c")
    }
    var modifier by remember {
        mutableStateOf(initial?.action?.modifier.orEmpty().ifBlank { "ctrl" })
    }
    var error by remember { mutableStateOf<String?>(null) }
    val errLabel = stringResource(R.string.kb_err_label)
    val errKeycode = stringResource(R.string.kb_err_keycode)
    val errText = stringResource(R.string.kb_err_text)
    val errChord = stringResource(R.string.kb_err_chord)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.kb_add_key_title else R.string.kb_edit_key_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.kb_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.kb_action_type), fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(
                        KeyActionType.KEY to stringResource(R.string.kb_action_key),
                        KeyActionType.TEXT to stringResource(R.string.kb_action_text),
                        KeyActionType.CHORD to stringResource(R.string.kb_action_chord),
                        KeyActionType.MODIFIER_LATCH to stringResource(R.string.kb_action_latch),
                    ).forEach { (t, name) ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(name) },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                when (type) {
                    KeyActionType.KEY -> OutlinedTextField(
                        value = keyCode,
                        onValueChange = { keyCode = it },
                        label = { Text("keyCode (Escape/Tab/ArrowUp…)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    KeyActionType.TEXT -> OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(stringResource(R.string.kb_send_text)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    KeyActionType.CHORD -> OutlinedTextField(
                        value = chord,
                        onValueChange = { chord = it },
                        label = { Text(stringResource(R.string.kb_chord_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    KeyActionType.MODIFIER_LATCH -> OutlinedTextField(
                        value = modifier,
                        onValueChange = { modifier = it },
                        label = { Text(stringResource(R.string.kb_mod_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (error != null) {
                    Text(error!!, color = LocalMtmuxTheme.current.colors.warning, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (label.isBlank()) {
                        error = errLabel
                        return@TextButton
                    }
                    val action = when (type) {
                        KeyActionType.KEY -> {
                            if (keyCode.isBlank()) {
                                error = errKeycode
                                return@TextButton
                            }
                            KeyAction(type = type, keyCode = keyCode.trim())
                        }
                        KeyActionType.TEXT -> {
                            if (text.isEmpty()) {
                                error = errText
                                return@TextButton
                            }
                            KeyAction(type = type, text = text)
                        }
                        KeyActionType.CHORD -> {
                            val keys = chord.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                            if (keys.isEmpty()) {
                                error = errChord
                                return@TextButton
                            }
                            KeyAction(type = type, keys = keys)
                        }
                        KeyActionType.MODIFIER_LATCH ->
                            KeyAction(type = type, modifier = modifier.trim().ifBlank { "ctrl" })
                    }
                    onConfirm(
                        KeyboardKey(
                            id = initial?.id ?: "k-" + UUID.randomUUID().toString().take(6),
                            label = label.trim(),
                            action = action,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun KeyboardImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.kb_import_json)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.kb_import_paste)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onImport(text) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.import_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun actionSummary(a: KeyAction): String = when (a.type) {
    KeyActionType.KEY -> "key:${a.keyCode}"
    KeyActionType.TEXT -> "text:${a.text}"
    KeyActionType.CHORD -> "chord:${a.keys?.joinToString("+")}"
    KeyActionType.MODIFIER_LATCH -> "latch:${a.modifier}"
}
