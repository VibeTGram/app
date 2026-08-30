package org.vibetgram.app

import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vibetgram.core.api.AccountHandle as CoreAccountHandle
import org.vibetgram.core.api.ChatRef as CoreChatRef
import org.vibetgram.core.api.ChatSummary
import org.vibetgram.core.api.Message as CoreMessage
import org.vibetgram.core.api.MessageDelta
import org.vibetgram.core.api.MessageRef as CoreMessageRef
import org.vibetgram.core.api.OutgoingContent as CoreOutgoingContent
import org.vibetgram.core.api.SendOptions as CoreSendOptions
import org.vibetgram.core.api.TelegramResult as CoreResult
import org.vibetgram.core.tdlib.AuthorizationDetails
import org.vibetgram.core.tdlib.AuthorizationState
import org.vibetgram.core.tdlib.TelegramEngine
import org.vibetgram.gui.domain.AuthState
import org.vibetgram.gui.domain.OutgoingContent
import org.vibetgram.gui.domain.TelegramResult

class CoreGuiDependenciesAdapterTest {
    @Test
    fun `real core services drive auth chats history and text send`() = runTest {
        val coreAccount = CoreAccountHandle.issue()
        val engine = FakeEngine(coreAccount)
        var logoutCalled = false
        val dependencies = CoreGuiDependenciesAdapter(
            coreAccount,
            engine,
            backgroundScope,
            logoutAccount = {
                logoutCalled = true
                CoreResult.Success(Unit)
            },
        ).dependencies

        assertEquals(AuthState.WaitPhoneNumber, dependencies.authService.observeAuthState().first())
        val guiAccount = dependencies.accountManager.observeActiveAccount().first()!!

        assertTrue(
            dependencies.authService.setPhoneNumber("+7 (999) 123-45-67") is TelegramResult.Success,
        )
        assertEquals("+79991234567", engine.lastPhoneNumber)

        val chats = dependencies.chatQuery.observeChats(guiAccount).first()
        assertEquals(-100123L, chats.single().ref.id)
        assertEquals("Core team", chats.single().title)

        val history = dependencies.messageQuery.observeMessages(guiAccount, chats.single().ref)
            .first { it.size == 2 }
        assertEquals(listOf(9L, 7L), history.map { it.ref.id })

        val sent = dependencies.messageComposer.sendMessage(
            guiAccount,
            chats.single().ref,
            OutgoingContent.Text("real send"),
        )
        assertTrue(sent is TelegramResult.Success)
        assertEquals("real send", engine.lastSentText)

        val qr = async { dependencies.authService.requestQrCode() }
        yield()
        engine.details.value = AuthorizationDetails(
            AuthorizationState.WAITING_QR_CODE,
            qrCodeLink = "tg://login?token=opaque",
        )
        assertEquals("tg://login?token=opaque", (qr.await() as TelegramResult.Success).value)

        assertTrue(dependencies.authService.logOut() is TelegramResult.Success)
        assertTrue(logoutCalled)
    }
}

private class FakeEngine(
    private val account: CoreAccountHandle,
) : TelegramEngine {
    private val authorization = MutableStateFlow(AuthorizationState.WAITING_PHONE_NUMBER)
    val details = MutableStateFlow(AuthorizationDetails(AuthorizationState.WAITING_PHONE_NUMBER))
    private val updates = MutableSharedFlow<MessageDelta>()
    var lastSentText: String? = null
    var lastPhoneNumber: String? = null

    override fun start() = Unit
    override fun recoverProcess() = Unit
    override fun close() = Unit
    override fun observeAuthorization() = authorization
    override fun observeAuthorizationDetails() = details

    override suspend fun setAuthenticationPhoneNumber(phoneNumber: String): CoreResult<Unit> {
        lastPhoneNumber = phoneNumber
        return CoreResult.Success(Unit)
    }

    override suspend fun checkAuthenticationCode(code: CharArray): CoreResult<Unit> = CoreResult.Success(Unit)
    override suspend fun checkAuthenticationPassword(password: CharArray): CoreResult<Unit> = CoreResult.Success(Unit)
    override suspend fun requestQrCodeAuthentication(otherUserIds: List<Long>): CoreResult<Unit> = CoreResult.Success(Unit)
    override suspend fun registerUser(firstName: String, lastName: String, disableNotification: Boolean): CoreResult<Unit> =
        CoreResult.Success(Unit)

    override suspend fun logOut(): CoreResult<Unit> = CoreResult.Success(Unit)

    override suspend fun listChats(account: CoreAccountHandle): CoreResult<List<ChatSummary>> =
        CoreResult.Success(listOf(ChatSummary(CoreChatRef(-100123L), "Core team", unreadCount = 2)))

    override suspend fun getMessage(account: CoreAccountHandle, message: CoreMessageRef): CoreResult<CoreMessage> =
        CoreResult.Success(CoreMessage(message, "hello from TDLib", Instant.ofEpochSecond(1)))

    override suspend fun listMessages(account: CoreAccountHandle, chat: CoreChatRef): CoreResult<List<CoreMessage>> =
        CoreResult.Success(listOf(CoreMessage(CoreMessageRef(chat, 7L), "hello from TDLib", Instant.ofEpochSecond(1)))).also {
            updates.emit(MessageDelta.Added(CoreMessage(CoreMessageRef(chat, 9L), "live", Instant.ofEpochSecond(2))))
        }

    override fun observeMessages(account: CoreAccountHandle, chat: CoreChatRef): Flow<MessageDelta> = updates

    override suspend fun sendMessage(
        account: CoreAccountHandle,
        chat: CoreChatRef,
        content: CoreOutgoingContent,
        options: CoreSendOptions,
    ): CoreResult<CoreMessageRef> {
        lastSentText = (content as CoreOutgoingContent.Text).value
        return CoreResult.Success(CoreMessageRef(chat, 8L))
    }
}
