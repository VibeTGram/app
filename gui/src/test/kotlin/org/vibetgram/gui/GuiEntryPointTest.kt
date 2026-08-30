package org.vibetgram.gui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vibetgram.gui.api.DefaultGuiEntryPoint
import org.vibetgram.gui.api.GuiDependencies
import org.vibetgram.gui.api.GuiDescriptor
import org.vibetgram.gui.api.GuiEventHandler
import org.vibetgram.gui.api.GuiEntryPoint
import org.vibetgram.gui.api.GuiRegistry
import org.vibetgram.gui.api.GuiRenderContainer
import org.vibetgram.gui.contract.GuiRoute
import org.vibetgram.gui.domain.FakeSemanticServices
import org.vibetgram.gui.modui.ModUiNode
import org.vibetgram.gui.modui.ModUiSlot
import org.vibetgram.gui.modui.ModUiValidator
import org.vibetgram.gui.screens.RootScreenRenderState

@OptIn(ExperimentalCoroutinesApi::class)
class GuiEntryPointTest {

    @Test
    fun testDefaultGuiEntryPointMetadata() {
        val entryPoint = DefaultGuiEntryPoint()
        val desc = entryPoint.descriptor
        assertEquals("org.vibetgram.gui.default", desc.id)
        assertEquals("1.0.0", desc.version)
        assertTrue(desc.supportsExpressiveTheming)
        assertTrue(desc.supportsAdaptiveTwoPane)
    }

    @Test
    fun testGuiRegistryReplacement() {
        val customDescriptor = GuiDescriptor(
            id = "org.custom.gui",
            name = "Custom Fork GUI",
            version = "2.0.0"
        )
        val customEntryPoint = object : GuiEntryPoint {
            override val descriptor: GuiDescriptor = customDescriptor
            override fun createStateHolder(dependencies: GuiDependencies) =
                DefaultGuiEntryPoint().createStateHolder(dependencies)
            override fun render(
                container: GuiRenderContainer,
                stateHolder: org.vibetgram.gui.state.RootStateHolder,
                events: GuiEventHandler,
                slotNodes: Map<String, ModUiNode>
            ): RootScreenRenderState =
                DefaultGuiEntryPoint().render(container, stateHolder, events, slotNodes)
            override fun validateAndRenderSlot(slot: ModUiSlot, node: ModUiNode) =
                ModUiValidator.validateTree(node)
        }

        GuiRegistry.register(customEntryPoint)
        assertEquals(2, GuiRegistry.listAvailable().size)

        GuiRegistry.setActiveEntryPoint(customEntryPoint)
        assertEquals("org.custom.gui", GuiRegistry.getActiveEntryPoint().descriptor.id)

        GuiRegistry.resetToDefault()
        assertEquals("org.vibetgram.gui.default", GuiRegistry.getActiveEntryPoint().descriptor.id)
    }

    @Test
    fun testGuiRenderLifecycle() = runTest {
        val fakeServices = FakeSemanticServices()
        val testScope = TestScope(testScheduler)
        val deps = GuiDependencies(
            authService = fakeServices,
            accountManager = fakeServices,
            chatQuery = fakeServices,
            chatMutation = fakeServices,
            messageQuery = fakeServices,
            messageComposer = fakeServices,
            messageMutation = fakeServices,
            draftService = fakeServices,
            coroutineScope = testScope
        )

        val entryPoint = DefaultGuiEntryPoint()
        val stateHolder = entryPoint.createStateHolder(deps)
        testScope.testScheduler.advanceUntilIdle()
        stateHolder.updateAccessibility(reducedMotion = true)
        assertEquals(0L, stateHolder.uiState.value.theme.motion.durationShortMs)
        assertEquals(0L, stateHolder.uiState.value.theme.motion.durationBubblePopMs)
        stateHolder.navigateTo(GuiRoute.ChatList())
        assertTrue(stateHolder.uiState.value.navigationState.currentRoute is GuiRoute.Auth)

        var capturedState: RootScreenRenderState? = null
        val container = object : GuiRenderContainer {
            override fun setRootState(state: RootScreenRenderState) {
                capturedState = state
            }
            override fun renderSlotNode(slot: ModUiSlot, node: ModUiNode) {}
        }
        val eventHandler = object : GuiEventHandler {
            override fun onNavigate(route: GuiRoute) {}
            override fun onBack() {}
            override fun onModUiAction(actionId: String, payload: Map<String, Any>) {}
        }

        val renderState = entryPoint.render(container, stateHolder, eventHandler)
        assertNotNull(renderState)
        assertTrue(renderState.currentRoute is GuiRoute.Auth)
        assertNotNull(capturedState)
        assertEquals(renderState, capturedState)
    }
}
