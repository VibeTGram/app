package org.vibetgram.core.tdlib

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.yield
import org.vibetgram.core.api.AccountHandle
import org.vibetgram.core.api.ChatRef
import org.vibetgram.core.api.MessageRef
import org.vibetgram.core.api.MessageDelta
import org.vibetgram.core.api.OutgoingContent
import org.vibetgram.core.api.TelegramError
import org.vibetgram.core.api.TelegramResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TdLibEngineTest {
    private val account = AccountHandle.issue()

    @Test
    fun `correlates concurrent requests even when responses arrive out of order`() = runTest {
        val client = RecordingTdClient()
        val engine = newEngine(client)
        engine.start()
        client.respondNext(TdResult.AuthorizationState(AuthorizationState.READY))

        val first = async(start = CoroutineStart.UNDISPATCHED) { engine.listChats(account) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { engine.listChats(account) }
        val requests = client.requests.filter { it.function is TdFunction.GetChats }
        assertEquals(2, requests.size)

        client.respond(requests[1].requestId, TdResult.Chats(listOf(TdChat(2, "second")), hasMore = false))
        client.respond(requests[0].requestId, TdResult.Chats(listOf(TdChat(1, "first")), hasMore = false))

        assertEquals(1L, assertIs<TelegramResult.Success<*>>(first.await()).value.let { (it as List<*>).first().let { chat -> (chat as org.vibetgram.core.api.ChatSummary).ref.value } })
        assertEquals(2L, assertIs<TelegramResult.Success<*>>(second.await()).value.let { (it as List<*>).first().let { chat -> (chat as org.vibetgram.core.api.ChatSummary).ref.value } })
        engine.close()
    }

    @Test
    fun `paginates chats and retries transient failures`() = runTest {
        val client = RecordingTdClient()
        val engine = newEngine(client, RetryPolicy(maxAttempts = 2, initialDelayMillis = 0))
        engine.start()
        client.respondSetup()

        val result = async(start = CoroutineStart.UNDISPATCHED) { engine.listChats(account) }
        val first = client.requests.filter { it.function is TdFunction.GetChats }.single()
        client.respond(first.requestId, TdResult.Error(TdError.NetworkUnavailable))
        yield()
        val retry = client.requests.filter { it.function is TdFunction.GetChats }.last()
        assertTrue(retry.requestId != first.requestId)
        client.respond(retry.requestId, TdResult.Chats(listOf(TdChat(1, "one")), hasMore = true))
        yield()
        val page = client.requests.filter { it.function is TdFunction.GetChats }.last()
        client.respond(page.requestId, TdResult.Chats(listOf(TdChat(2, "two")), hasMore = false))
        yield()

        val chats = assertIs<TelegramResult.Success<List<org.vibetgram.core.api.ChatSummary>>>(result.await()).value
        assertEquals(listOf(1L, 2L), chats.map { it.ref.value })
        engine.close()
    }

    @Test
    fun `resolves chat identifiers through getChat using signed Telegram chat ids`() = runTest {
        val client = RecordingTdClient()
        val engine = newEngine(client)
        engine.start()
        client.respondSetup()

        val result = async(start = CoroutineStart.UNDISPATCHED) { engine.listChats(account) }
        val page = client.requests.filter { it.function is TdFunction.GetChats }.single()
        client.respond(page.requestId, TdResult.ChatIds(listOf(-100123L), totalCount = 1))
        yield()
        val detail = client.requests.filter { it.function is TdFunction.GetChat }.single()
        assertEquals(-100123L, assertIs<TdFunction.GetChat>(detail.function).chatId)
        client.respond(detail.requestId, TdResult.Chat(TdChat(-100123L, "Core team", unreadCount = 7)))

        val chats = assertIs<TelegramResult.Success<List<org.vibetgram.core.api.ChatSummary>>>(result.await()).value
        assertEquals(-100123L, chats.single().ref.value)
        assertEquals(7, chats.single().unreadCount)
        engine.close()
    }

    @Test
    fun `accepts the real TDLib message response from sendMessage`() = runTest {
        val client = RecordingTdClient()
        val engine = newEngine(client)
        engine.start()
        client.respondSetup()

        val result = async(start = CoroutineStart.UNDISPATCHED) {
            engine.sendMessage(
                account,
                ChatRef(-100123L),
                OutgoingContent.Text("hello"),
                org.vibetgram.core.api.SendOptions(),
            )
        }
        val request = client.requests.filter { it.function is TdFunction.SendMessage }.single()
        client.respond(request.requestId, TdResult.Message(TdMessage(-100123L, 42L, "hello", 4L)))

        assertEquals(42L, assertIs<TelegramResult.Success<MessageRef>>(result.await()).value.value)
        engine.close()
    }

    @Test
    fun `history de-duplicates the TDLib boundary message and stops without new rows`() = runTest {
        val client = RecordingTdClient()
        val engine = newEngine(client)
        engine.start()
        client.respondSetup()

        val result = async(start = CoroutineStart.UNDISPATCHED) {
            engine.listMessages(account, ChatRef(-100123L))
        }
        val first = client.requests.filter { it.function is TdFunction.GetChatHistory }.last()
        client.respond(first.requestId, TdResult.ChatHistory(listOf(
            TdMessage(-100123L, 3L, "three", 3L),
            TdMessage(-100123L, 2L, "two", 2L),
        ), hasMore = true))
        runCurrent()
        val second = client.requests.filter { it.function is TdFunction.GetChatHistory }.last()
        client.respond(second.requestId, TdResult.ChatHistory(listOf(
            TdMessage(-100123L, 2L, "two", 2L),
            TdMessage(-100123L, 1L, "one", 1L),
        ), hasMore = true))
        runCurrent()
        val third = client.requests.filter { it.function is TdFunction.GetChatHistory }.last()
        client.respond(third.requestId, TdResult.ChatHistory(listOf(
            TdMessage(-100123L, 1L, "one", 1L),
        ), hasMore = true))
        runCurrent()

        assertTrue(result.isCompleted)
        val messages = assertIs<TelegramResult.Success<List<org.vibetgram.core.api.Message>>>(result.await()).value
        assertEquals(listOf(3L, 2L, 1L), messages.map { it.ref.value })
        engine.close()
    }

    @Test
    fun `orders incoming updates and exposes authorization state`() = runTest {
        val client = RecordingTdClient()
        val engine = newEngine(client)
        engine.start()
        client.respondSetup()
        val state = async(start = CoroutineStart.UNDISPATCHED) {
            engine.observeAuthorization().first { it == AuthorizationState.READY }
        }
        val updates = async(start = CoroutineStart.UNDISPATCHED) {
            engine.observeMessages(account, ChatRef(7)).take(2).toList()
        }

        client.emit(TdUpdate.AuthorizationStateChanged(AuthorizationState.READY))
        client.emit(TdUpdate.NewMessage(TdMessage(7, 2, "two", 2)))
        client.emit(TdUpdate.NewMessage(TdMessage(7, 1, "one", 1)))

        assertEquals(AuthorizationState.READY, state.await())
        assertEquals(listOf(2L, 1L), updates.await().map { (it as MessageDelta.Added).message.ref.value })
        engine.close()
    }

    @Test
    fun `sends the isolated encryption key in pinned tdlib parameters`() {
        val client = RecordingTdClient()
        val suppliedKey = byteArrayOf(1, 2, 3)
        val engine = TdLibEngine(
            account,
            QueueTdClientManager(listOf(client)),
            TdLibConfig("/db", 1, "hash", "device", encryptionKey = suppliedKey),
        )

        engine.start()
        assertEquals(1, client.requests.size)
        val setup = assertIs<TdFunction.SetTdlibParameters>(client.requests.single().function)
        suppliedKey[0] = 9
        assertTrue(setup.parameters.databaseEncryptionKey.all { it == 0.toByte() })
        client.requests.single().callback(TdResult.Ok)
        assertEquals(1, client.requests.size)
        engine.close()
    }

    @Test
    fun `process recovery replaces the client and replays setup`() = runTest {
        val old = RecordingTdClient()
        val replacement = RecordingTdClient()
        val manager = QueueTdClientManager(listOf(old, replacement))
        val engine = TdLibEngine(account, manager, TdLibConfig("/db", 1, "hash", "device"))
        engine.start()
        old.respondSetup()

        engine.recoverProcess()
        replacement.respondSetup()

        assertTrue(old.closed)
        assertEquals(2, manager.created)
        assertTrue(replacement.requests.any { it.function is TdFunction.SetTdlibParameters })
        engine.close()
    }

    @Test
    fun `authorization commands are typed and secrets are copied then cleared`() = runTest {
        val client = RecordingTdClient()
        val engine = newEngine(client)
        engine.start()
        client.respondSetup()

        val phone = async(start = CoroutineStart.UNDISPATCHED) { engine.setAuthenticationPhoneNumber("+15551234567") }
        val phoneRequest = client.requests.last()
        assertEquals("+15551234567", assertIs<TdFunction.SetAuthenticationPhoneNumber>(phoneRequest.function).phoneNumber)
        client.respond(phoneRequest.requestId, TdResult.Ok)
        assertIs<TelegramResult.Success<Unit>>(phone.await())

        val passwordInput = charArrayOf('s', 'e', 'c', 'r', 'e', 't')
        val password = async(start = CoroutineStart.UNDISPATCHED) { engine.checkAuthenticationPassword(passwordInput) }
        assertTrue(passwordInput.all { it == '\u0000' })
        val passwordRequest = client.requests.last()
        val typedPassword = assertIs<TdFunction.CheckAuthenticationPassword>(passwordRequest.function)
        assertEquals("secret", typedPassword.copyPassword().concatToString())
        client.respond(passwordRequest.requestId, TdResult.Ok)
        assertIs<TelegramResult.Success<Unit>>(password.await())
        assertTrue(typedPassword.copyPassword().all { it == '\u0000' })

        val codeInput = charArrayOf('1', '2', '3', '4', '5')
        val code = async(start = CoroutineStart.UNDISPATCHED) { engine.checkAuthenticationCode(codeInput) }
        assertTrue(codeInput.all { it == '\u0000' })
        val codeRequest = client.requests.last()
        assertIs<TdFunction.CheckAuthenticationCode>(codeRequest.function)
        client.respond(codeRequest.requestId, TdResult.Ok)
        assertIs<TelegramResult.Success<Unit>>(code.await())

        suspend fun completeAuthorizationRequest(
            operation: suspend () -> TelegramResult<Unit>,
            assertion: (TdFunction) -> Unit,
        ) {
            val result = async(start = CoroutineStart.UNDISPATCHED) { operation() }
            val request = client.requests.last()
            assertion(request.function)
            client.respond(request.requestId, TdResult.Ok)
            assertIs<TelegramResult.Success<Unit>>(result.await())
        }
        completeAuthorizationRequest(
            { engine.requestQrCodeAuthentication(listOf(7)) },
            { assertEquals(listOf(7L), assertIs<TdFunction.RequestQrCodeAuthentication>(it).otherUserIds) },
        )
        completeAuthorizationRequest(
            { engine.registerUser("Alice", "Smith", disableNotification = true) },
            { assertTrue(assertIs<TdFunction.RegisterUser>(it).disableNotification) },
        )
        completeAuthorizationRequest(
            engine::logOut,
            { assertIs<TdFunction.LogOut>(it) },
        )

        engine.close()
    }

    @Test
    fun `authorization details carry qr password and terms without exposing them as errors`() {
        val client = RecordingTdClient()
        val engine = newEngine(client)
        engine.start()
        client.respondSetup()

        client.emit(TdUpdate.AuthorizationStateChanged(
            state = AuthorizationState.WAITING_PASSWORD,
            passwordHint = "correct horse",
        ))
        assertEquals("correct horse", engine.observeAuthorizationDetails().value.passwordHint)
        assertNull(engine.observeAuthorizationDetails().value.qrCodeLink)

        client.emit(TdUpdate.AuthorizationStateChanged(
            state = AuthorizationState.WAITING_QR_CODE,
            qrCodeLink = "tg://login?token=opaque",
        ))
        assertEquals("tg://login?token=opaque", engine.observeAuthorizationDetails().value.qrCodeLink)
        assertNull(engine.observeAuthorizationDetails().value.passwordHint)

        val terms = AuthorizationTerms("Telegram terms", minimumUserAge = 16, showPopup = true)
        client.emit(TdUpdate.AuthorizationStateChanged(
            state = AuthorizationState.WAITING_REGISTRATION,
            terms = terms,
        ))
        assertEquals(terms, engine.observeAuthorizationDetails().value.terms)
        engine.close()
    }

    @Test
    fun `process recovery cancels requests owned by replaced client`() = runTest {
        val old = RecordingTdClient()
        val replacement = RecordingTdClient()
        val engine = TdLibEngine(
            account,
            QueueTdClientManager(listOf(old, replacement)),
            TdLibConfig("/db", 1, "hash", "device"),
        )
        engine.start()
        old.respondSetup()
        val pending = async(start = CoroutineStart.UNDISPATCHED) { engine.listChats(account) }

        engine.recoverProcess()

        assertFailsWith<kotlinx.coroutines.CancellationException> { pending.await() }
        assertTrue(old.closed)
        engine.close()
    }

    private fun newEngine(client: RecordingTdClient, retryPolicy: RetryPolicy = RetryPolicy()) =
        TdLibEngine(
            account = account,
            clientManager = QueueTdClientManager(listOf(client)),
            config = TdLibConfig("/db", 1, "hash", "device"),
            retryPolicy = retryPolicy,
        )
}

private class RecordingTdClient : TdClient {
    data class Request(val requestId: Long, val function: TdFunction, val callback: (TdResult) -> Unit)

    private var nextId = 1L
    val requests = mutableListOf<Request>()
    var closed = false
        private set
    private var updateHandler: ((TdUpdate) -> Unit)? = null

    override fun setUpdateHandler(handler: (TdUpdate) -> Unit) {
        updateHandler = handler
    }

    override fun send(function: TdFunction, callback: (TdResult) -> Unit): Long {
        val id = nextId++
        requests += Request(id, function, callback)
        return id
    }

    override fun close() {
        closed = true
    }

    fun respond(requestId: Long, result: TdResult) {
        requests.first { it.requestId == requestId }.callback(result)
    }

    fun respondNext(result: TdResult) = requests.first().callback(result)

    fun respondSetup() {
        var index = 0
        while (index < requests.size) {
            val request = requests[index]
            if (request.function is TdFunction.SetTdlibParameters) {
                request.callback(TdResult.Ok)
            }
            index++
        }
    }

    fun emit(update: TdUpdate) {
        updateHandler?.invoke(update)
    }
}

private class QueueTdClientManager(private val clients: List<TdClient>) : TdClientManager {
    var created = 0
        private set

    override fun createClient(): TdClient = clients[created++]
}
