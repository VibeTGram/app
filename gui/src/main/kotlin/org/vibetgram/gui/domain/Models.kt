package org.vibetgram.gui.domain

/**
 * Immutable domain models for VibeTGram semantic layer.
 * Normative reference: docs/api/two-level-api.md
 */

@JvmInline
value class AccountHandle(val id: String) {
    init {
        require(id.isNotBlank()) { "AccountHandle ID cannot be blank" }
    }
    override fun toString(): String = "AccountHandle(***)"
}

@JvmInline
value class ChatRef(val id: Long)

@JvmInline
value class MessageRef(val id: Long)

@JvmInline
value class UserRef(val id: Long)

data class FolderRef(
    val id: Int,
    val title: String,
    val isIncludedByDefault: Boolean = false,
    val unreadCount: Int = 0
)

data class TextEntity(
    val offset: Int,
    val length: Int,
    val type: EntityType
) {
    enum class EntityType {
        BOLD, ITALIC, CODE, PRE, SPOILER, STRIKETHROUGH, UNDERLINE, URL, MENTION
    }
}

sealed interface OutgoingContent {
    data class Text(
        val text: String,
        val entities: List<TextEntity> = emptyList()
    ) : OutgoingContent

    data class Photo(
        val fileUri: String,
        val caption: String? = null
    ) : OutgoingContent

    data class Document(
        val fileUri: String,
        val fileName: String,
        val mimeType: String
    ) : OutgoingContent
}

data class SendOptions(
    val silent: Boolean = false,
    val scheduleDate: Long? = null,
    val replyToMessageRef: MessageRef? = null
)

data class Draft(
    val text: String,
    val replyToMessageRef: MessageRef? = null,
    val timestampMs: Long = 0L
)

enum class MessageDeliveryStatus {
    PENDING,
    SENT,
    READ,
    FAILED
}

data class MessageItem(
    val ref: MessageRef,
    val chatRef: ChatRef,
    val senderRef: UserRef,
    val senderName: String,
    val text: String,
    val timestampMs: Long,
    val isOutgoing: Boolean,
    val deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.SENT,
    val replyTo: MessageRef? = null,
    val replySnippet: String? = null
)

data class ChatItem(
    val ref: ChatRef,
    val title: String,
    val lastMessageSnippet: String,
    val lastMessageTimestampMs: Long,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isChannel: Boolean = false,
    val isGroup: Boolean = false,
    val folderId: Int? = null,
    val avatarInitials: String = title.take(2).uppercase()
)

data class UserItem(
    val ref: UserRef,
    val firstName: String,
    val lastName: String? = null,
    val username: String? = null,
    val isOnline: Boolean = false,
    val bio: String? = null
)

sealed interface AuthState {
    data object Uninitialized : AuthState
    data object WaitTdlibParameters : AuthState
    data object WaitPhoneNumber : AuthState
    data class WaitCode(val phone: String, val codeLength: Int = 5) : AuthState
    data class WaitPassword(val hint: String?, val hasRecoveryEmail: Boolean) : AuthState
    data class WaitRegistration(val termsOfService: String?) : AuthState
    data class WaitQrCode(val link: String) : AuthState
    data class Ready(val activeAccount: AccountHandle) : AuthState
    data object Closed : AuthState
}

sealed interface ConnectionState {
    data object Connecting : ConnectionState
    data object ConnectingToProxy : ConnectionState
    data object Updating : ConnectionState
    data object Ready : ConnectionState
    data object WaitingForNetwork : ConnectionState
}
