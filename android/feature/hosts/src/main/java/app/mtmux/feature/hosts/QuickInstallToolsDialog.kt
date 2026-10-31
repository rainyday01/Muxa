package app.mtmux.feature.hosts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.mtmux.core.ui.R
import app.mtmux.core.ui.theme.LocalMtmuxTheme

@Composable
fun QuickInstallToolsDialog(onDismiss: () -> Unit) {
    val theme = LocalMtmuxTheme.current
    val ctx = LocalContext.current
    var copiedId by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(theme.cardRadiusDp.dp),
            color = theme.colors.surface,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .testTag("host_tools_dialog"),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                Text(
                    stringResource(R.string.host_quick_tools),
                    color = theme.colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                )
                Text(
                    stringResource(R.string.host_quick_tools_body),
                    color = theme.colors.onSurfaceMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                )
                AgentInstallCommands.all.forEach { tool ->
                    Text(
                        ToolLabel(tool.id),
                        color = theme.colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                    )
                    Text(
                        tool.command,
                        fontFamily = FontFamily.Monospace,
                        color = theme.colors.terminalForeground,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.colors.terminalBackground)
                            .padding(10.dp)
                            .testTag("host_tools_cmd_${tool.id}"),
                    )
                    TextButton(
                        onClick = {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText(tool.id, tool.command))
                            copiedId = tool.id
                        },
                        modifier = Modifier.testTag("host_tools_copy_${tool.id}"),
                    ) {
                        Text(
                            if (copiedId == tool.id) {
                                stringResource(R.string.host_quick_tools_copied)
                            } else {
                                stringResource(R.string.host_quick_tools_copy)
                            },
                        )
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("host_tools_close"),
                ) { Text(stringResource(R.string.host_quick_tools_close)) }
            }
        }
    }
}

@Composable
private fun ToolLabel(id: String): String = when (id) {
    "codex" -> stringResource(R.string.tool_codex)
    "claude" -> stringResource(R.string.tool_claude)
    "agy" -> stringResource(R.string.tool_agy)
    "grok" -> stringResource(R.string.tool_grok)
    else -> id
}
