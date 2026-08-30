package org.vibetgram.core.api.fake

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import org.vibetgram.core.api.AccountHandle
import org.vibetgram.core.api.ChatRef
import org.vibetgram.core.api.ChatSummary
import org.vibetgram.core.api.Message
import org.vibetgram.core.api.MessageComposer
import org.vibetgram.core.api.MessageDelta
import org.vibetgram.core.api.MessageQuery
import org.vibetgram.core.api.MessageRef
import org.vibetgram.core.api.MessageSnapshot
import org.vibetgram.core.api.OutgoingContent
import org.vibetgram.core.api.SendOptions
import org.vibetgram.core.api.TelegramError
import org.vibetgram.core.api.TelegramResult
import org.vibetgram.core.api.TelegramService

internal class FakeTelegramAdapter : TelegramService {
    private val chats = linkedMapOf<AccountHandle, LinkedHashMap<ChatRef, ChatSummary>>()
    private val messages = linkedMapOf<Pair<AccountHandle, MessageRef>, Message>()
    private val snapshots = linkedMapOf<Pair<AccountHandle, ChatRef>, MutableSharedFlow<MessageSnapshot>>()
    private val updates = MutableSharedFlow<AccountMessageDelta>(extraBufferCapacity = 16)
    private var nextMessageId = 1L

    fun addChat(account: AccountHandle, ref: ChatRef, title: String) {
        chats.getOrPut(account) { linkedMapOf() }[ref] = ChatSummary(ref, title, 0)
        snapshots.getOrPut(account to ref) {
            MutableSharedFlow(replay = 1, extraBufferCapacity = 16)
        }.tryEmit(MessageSnapshot.empty())
    }

    override suspend fun listChats(account: AccountHandle): TelegramResult<List<ChatSummary>> =
        TelegramResult.Success(chats[account]?.values?.toList() ?: emptyList())

    override suspend fun getMessage(
        account: AccountHandle,
        message: MessageRef,
    ): TelegramResult<Message> = messages[account to message]
        ?.let { TelegramResult.Success(it) }
        ?: TelegramResult.Error(TelegramError.NotFound)

    override suspend fun listMessages(
        account: AccountHandle,
        chat: ChatRef,
    ): TelegramResult<List<Message>> {
        if (chat !in (chats[account] ?: emptyMap())) {
            return TelegramResult.Error(TelegramError.NotFound)
        }
        return TelegramResult.Success(
            messages.asSequence()
                .filter { (key, _) -> key.first == account && key.second.chat == chat }
                .map { it.value }
                .toList(),
        )
    }

    override fun observeMessages(account: AccountHandle, chat: ChatRef): Flow<MessageDelta> =
        updates.filter { it.account == account && it.delta.chat == chat }.map { it.delta }

    override fun observeMessageSnapshots(account: AccountHandle, chat: ChatRef): Flow<MessageSnapshot> =
        snapshots[account to chat] ?: MutableSharedFlow<MessageSnapshot>(replay = 1).also {
            it.tryEmit(MessageSnapshot.empty())
        }

    override suspend fun sendMessage(
        account: AccountHandle,
        chat: ChatRef,
        content: OutgoingContent,
        options: SendOptions,
    ): TelegramResult<MessageRef> {
        if (chat !in (chats[account] ?: emptyMap())) {
            return TelegramResult.Error(TelegramError.NotFound)
        }
        val text = (content as? OutgoingContent.Text)?.value
            ?: return TelegramResult.Error(TelegramError.UpstreamUnsupported)
        val ref = MessageRef(chat, nextMessageId++)
        val message = Message(ref, text, Instant.EPOCH)
        messages[account to ref] = message
        val snapshot = snapshots.getValue(account to chat)
        val previous = snapshot.replayCache.lastOrNull() ?: MessageSnapshot.empty()
        snapshot.emit(MessageSnapshot(previous.sequence + 1, previous.messages + message))
        updates.emit(AccountMessageDelta(account, MessageDelta.Added(message)))
        return TelegramResult.Success(ref)
    }

    private data class AccountMessageDelta(val account: AccountHandle, val delta: MessageDelta)
}
