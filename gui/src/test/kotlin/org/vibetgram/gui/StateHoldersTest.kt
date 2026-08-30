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
import org.vibetgram.gui.domain.ChatRef
import org.vibetgram.gui.domain.FakeSemanticServices
import org.vibetgram.gui.domain.MessageRef
import org.vibetgram.gui.state.AuthStateHolder
import org.vibetgram.gui.state.AuthUiState
import org.vibetgram.gui.state.ChatListStateHolder
import org.vibetgram.gui.state.ConversationStateHolder
import org.vibetgram.gui.state.TextComposerStateHolder

@OptIn(ExperimentalCoroutinesApi::class)
class StateHoldersTest {

    @Test
    fun testAuthFlowTransitions() = runTest {
        val fakeServices = FakeSemanticServices()
        val testScope = TestScope(testScheduler)
        val authHolder = AuthStateHolder(fakeServices, testScope)

        authHolder.onPhoneChanged("+1234567890")
        assertEquals("+1234567890", authHolder.uiState.value.phoneNumber)

        authHolder.submitPhone()
        advanceUntilIdle()

        assertEquals(AuthUiState.AuthStep.CODE_VERIFY, authHolder.uiState.value.authStep)

        authHolder.onCodeChanged("12345")
        advanceUntilIdle()

        assertEquals(AuthUiState.AuthStep.AUTHORIZED, authHolder.uiState.value.authStep)
        assertEquals("", authHolder.uiState.value.authCode)
        assertEquals("", authHolder.uiState.value.password2Fa)
    }

    @Test
    fun testChatListAndSearch() = runTest {
        val fakeServices = FakeSemanticServices()
        val testScope = TestScope(testScheduler)
        val chatListHolder = ChatListStateHolder(fakeServices, fakeServices, fakeServices, testScope)
        advanceUntilIdle()

        val initialChats = chatListHolder.uiState.value.chats
        assertTrue(initialChats.isNotEmpty())
        assertEquals(3, initialChats.size)

        chatListHolder.onSearchQueryChanged("Alice")
        advanceUntilIdle()

        assertTrue(chatListHolder.uiState.value.isSearchActive)
        assertEquals(1, chatListHolder.uiState.value.searchResults.size)
        assertEquals("Alice Smith", chatListHolder.uiState.value.searchResults.first().title)
    }

    @Test
    fun testConversationAndComposerFlow() = runTest {
        val fakeServices = FakeSemanticServices()
        val testScope = TestScope(testScheduler)
        val convHolder = ConversationStateHolder(fakeServices, fakeServices, fakeServices, testScope)
        val compHolder = TextComposerStateHolder(fakeServices, fakeServices, testScope)

        val account = fakeServices.observeActiveAccount()
        convHolder.setAccount(fakeServices.observeAccounts().let { org.vibetgram.gui.domain.AccountHandle("account_primary") })
        compHolder.setAccount(org.vibetgram.gui.domain.AccountHandle("account_primary"))

        val chatRef = ChatRef(101L)
        convHolder.openChat(chatRef)
        compHolder.bindChat(chatRef)
        advanceUntilIdle()

        assertEquals(3, convHolder.uiState.value.messages.size)

        compHolder.onTextChanged("New test message")
        assertTrue(compHolder.uiState.value.canSend)

        var sentMsgRef: MessageRef? = null
        compHolder.sendMessage(onSent = { sentMsgRef = it })
        advanceUntilIdle()

        assertNotNull(sentMsgRef)
        assertEquals("", compHolder.uiState.value.inputText)
        assertFalse(compHolder.uiState.value.canSend)
        assertEquals(4, convHolder.uiState.value.messages.size)
    }
}
