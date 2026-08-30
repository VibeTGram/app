package org.vibetgram.core.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Metadata consumed by core-policy before an adapter performs an operation. */
data class OperationDescriptor(val stableId: String) {
    init {
        require(stableId.isNotBlank()) { "operation ID must not be blank" }
    }
}

/** Stable semantic operation identifiers; policy definitions live outside core-api. */
object SemanticOperations {
    val ListChats = OperationDescriptor("semantic.chat.list.v1")
    val ObserveChats = OperationDescriptor("semantic.chat.observe.v1")
    val GetMessage = OperationDescriptor("semantic.message.get.v1")
    val ListMessages = OperationDescriptor("semantic.message.list.v1")
    val ObserveMessages = OperationDescriptor("semantic.message.observe.v1")
    val SendMessage = OperationDescriptor("semantic.message.send.v1")
    val ObserveFile = OperationDescriptor("semantic.file.observe.v1")
    val StartDownload = OperationDescriptor("semantic.file.download.start.v1")
    val PauseDownload = OperationDescriptor("semantic.file.download.pause.v1")
    val CancelDownload = OperationDescriptor("semantic.file.download.cancel.v1")
    val ImportMedia = OperationDescriptor("semantic.media.import.v1")
    val ExportMedia = OperationDescriptor("semantic.media.export.v1")
}

/** Cancellable chat reads and ordered immutable chat snapshots. */
interface ChatQuery {
    suspend fun listChats(account: AccountHandle): TelegramResult<List<ChatSummary>>

    fun observeChats(account: AccountHandle): Flow<ChatSnapshot> = emptyFlow()
}

/** Cancellable message reads and ordered immutable message streams. */
interface MessageQuery {
    suspend fun getMessage(account: AccountHandle, message: MessageRef): TelegramResult<Message>

    suspend fun listMessages(account: AccountHandle, chat: ChatRef): TelegramResult<List<Message>>

    /** Event stream; events are immutable and delivered in adapter order. */
    fun observeMessages(account: AccountHandle, chat: ChatRef): Flow<MessageDelta>

    /** State stream; each value is a complete immutable ordered snapshot. */
    fun observeMessageSnapshots(account: AccountHandle, chat: ChatRef): Flow<MessageSnapshot> = emptyFlow()
}

/** Cancellable semantic message commands. */
interface MessageComposer {
    suspend fun sendMessage(
        account: AccountHandle,
        chat: ChatRef,
        content: OutgoingContent,
        options: SendOptions = SendOptions(),
    ): TelegramResult<MessageRef>
}

/** Minimal semantic Telegram seam for application composition and adapter tests. */
interface TelegramService : ChatQuery, MessageQuery, MessageComposer
