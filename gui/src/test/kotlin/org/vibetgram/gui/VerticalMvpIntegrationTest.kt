package org.vibetgram.gui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vibetgram.gui.accessibility.A11yValidator
import org.vibetgram.gui.accessibility.AccessibleInputNavigation
import org.vibetgram.gui.accessibility.NavigationAction
import org.vibetgram.gui.accessibility.NavigationKey
import org.vibetgram.gui.adapter.CoreTelegramServiceAdapter
import org.vibetgram.gui.adaptive.AdaptiveLayoutStrategy
import org.vibetgram.gui.adaptive.ContentLayoutType
import org.vibetgram.gui.adaptive.NavigationType
import org.vibetgram.gui.adaptive.WindowFormFactor
import org.vibetgram.gui.adaptive.WindowSizeClass
import org.vibetgram.gui.adaptive.WindowWidthSizeClass
import org.vibetgram.gui.api.DefaultGuiEntryPoint
import org.vibetgram.gui.api.GuiEventHandler
import org.vibetgram.gui.api.GuiRenderContainer
import org.vibetgram.gui.contract.GuiRoute
import org.vibetgram.gui.domain.AccountHandle
import org.vibetgram.gui.domain.AuthState
import org.vibetgram.gui.domain.ChatRef
import org.vibetgram.gui.domain.MessageRef
import org.vibetgram.gui.modui.ModUiNode
import org.vibetgram.gui.modui.ModUiSlot
import org.vibetgram.gui.screens.AuthScreenRenderer
import org.vibetgram.gui.screens.ChatListScreenRenderer
import org.vibetgram.gui.screens.ConversationScreenRenderer
import org.vibetgram.gui.screens.RootScreenRenderState
import org.vibetgram.gui.screens.TextComposerRenderer
import org.vibetgram.gui.state.AuthUiState
import org.vibetgram.gui.theme.ExpressiveColor

