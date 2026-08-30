package org.vibetgram.core.tdlib

/** The authorization states surfaced by TDLib's updateAuthorizationState. */
enum class AuthorizationState {
    UNKNOWN,
    WAITING_PARAMETERS,
    WAITING_PHONE_NUMBER,
    WAITING_CODE,
    WAITING_PASSWORD,
    WAITING_QR_CODE,
    WAITING_REGISTRATION,
    READY,
    LOGGING_OUT,
    CLOSING,
    CLOSED,
}

/** Typed TDLib errors kept behind the adapter boundary. */
enum class TdError {
    NetworkUnavailable,
    RateLimited,
    NotFound,
    Conflict,
    Unsupported,
    InvalidParameters,
    Internal,
}

/** A typed subset of TDLib functions used by the semantic bootstrap adapter. */
sealed interface TdFunction {
    data class SetTdlibParameters(
        val parameters: TdlibParameters,
    ) : TdFunction

    data class SetAuthenticationPhoneNumber(val phoneNumber: String) : TdFunction {
        init {
            require(phoneNumber.isNotBlank())
        }
    }

    class CheckAuthenticationCode(code: CharArray) : TdFunction {
        private val code = code.copyOf()

        fun copyCode(): CharArray = code.copyOf()
        fun clear() = code.fill('\u0000')
    }

    class CheckAuthenticationPassword(password: CharArray) : TdFunction {
        private val password = password.copyOf()

        fun copyPassword(): CharArray = password.copyOf()
        fun clear() = password.fill('\u0000')
    }

    data class RequestQrCodeAuthentication(val otherUserIds: List<Long> = emptyList()) : TdFunction {
        init {
            require(otherUserIds.all { it > 0 })
        }
    }

    data class RegisterUser(
        val firstName: String,
        val lastName: String,
        val disableNotification: Boolean = false,
    ) : TdFunction {
        init {
            require(firstName.isNotBlank())
        }
    }

    data object LogOut : TdFunction
    data object Close : TdFunction

    data class GetChats(
        val offsetChatId: Long = 0,
        val limit: Int,
    ) : TdFunction {
        init {
            require(offsetChatId >= 0)
            require(limit > 0)
        }
    }

    data class GetChat(val chatId: Long) : TdFunction {
        init {
            require(chatId != 0L)
        }
    }

    data class GetChatHistory(
        val chatId: Long,
        val fromMessageId: Long = 0,
        val limit: Int,
    ) : TdFunction {
        init {
            require(chatId != 0L)
            require(fromMessageId >= 0)
            require(limit > 0)
        }
    }

    data class GetMessage(
        val chatId: Long,
        val messageId: Long,
    ) : TdFunction {
        init {
            require(chatId != 0L)
            require(messageId > 0)
        }
    }

    data class SendMessage(
        val chatId: Long,
        val text: String,
        val replyToMessageId: Long? = null,
        val disableNotification: Boolean = false,
    ) : TdFunction {
        init {
            require(chatId != 0L)
            require(text.isNotBlank())
            require(replyToMessageId == null || replyToMessageId > 0)
        }
    }
}

/** Typed TDLib results delivered to a correlated request callback. */
sealed interface TdResult {
    data object Ok : TdResult
    data class AuthorizationState(val state: org.vibetgram.core.tdlib.AuthorizationState) : TdResult
    /** Exact result of pinned getChats: identifiers are resolved through getChat. */
    data class ChatIds(val chatIds: List<Long>, val totalCount: Int) : TdResult
    data class Chat(val chat: TdChat) : TdResult
    /** Deterministic typed seam retained for pagination/correlation probes. */
    data class Chats(val chats: List<TdChat>, val hasMore: Boolean) : TdResult
    data class ChatHistory(val messages: List<TdMessage>, val hasMore: Boolean) : TdResult
    data class Message(val message: TdMessage) : TdResult
    data class SentMessage(val message: TdMessage) : TdResult
    data class Error(
        val error: TdError,
        val code: Int = 0,
        val safeMessage: String? = null,
        val retryAfterMillis: Long = 0,
    ) : TdResult
}

data class TdChat(val id: Long, val title: String, val unreadCount: Int = 0) {
    init {
        require(id != 0L)
        require(title.isNotBlank())
        require(unreadCount >= 0)
    }
}

data class TdMessage(
    val chatId: Long,
    val id: Long,
    val text: String,
    val sentAtEpochSeconds: Long,
) {
    init {
        require(chatId != 0L)
        require(id > 0)
    }
}

class TdlibParameters(
    val databaseDirectory: String,
    val filesDirectory: String,
    val apiId: Int,
    val apiHash: String,
    val deviceModel: String,
    val systemVersion: String = "Android",
    val applicationVersion: String = "VibeTGram",
    val enableStorageOptimizer: Boolean = true,
    databaseEncryptionKey: ByteArray = ByteArray(0),
) {
    val databaseEncryptionKey: ByteArray = databaseEncryptionKey.copyOf()

    init {
        require(databaseDirectory.isNotBlank())
        require(filesDirectory.isNotBlank())
        require(apiId > 0)
        require(apiHash.isNotBlank())
        require(deviceModel.isNotBlank())
    }

    fun clear() {
        databaseEncryptionKey.fill(0)
    }
}

data class AuthorizationTerms(
    val text: String,
    val minimumUserAge: Int? = null,
    val showPopup: Boolean = false,
) {
    init {
        require(text.isNotBlank())
        require(minimumUserAge == null || minimumUserAge >= 0)
    }
}

data class AuthorizationDetails(
    val state: AuthorizationState,
    val passwordHint: String? = null,
    val qrCodeLink: String? = null,
    val terms: AuthorizationTerms? = null,
)

/** Minimal callback port implemented by the real TDLib JNI binding and test fakes. */
interface TdClient {
    fun setUpdateHandler(handler: (TdUpdate) -> Unit)
    fun send(function: TdFunction, callback: (TdResult) -> Unit): Long
    fun close()
}

/** ClientManager seam; it is the only factory used by [TdLibEngine]. */
interface ClientManager {
    fun createClient(): TdClient

    fun destroyClient(client: TdClient) {
        client.close()
    }
}

typealias TdClientManager = ClientManager

sealed interface TdUpdate {
    data class AuthorizationStateChanged(
        val state: AuthorizationState,
        val passwordHint: String? = null,
        val qrCodeLink: String? = null,
        val terms: AuthorizationTerms? = null,
    ) : TdUpdate
    data class NewMessage(val message: TdMessage) : TdUpdate
    data class MessageDeleted(val chatId: Long, val messageId: Long) : TdUpdate
}
