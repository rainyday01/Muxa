package app.mtmux.core.ui.feedback

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

val LocalSnackbarHost = staticCompositionLocalOf<SnackbarHostState> {
    error("SnackbarHostState not provided")
}

@Composable
fun rememberAppSnackbarHostState(): SnackbarHostState = remember { SnackbarHostState() }