/**
 * End-to-end integration tests verifying the first vertical MVP slice (GUI-02):
 * Wiring between replaceable GUI state holders, Core semantic adapter,
 * authorization flow, chat list, conversation, text send/receive,
 * and preservation of accessibility semantics and adaptive layout behaviors.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VerticalMvpIntegrationTest {

    @Test
    fun testCompleteVerticalMvpSliceWiring() = runTest {
        val testScope = TestScope(testScheduler)
        val coreAdapter = CoreTelegramServiceAdapter(
            initialAccount = AccountHandle("account_test_01")
        )
        val deps = coreAdapter.toGuiDependencies(testScope)
        val entryPoint = DefaultGuiEntryPoint()
        val rootHolder = entryPoint.createStateHolder(deps)
        val authHolder = rootHolder.authStateHolder
        val chatListHolder = rootHolder.chatListStateHolder
        val convHolder = rootHolder.conversationStateHolder
        val composerHolder = rootHolder.composerStateHolder

        advanceUntilIdle()

        // 1. Initial State: Unauthenticated -> Auth Screen (Phone Entry)
        assertTrue(rootHolder.uiState.value.navigationState.currentRoute is GuiRoute.Auth)
        assertEquals(AuthUiState.AuthStep.PHONE_ENTRY, authHolder.uiState.value.authStep)

        // 2. Authorize fake/test account
        authHolder.onPhoneChanged("+1555019999")
        authHolder.submitPhone()
        advanceUntilIdle()

        assertEquals(AuthUiState.AuthStep.CODE_VERIFY, authHolder.uiState.value.authStep)
        assertEquals("+1555019999", authHolder.uiState.value.phoneNumber)

        authHolder.onCodeChanged("12345")
        advanceUntilIdle()

        assertEquals(AuthUiState.AuthStep.AUTHORIZED, authHolder.uiState.value.authStep)
        assertTrue(rootHolder.uiState.value.navigationState.currentRoute is GuiRoute.ChatList)

        // 3. Show Chat List
        val chats = chatListHolder.uiState.value.chats
        assertTrue(chats.isNotEmpty())
        assertEquals(3, chats.size)
        val targetChat = chats.first()
        assertEquals(ChatRef(101L), targetChat.ref)
        assertEquals("Alice Smith", targetChat.title)

        // 4. Open Conversation
        rootHolder.navigateTo(GuiRoute.Conversation(targetChat.ref))
        advanceUntilIdle()

        assertEquals(GuiRoute.Conversation(targetChat.ref), rootHolder.uiState.value.navigationState.currentRoute)
        assertEquals(targetChat.ref, convHolder.uiState.value.chatRef)
        assertEquals(3, convHolder.uiState.value.messages.size)
        assertEquals("Alice Smith", convHolder.uiState.value.chatInfo?.title)

        // 5. Compose and Send Text Message
        composerHolder.onTextChanged("Hello Alice from VibeTGram vertical slice!")
        assertTrue(composerHolder.uiState.value.canSend)
        assertEquals("Hello Alice from VibeTGram vertical slice!", composerHolder.uiState.value.inputText)

        var sentRef: MessageRef? = null
        composerHolder.sendMessage(onSent = { sentRef = it })
        advanceUntilIdle()

        assertNotNull(sentRef)
        assertEquals("", composerHolder.uiState.value.inputText)
        assertFalse(composerHolder.uiState.value.canSend)
        assertFalse(composerHolder.uiState.value.isSending)

        // Verify sent message is in conversation history
        assertEquals(4, convHolder.uiState.value.messages.size)
        val lastSent = convHolder.uiState.value.messages.last()
        assertEquals("Hello Alice from VibeTGram vertical slice!", lastSent.text)
        assertTrue(lastSent.isOutgoing)

        // Verify chat list last message snippet is updated
        val updatedChat = chatListHolder.uiState.value.chats.first { it.ref == targetChat.ref }
        assertEquals("Hello Alice from VibeTGram vertical slice!", updatedChat.lastMessageSnippet)

        // 6. Receive Text Message from Peer
        val incomingMsg = coreAdapter.receiveIncomingMessage(
            chatRef = targetChat.ref,
            senderName = "Alice Smith",
            text = "Awesome! The vertical MVP slice is functioning seamlessly."
        )
        advanceUntilIdle()

        assertEquals(5, convHolder.uiState.value.messages.size)
        val lastReceived = convHolder.uiState.value.messages.last()
        assertEquals(incomingMsg.text, lastReceived.text)
        assertEquals("Alice Smith", lastReceived.senderName)
        assertFalse(lastReceived.isOutgoing)

        // 7. Render State verification
        var capturedRender: RootScreenRenderState? = null
        val container = object : GuiRenderContainer {
            override fun setRootState(state: RootScreenRenderState) {
                capturedRender = state
            }
            override fun renderSlotNode(slot: ModUiSlot, node: ModUiNode) {}
        }
        val eventHandler = object : GuiEventHandler {
            override fun onNavigate(route: GuiRoute) { rootHolder.navigateTo(route) }
            override fun onBack() { rootHolder.popNavigation() }
            override fun onModUiAction(actionId: String, payload: Map<String, Any>) {}
        }

        val renderState = entryPoint.render(container, rootHolder, eventHandler)
        assertNotNull(renderState)
        assertEquals(GuiRoute.Conversation(targetChat.ref), renderState.currentRoute)
        assertNotNull(renderState.conversationState)
        assertNotNull(renderState.composerState)
        assertEquals(5, renderState.conversationState?.messageList?.size)
    }

    @Test
    fun testAccessibilitySemanticsPreserved() {
        val theme = org.vibetgram.gui.theme.ThemeResolver.resolve(isDark = false)
        val black = ExpressiveColor.fromHex("#000000")
        val white = ExpressiveColor.fromHex("#FFFFFF")

        // WCAG AA Contrast Compliance
        assertTrue(A11yValidator.isContrastCompliant(black, white))
        val contrastRatio = A11yValidator.calculateContrastRatio(black, white)
        assertTrue(contrastRatio >= 21.0)

        // Touch target sizing (minimum 48dp)
        assertTrue(A11yValidator.validateTouchTarget(48f, 48f))
        assertTrue(A11yValidator.validateTouchTarget(56f, 48f))
        assertFalse(A11yValidator.validateTouchTarget(40f, 48f))

        // D-Pad / Keyboard navigation mappings
        assertEquals(NavigationAction.MOVE_PREVIOUS, AccessibleInputNavigation.actionFor(NavigationKey.UP))
        assertEquals(NavigationAction.MOVE_NEXT, AccessibleInputNavigation.actionFor(NavigationKey.DOWN))
        assertEquals(NavigationAction.ACTIVATE, AccessibleInputNavigation.actionFor(NavigationKey.ENTER))
        assertEquals(NavigationAction.BACK, AccessibleInputNavigation.actionFor(NavigationKey.BACK))

        // Screen reader content descriptions in Renderers
        val authRender = AuthScreenRenderer.prepareRenderState(
            state = AuthUiState(authStep = AuthUiState.AuthStep.PHONE_ENTRY),
            theme = theme
        )
        assertTrue(authRender.accessibilityDescription.contains("Authorization screen"))

        val chatListRender = ChatListScreenRenderer.prepareRenderState(
            state = org.vibetgram.gui.state.ChatListUiState(
                chats = CoreTelegramServiceAdapter.defaultChats()
            ),
            theme = theme
        )
        assertEquals(3, chatListRender.accessibilityDescriptions.size)
        assertTrue(chatListRender.accessibilityDescriptions.first().contains("Alice Smith"))
        assertTrue(chatListRender.accessibilityDescriptions.first().contains("unread"))

        val composerRender = TextComposerRenderer.prepareRenderState(
            state = org.vibetgram.gui.state.TextComposerUiState(canSend = true),
            theme = theme
        )
        assertEquals("Send message", composerRender.sendButtonContentDescription)
    }

    @Test
    fun testAdaptiveLayoutBehaviorPreserved() {
        // Compact (Phone) -> Bottom Nav + Single Pane
        val wscCompact = WindowSizeClass.compute(390f, 844f)
        val configCompact = AdaptiveLayoutStrategy.determineConfig(wscCompact)
        assertEquals(NavigationType.BOTTOM_NAVIGATION_BAR, configCompact.navigationType)
        assertEquals(ContentLayoutType.SINGLE_PANE, configCompact.contentLayoutType)

        // Medium (Tablet) -> Navigation Rail + Two-Pane Master Detail
        val wscMedium = WindowSizeClass.compute(768f, 1024f)
        val configMedium = AdaptiveLayoutStrategy.determineConfig(wscMedium)
        assertEquals(NavigationType.NAVIGATION_RAIL, configMedium.navigationType)
        assertEquals(ContentLayoutType.TWO_PANE_MASTER_DETAIL, configMedium.contentLayoutType)

        // Expanded (Desktop / Landscape) -> Permanent Navigation Drawer + Two-Pane Master Detail
        val wscExpanded = WindowSizeClass.compute(1200f, 800f)
        val configExpanded = AdaptiveLayoutStrategy.determineConfig(wscExpanded)
        assertEquals(NavigationType.PERMANENT_NAVIGATION_DRAWER, configExpanded.navigationType)
        assertEquals(ContentLayoutType.TWO_PANE_MASTER_DETAIL, configExpanded.contentLayoutType)

        // Form factors
        assertEquals(WindowFormFactor.FOLDABLE, WindowSizeClass.compute(720f, 800f, isFoldable = true).formFactor)
        assertEquals(WindowFormFactor.CHROMEOS, WindowSizeClass.compute(1200f, 800f, isChromeOs = true).formFactor)
    }
}
