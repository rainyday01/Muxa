package app.mtmux.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.mtmux.core.ui.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mtmux.core.ui.theme.LocalMtmuxTheme

const val MUXA_PRIVACY_POLICY_URL = "https://muxaprivacy.xcodeuk.com"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    appVersion: String,
    onBack: () -> Unit,
    onDeleteAllData: (() -> Unit)? = null,
) {
    val theme = LocalMtmuxTheme.current
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(theme.colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("screen_privacy"),
    ) {
        TopAppBar(
            title = {
                Text(stringResource(R.string.privacy_title), fontWeight = FontWeight.Normal, color = theme.colors.onSurface)
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("privacy_back")) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
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
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                "Muxa",
                color = theme.colors.onSurface,
                fontSize = 28.sp,
                fontWeight = FontWeight.Normal,
            )
            Text(
                appVersion,
                color = theme.colors.onSurfaceMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = theme.colors.outline)
            Spacer(Modifier.height(20.dp))

            PrivacySection(stringResource(R.string.privacy_s1_title), stringResource(R.string.privacy_s1_body))
            PrivacySection(stringResource(R.string.privacy_s2_title), stringResource(R.string.privacy_s2_body))
            PrivacySection(stringResource(R.string.privacy_s3_title), stringResource(R.string.privacy_s3_body))
            PrivacySection(stringResource(R.string.privacy_s4_title), stringResource(R.string.privacy_s4_body))
            PrivacySection(stringResource(R.string.privacy_s5_title), stringResource(R.string.privacy_s5_body))
            PrivacySection(stringResource(R.string.privacy_s6_title), stringResource(R.string.privacy_s6_body))

            Text(
                stringResource(R.string.privacy_policy_title),
                color = theme.colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            TextButton(
                onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MUXA_PRIVACY_POLICY_URL)))
                },
                modifier = Modifier.testTag("privacy_policy_link"),
            ) {
                Text(stringResource(R.string.privacy_policy_open))
            }
            Text(
                MUXA_PRIVACY_POLICY_URL,
                color = theme.colors.onSurfaceMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 20.dp),
            )

            if (onDeleteAllData != null) {
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.testTag("privacy_delete_all"),
                ) {
                    Text(stringResource(R.string.privacy_delete_all), color = theme.colors.warning)
                }
            }
        }
    }
    if (confirmDelete && onDeleteAllData != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.privacy_delete_all)) },
            text = { Text(stringResource(R.string.privacy_delete_all_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDeleteAllData()
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun PrivacySection(title: String, body: String) {
    val theme = LocalMtmuxTheme.current
    Text(
        title,
        color = theme.colors.onSurface,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp),
    )
    Text(
        body,
        color = theme.colors.onSurfaceMuted,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        modifier = Modifier.padding(bottom = 20.dp),
    )
}
