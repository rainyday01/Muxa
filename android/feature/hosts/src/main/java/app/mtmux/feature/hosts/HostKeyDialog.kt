package app.mtmux.feature.hosts

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.mtmux.core.ui.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import app.mtmux.ssh.HostKeyCheck

@Composable
fun HostKeyConfirmDialog(
    check: HostKeyCheck,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    val (title, body) = when (check) {
        is HostKeyCheck.Unknown ->
            stringResource(R.string.hk_unknown_title) to
                stringResource(
                    R.string.hk_unknown_body,
                    check.hostname,
                    check.port,
                    check.keyType,
                    check.fingerprint,
                )
        is HostKeyCheck.Mismatch ->
            stringResource(R.string.hk_changed_title) to
                stringResource(
                    R.string.hk_changed_body,
                    check.hostname,
                    check.port,
                    check.expectedFingerprint,
                    check.actualFingerprint,
                )
        HostKeyCheck.Ok -> return
    }
    val acceptLabel = when (check) {
        is HostKeyCheck.Mismatch -> stringResource(R.string.hk_replace)
        else -> stringResource(R.string.hk_trust)
    }
    AlertDialog(
        onDismissRequest = onReject,
        modifier = Modifier.testTag("host_key_dialog"),
        title = { Text(title) },
        text = {
            Text(body, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        },
        confirmButton = {
            TextButton(
                onClick = onAccept,
                modifier = Modifier
                    .testTag("host_key_accept")
                    .semantics { contentDescription = "host_key_accept" },
            ) { Text(acceptLabel) }
        },
        dismissButton = {
            TextButton(
                onClick = onReject,
                modifier = Modifier
                    .testTag("host_key_reject")
                    .semantics { contentDescription = "host_key_reject" },
            ) { Text(stringResource(R.string.hk_reject)) }
        },
    )
}
