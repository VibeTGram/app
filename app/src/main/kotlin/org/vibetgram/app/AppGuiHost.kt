package org.vibetgram.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.vibetgram.gui.api.GuiDependencies
import org.vibetgram.gui.api.GuiEntryPoint
import org.vibetgram.gui.compose.VibeTGramApp

sealed interface GuiHostState {
    data object Loading : GuiHostState
    data class Ready(val dependencies: GuiDependencies) : GuiHostState
    data class Error(val code: String, val message: String) : GuiHostState
}

@Composable
fun VibeTGramHost(
    state: GuiHostState,
    entryPoint: GuiEntryPoint,
    windowWidthDp: Float,
    windowHeightDp: Float,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    contentInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
) {
    MaterialTheme {
        Surface(modifier.fillMaxSize().windowInsetsPadding(contentInsets)) {
            when (state) {
                GuiHostState.Loading -> HostStatus("Starting VibeTGram", "Waiting for Core services", true)
                is GuiHostState.Error -> HostStatus(
                    "Telegram Core unavailable",
                    "${state.code}: ${state.message}",
                    false,
                )
                is GuiHostState.Ready -> VibeTGramApp(
                    entryPoint = entryPoint,
                    dependencies = state.dependencies,
                    windowWidthDp = windowWidthDp,
                    windowHeightDp = windowHeightDp,
                    reducedMotion = reducedMotion,
                )
            }
        }
    }
}

@Composable
private fun HostStatus(title: String, detail: String, loading: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) CircularProgressIndicator()
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(detail, style = MaterialTheme.typography.bodyMedium)
    }
}
