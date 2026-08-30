package org.vibetgram.app

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vibetgram.gui.api.GuiDependencies
import org.vibetgram.gui.domain.FakeSemanticServices

class AppCompositionRootTest {
    @After
    fun tearDown() {
        AppCompositionRoot.resetForTests()
    }

    @Test
    fun missingCoreFailsClosedWithTypedBlocker() = runBlocking {
        val result = AppCompositionRoot.load(this)

        assertTrue(result is GuiDependenciesLoadResult.Unavailable)
        assertEquals(
            "CORE_GUI_DEPENDENCIES_UNAVAILABLE",
            (result as GuiDependenciesLoadResult.Unavailable).code,
        )
    }

    @Test
    fun injectedTypedCoreBundleEntersReadyState() = runBlocking {
        val services = FakeSemanticServices()
        val dependencies = GuiDependencies(
            authService = services,
            accountManager = services,
            chatQuery = services,
            chatMutation = services,
            messageQuery = services,
            messageComposer = services,
            messageMutation = services,
            draftService = services,
            coroutineScope = this,
        )
        AppCompositionRoot.install { GuiDependenciesLoadResult.Ready(dependencies) }

        val result = AppCompositionRoot.load(this)

        assertTrue(result is GuiDependenciesLoadResult.Ready)
        assertSame(dependencies, (result as GuiDependenciesLoadResult.Ready).dependencies)
    }

    @Test
    fun activeGuiComesFromReplaceableRegistry() {
        assertEquals("org.vibetgram.gui.default", AppCompositionRoot.activeGui().descriptor.id)
    }
}
