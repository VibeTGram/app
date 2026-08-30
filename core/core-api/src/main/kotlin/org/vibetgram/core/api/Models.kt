package org.vibetgram.core.api

import java.time.Instant
import java.util.Collections

/** Immutable row used by ordered chat snapshots. */
data class ChatSummary(
    val ref: ChatRef,
    val title: String,
    val unreadCount: Int,
) {
    init {
        require(title.isNotBlank()) { "chat title must not be blank" }
        require(unreadCount >= 0) { "unread count must not be negative" }
    }
}

/** Immutable semantic message model. */
data class Message(
    val ref: MessageRef,
    val text: String,
    val sentAt: Instant,
)

/** Snapshot with a monotonically increasing sequence number. */
class MessageSnapshot(
    val sequence: Long,
    messages: List<Message>,
) {
    val messages: List<Message> = Collections.unmodifiableList(messages.toList())

    init {
        require(sequence >= 0) { "snapshot sequence must not be negative" }
    }

    override fun equals(other: Any?): Boolean =
        other is MessageSnapshot && sequence == other.sequence && messages == other.messages

    override fun hashCode(): Int = 31 * sequence.hashCode() + messages.hashCode()

    override fun toString(): String = "MessageSnapshot(sequence=$sequence, messages=$messages)"

    companion object {
        fun empty(): MessageSnapshot = MessageSnapshot(sequence = 0, messages = emptyList())
    }
}

/** Snapshot with a monotonically increasing sequence number for chat lists. */
class ChatSnapshot(
    val sequence: Long,
    chats: List<ChatSummary>,
) {
    val chats: List<ChatSummary> = Collections.unmodifiableList(chats.toList())

    init {
        require(sequence >= 0) { "snapshot sequence must not be negative" }
    }

    override fun equals(other: Any?): Boolean =
        other is ChatSnapshot && sequence == other.sequence && chats == other.chats

    override fun hashCode(): Int = 31 * sequence.hashCode() + chats.hashCode()

    override fun toString(): String = "ChatSnapshot(sequence=$sequence, chats=$chats)"

    companion object {
        fun empty(): ChatSnapshot = ChatSnapshot(sequence = 0, chats = emptyList())
    }
}

/** Typed content family; adding content is an explicit API change. */
sealed interface OutgoingContent {
    data class Text(val value: String) : OutgoingContent {
        init {
            require(value.isNotBlank()) { "message text must not be blank" }
        }
    }
}

data class SendOptions(
    val replyTo: MessageRef? = null,
    val disableNotification: Boolean = false,
)

/** Immutable account-scoped semantic message events. */
sealed interface MessageDelta {
    val chat: ChatRef

    data class Added(val message: Message) : MessageDelta {
        override val chat: ChatRef = message.ref.chat
    }

    data class Edited(val message: Message) : MessageDelta {
        override val chat: ChatRef = message.ref.chat
    }

    data class Deleted(
        override val chat: ChatRef,
        val message: MessageRef,
    ) : MessageDelta {
        init {
            require(message.chat == chat) { "deleted message must belong to the event chat" }
        }
    }
}
