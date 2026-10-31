package app.mtmux.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.mtmux.core.ui.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mtmux.core.model.Snippet
import app.mtmux.core.ui.theme.LocalMtmuxTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetsScreen(
    snippets: List<Snippet>,
    onBack: () -> Unit,
    onSave: (Snippet) -> Unit,
    onDelete: (String) -> Unit,
) {
    val theme = LocalMtmuxTheme.current
    var editing by remember { mutableStateOf<Snippet?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(theme.colors.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.snippets_title), fontWeight = FontWeight.Bold, color = theme.colors.onSurface) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = theme.colors.primary)
                }
            },
            actions = {
                IconButton(onClick = {
                    editing = null
                    showEditor = true
                }) {
                    Icon(Icons.Default.Add, null, tint = theme.colors.primary)
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.colors.background),
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(snippets, key = { it.id }) { s ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(s.name, fontWeight = FontWeight.SemiBold, color = theme.colors.onSurface)
                        Text(
                            s.body,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = theme.colors.onSurfaceMuted,
                            maxLines = 3,
                        )
                        Row {
                            TextButton(onClick = {
                                editing = s
                                showEditor = true
                            }) { Text(stringResource(R.string.edit)) }
                            TextButton(onClick = { onDelete(s.id) }) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        SnippetEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = {
                onSave(it)
                showEditor = false
            },
        )
    }
}

@Composable
private fun SnippetEditorDialog(
    initial: Snippet?,
    onDismiss: () -> Unit,
    onSave: (Snippet) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var body by remember { mutableStateOf(initial?.body.orEmpty()) }
    var runOnSelect by remember { mutableStateOf(initial?.runOnSelect == true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) stringResource(R.string.snippet_new) else stringResource(R.string.snippet_edit)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.snippet_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(stringResource(R.string.snippet_body)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    minLines = 3,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.snippet_run))
                    Switch(checked = runOnSelect, onCheckedChange = { runOnSelect = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank() && body.isNotBlank()) {
                    onSave(
                        Snippet(
                            id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.trim(),
                            body = body,
                            runOnSelect = runOnSelect,
                        ),
                    )
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
