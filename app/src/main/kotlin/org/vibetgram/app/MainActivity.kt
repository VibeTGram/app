package org.vibetgram.app

import android.animation.ValueAnimator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    private val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var hostState by mutableStateOf<GuiHostState>(GuiHostState.Loading)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val configuration = LocalConfiguration.current
            VibeTGramHost(
                state = hostState,
                entryPoint = AppCompositionRoot.activeGui(),
                windowWidthDp = configuration.screenWidthDp.toFloat(),
                windowHeightDp = configuration.screenHeightDp.toFloat(),
                reducedMotion = !ValueAnimator.areAnimatorsEnabled(),
                contentInsets = WindowInsets.safeDrawing,
            )
        }

        hostScope.launch {
            hostState = when (val result = AppCompositionRoot.load(hostScope)) {
                is GuiDependenciesLoadResult.Ready -> GuiHostState.Ready(result.dependencies)
                is GuiDependenciesLoadResult.Unavailable -> GuiHostState.Error(result.code, result.message)
            }
        }
    }

    override fun onDestroy() {
        hostScope.cancel()
        super.onDestroy()
    }
}
