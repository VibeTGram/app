package org.vibetgram.core.api

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.vibetgram.core.api.fake.FakeTelegramAdapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TelegramServiceContractTest {
    private val account = AccountHandle.issue()

    @Test
    fun `fake adapter preserves insertion order in immutable chat results`() = runTest {
        val service = FakeTelegramAdapter()
        service.addChat(account, ChatRef(42), "VibeTGram")
        service.addChat(account, ChatRef(43), "Core")

        val result = assertIs<TelegramResult.Success<List<ChatSummary>>>(service.listChats(account))
        assertEquals(
            listOf(ChatSummary(ChatRef(42), "VibeTGram", 0), ChatSummary(ChatRef(43), "Core", 0)),
            result.value,
        )
    }

    @Test
    fun `send emits a semantic delta and query returns the same immutable message`() = runTest {
        val service = FakeTelegramAdapter()
        service.addChat(account, ChatRef(42), "VibeTGram")
        val observed = async(start = CoroutineStart.UNDISPATCHED) {
            service.observeMessages(account, ChatRef(42)).first()
        }

        val sent = service.sendMessage(account, ChatRef(42), OutgoingContent.Text("Hello"))
        val ref = assertIs<TelegramResult.Success<MessageRef>>(sent).value
        val delta = assertIs<MessageDelta.Added>(observed.await())
        val fetched = assertIs<TelegramResult.Success<Message>>(service.getMessage(account, ref))

        assertEquals(ref, delta.message.ref)
        assertEquals(delta.message, fetched.value)
    }

    @Test
    fun `message snapshots are ordered and isolated from adapter mutations`() = runTest {
        val service = FakeTelegramAdapter()
        service.addChat(account, ChatRef(42), "VibeTGram")
        val snapshots = async(start = CoroutineStart.UNDISPATCHED) {
            service.observeMessageSnapshots(account, ChatRef(42)).take(3).toList()
        }

        service.sendMessage(account, ChatRef(42), OutgoingContent.Text("one"))
        service.sendMessage(account, ChatRef(42), OutgoingContent.Text("two"))

        val result = snapshots.await()
        assertEquals(listOf(0L, 1L, 2L), result.map(MessageSnapshot::sequence))
        assertEquals(listOf("one", "two"), result.last().messages.map(Message::text))
    }

    @Test
    fun `unknown resources use stable typed errors`() = runTest {
        val result = FakeTelegramAdapter().sendMessage(
            account,
            ChatRef(404),
            OutgoingContent.Text("Hello"),
        )

        assertEquals(TelegramError.NotFound, assertIs<TelegramResult.Error>(result).error)
    }
}
