package org.vibetgram.app

import kotlinx.coroutines.CoroutineScope
import org.vibetgram.gui.api.GuiDependencies
import org.vibetgram.gui.api.GuiEntryPoint
import org.vibetgram.gui.api.GuiRegistry

sealed interface GuiDependenciesLoadResult {
    data class Ready(val dependencies: GuiDependencies) : GuiDependenciesLoadResult
    data class Unavailable(val code: String, val message: String) : GuiDependenciesLoadResult
}

fun interface GuiDependenciesProvider {
    suspend fun load(scope: CoroutineScope): GuiDependenciesLoadResult
}

/** Android composition seam. The default never substitutes demo data for Core. */
object AppCompositionRoot {
    private val unavailableProvider = GuiDependenciesProvider {
        GuiDependenciesLoadResult.Unavailable(
            code = "CORE_GUI_DEPENDENCIES_UNAVAILABLE",
            message = "Core has not supplied a native TDLib-backed GUI dependency bundle.",
        )
    }

    @Volatile
    private var provider: GuiDependenciesProvider = unavailableProvider

    fun install(provider: GuiDependenciesProvider) {
        this.provider = provider
    }

    fun activeGui(): GuiEntryPoint = GuiRegistry.getActiveEntryPoint()

    suspend fun load(scope: CoroutineScope): GuiDependenciesLoadResult = provider.load(scope)

    internal fun resetForTests() {
        provider = unavailableProvider
        GuiRegistry.resetToDefault()
    }
}
