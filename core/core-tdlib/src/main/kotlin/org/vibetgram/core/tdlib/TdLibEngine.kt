package org.vibetgram.core.tdlib

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import org.vibetgram.core.api.OutgoingContent
import org.vibetgram.core.api.SendOptions
import org.vibetgram.core.api.TelegramError
import org.vibetgram.core.api.TelegramResult
import org.vibetgram.core.api.TelegramService

/** Retry settings for idempotent reads. Writes are never retried by this adapter. */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMillis: Long = 100,
    val maxDelayMillis: Long = 2_000,
) {
    init {
        require(maxAttempts > 0)
        require(initialDelayMillis >= 0)
        require(maxDelayMillis >= initialDelayMillis)
    }
}

/** Immutable configuration passed to one account's TDLib database context. */
class TdLibConfig(
    val databaseDirectory: String,
    val apiId: Int,
    val apiHash: String,
    val deviceModel: String,
    val systemVersion: String = "Android",
    val applicationVersion: String = "VibeTGram",
    val enableStorageOptimizer: Boolean = true,
    encryptionKey: ByteArray = ByteArray(0),
    val filesDirectory: String = "$databaseDirectory/files",
) {
    private val encryptionKey = encryptionKey.copyOf()
    private var encryptionKeyConsumed = false

    @Synchronized
    fun takeParameters(): TdlibParameters {
        check(!encryptionKeyConsumed || encryptionKey.isEmpty()) {
            "account runtime must be recreated before TDLib recovery"
        }
        val parameters = TdlibParameters(
            databaseDirectory = databaseDirectory,
            filesDirectory = filesDirectory,
            apiId = apiId,
            apiHash = apiHash,
            deviceModel = deviceModel,
            systemVersion = systemVersion,
            applicationVersion = applicationVersion,
            enableStorageOptimizer = enableStorageOptimizer,
            databaseEncryptionKey = encryptionKey,
        )
        if (encryptionKey.isNotEmpty()) encryptionKeyConsumed = true
        encryptionKey.fill(0)
        return parameters
    }

    @Synchronized
    fun canCreateParameters(): Boolean = !encryptionKeyConsumed || encryptionKey.isEmpty()

    @Synchronized
    fun clear() {
        encryptionKey.fill(0)
        encryptionKeyConsumed = true
    }
}

/**
 * One request/response correlation table for one TDLib client.
 * TDLib request identifiers are never exposed through the semantic API.
 */
private class RequestCorrelator(private val client: TdClient) {
    private val pending = ConcurrentHashMap<Long, kotlinx.coroutines.CompletableDeferred<TdResult>>()

    suspend fun request(function: TdFunction): TdResult {
        val result = kotlinx.coroutines.CompletableDeferred<TdResult>()
        val earlyResult = AtomicReference<TdResult?>(null)
        var requestId = 0L
        requestId = client.send(function) { response ->
            val waiter = pending[requestId]
            if (waiter != null) {
                waiter.complete(response)
            } else {
                earlyResult.set(response)
            }
        }
        pending[requestId] = result
        earlyResult.getAndSet(null)?.let(result::complete)
        try {
            return result.await()
        } finally {
            pending.remove(requestId, result)
        }
    }

    fun cancelAll() {
        pending.values.forEach { it.cancel() }
        pending.clear()
    }
}

/** TDLib-backed semantic adapter. It owns exactly one ClientManager client. */
class TdLibEngine(
    private val account: AccountHandle,
    private val clientManager: TdClientManager,
    private val config: TdLibConfig,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
) : TelegramEngine {
    private val authorization = MutableStateFlow(AuthorizationState.UNKNOWN)
    private val authorizationDetails = MutableStateFlow(AuthorizationDetails(AuthorizationState.UNKNOWN))
    private val messageUpdates = MutableSharedFlow<MessageDelta>(extraBufferCapacity = 256)
    private val lifecycleLock = Any()
    private var client: TdClient? = null
    private var correlator: RequestCorrelator? = null
    private var started = false
    private var closed = false

    /** Starts the TDLib client and sends parameters before the encryption-key check. */
    override fun start() {
        synchronized(lifecycleLock) {
            check(!closed) { "TDLib engine is closed" }
            if (started) return
            started = true
            installClient(clientManager.createClient())
        }
    }

    /** Recreates the single account client after process death or native failure. */
    override fun recoverProcess() {
        synchronized(lifecycleLock) {
            check(!closed) { "TDLib engine is closed" }
            check(config.canCreateParameters()) { "account runtime must be recreated for secure recovery" }
            correlator?.cancelAll()
            client?.let(clientManager::destroyClient)
            authorization.value = AuthorizationState.UNKNOWN
            authorizationDetails.value = AuthorizationDetails(AuthorizationState.UNKNOWN)
            installClient(clientManager.createClient())
        }
    }

    override fun observeAuthorization(): StateFlow<AuthorizationState> = authorization.asStateFlow()

    override fun observeAuthorizationDetails(): StateFlow<AuthorizationDetails> = authorizationDetails.asStateFlow()

    override suspend fun setAuthenticationPhoneNumber(phoneNumber: String): TelegramResult<Unit> =
        authorizationRequest(TdFunction.SetAuthenticationPhoneNumber(phoneNumber))

    override suspend fun checkAuthenticationCode(code: CharArray): TelegramResult<Unit> {
        val function = try {
            TdFunction.CheckAuthenticationCode(code)
        } finally {
            code.fill('\u0000')
        }
        return try {
            authorizationRequest(function)
        } finally {
            function.clear()
        }
    }

    override suspend fun checkAuthenticationPassword(password: CharArray): TelegramResult<Unit> {
        val function = try {
            TdFunction.CheckAuthenticationPassword(password)
        } finally {
            password.fill('\u0000')
        }
        return try {
            authorizationRequest(function)
        } finally {
            function.clear()
        }
    }

    override suspend fun requestQrCodeAuthentication(otherUserIds: List<Long>): TelegramResult<Unit> =
        authorizationRequest(TdFunction.RequestQrCodeAuthentication(otherUserIds.toList()))

    override suspend fun registerUser(
        firstName: String,
        lastName: String,
        disableNotification: Boolean,
    ): TelegramResult<Unit> = authorizationRequest(
        TdFunction.RegisterUser(firstName, lastName, disableNotification),
    )

    override suspend fun logOut(): TelegramResult<Unit> = authorizationRequest(TdFunction.LogOut)

    override suspend fun listChats(account: AccountHandle): TelegramResult<List<ChatSummary>> {
        if (account != this.account) return TelegramResult.Error(TelegramError.NotFound)
        val output = mutableListOf<ChatSummary>()
        var offsetChatId = 0L
        while (true) {
            when (val result = read(TdFunction.GetChats(offsetChatId, PAGE_SIZE))) {
                is TdResult.ChatIds -> {
                    for (chatId in result.chatIds) {
                        when (val detail = read(TdFunction.GetChat(chatId))) {
                            is TdResult.Chat -> output += ChatSummary(
                                ChatRef(detail.chat.id),
                                detail.chat.title,
                                detail.chat.unreadCount,
                            )
                            is TdResult.Error -> return TelegramResult.Error(detail.toTelegramError())
                            else -> return TelegramResult.Error(
                                TelegramError.Upstream(-1, "unexpected TDLib chat result"),
                            )
                        }
                    }
                    return TelegramResult.Success(output)
                }
                is TdResult.Chats -> {
                    output += result.chats.map { ChatSummary(ChatRef(it.id), it.title, it.unreadCount) }
                    if (!result.hasMore || result.chats.isEmpty()) return TelegramResult.Success(output)
                    offsetChatId = result.chats.last().id
                }
                is TdResult.Error -> return TelegramResult.Error(result.toTelegramError())
                else -> return TelegramResult.Error(TelegramError.Upstream(-1, "unexpected TDLib result"))
            }
        }
    }

    override suspend fun getMessage(
        account: AccountHandle,
        message: MessageRef,
    ): TelegramResult<Message> {
        if (account != this.account) return TelegramResult.Error(TelegramError.NotFound)
        return when (val result = request(TdFunction.GetMessage(message.chat.value, message.value))) {
            is TdResult.Message -> TelegramResult.Success(toMessage(result.message))
            is TdResult.Error -> TelegramResult.Error(result.toTelegramError())
            else -> TelegramResult.Error(TelegramError.Upstream(-1, "unexpected TDLib result"))
        }
    }

    override suspend fun listMessages(
        account: AccountHandle,
        chat: ChatRef,
    ): TelegramResult<List<Message>> {
        if (account != this.account) return TelegramResult.Error(TelegramError.NotFound)
        val output = mutableListOf<Message>()
        val seen = mutableSetOf<Long>()
        var fromMessageId = 0L
        while (true) {
            when (val result = read(TdFunction.GetChatHistory(chat.value, fromMessageId, PAGE_SIZE))) {
                is TdResult.ChatHistory -> {
                    val newMessages = result.messages.filter { seen.add(it.id) }
                    output += newMessages.map(::toMessage)
                    if (!result.hasMore || result.messages.isEmpty() || newMessages.isEmpty()) {
                        return TelegramResult.Success(output)
                    }
                    fromMessageId = result.messages.last().id
                }
                is TdResult.Error -> return TelegramResult.Error(result.toTelegramError())
                else -> return TelegramResult.Error(TelegramError.Upstream(-1, "unexpected TDLib result"))
            }
        }
    }

    override fun observeMessages(account: AccountHandle, chat: ChatRef): Flow<MessageDelta> =
        if (account == this.account) {
            messageUpdates.filter { it.chat == chat }
        } else {
            kotlinx.coroutines.flow.emptyFlow()
        }

    override suspend fun sendMessage(
        account: AccountHandle,
        chat: ChatRef,
        content: OutgoingContent,
        options: SendOptions,
    ): TelegramResult<MessageRef> {
        if (account != this.account) return TelegramResult.Error(TelegramError.NotFound)
        val text = (content as? OutgoingContent.Text)?.value
            ?: return TelegramResult.Error(TelegramError.UpstreamUnsupported)
        val result = request(
            TdFunction.SendMessage(
                chatId = chat.value,
                text = text,
                replyToMessageId = options.replyTo?.value,
                disableNotification = options.disableNotification,
            ),
        )
        return when (result) {
            is TdResult.Message -> TelegramResult.Success(MessageRef(chat, result.message.id))
            is TdResult.SentMessage -> TelegramResult.Success(MessageRef(chat, result.message.id))
            is TdResult.Error -> TelegramResult.Error(result.toTelegramError())
            else -> TelegramResult.Error(TelegramError.Upstream(-1, "unexpected TDLib result"))
        }
    }

    override fun close() {
        synchronized(lifecycleLock) {
            if (closed) return
            closed = true
            correlator?.cancelAll()
            client?.let(clientManager::destroyClient)
            client = null
            correlator = null
            authorization.value = AuthorizationState.CLOSED
            authorizationDetails.value = AuthorizationDetails(AuthorizationState.CLOSED)
            config.clear()
        }
    }

    private fun installClient(newClient: TdClient) {
        client = newClient
        correlator = RequestCorrelator(newClient)
        newClient.setUpdateHandler(::handleUpdate)
        val parameters = config.takeParameters()
        try {
            newClient.send(TdFunction.SetTdlibParameters(parameters)) { result ->
                if (isCurrent(newClient)) handleSetupResult(result)
            }
        } finally {
            parameters.clear()
        }
    }

    private fun isCurrent(candidate: TdClient): Boolean =
        synchronized(lifecycleLock) { !closed && client === candidate }

    private fun handleSetupResult(result: TdResult) {
        if (result is TdResult.Error) {
            authorization.value = when (result.error) {
                TdError.NetworkUnavailable -> AuthorizationState.UNKNOWN
                TdError.InvalidParameters -> AuthorizationState.WAITING_PARAMETERS
                else -> AuthorizationState.WAITING_PARAMETERS
            }
        }
    }

    private fun handleUpdate(update: TdUpdate) {
        synchronized(lifecycleLock) {
            if (closed) return
            when (update) {
                is TdUpdate.AuthorizationStateChanged -> {
                    authorization.value = update.state
                    authorizationDetails.value = AuthorizationDetails(
                        state = update.state,
                        passwordHint = update.passwordHint,
                        qrCodeLink = update.qrCodeLink,
                        terms = update.terms,
                    )
                }
                is TdUpdate.NewMessage -> messageUpdates.tryEmit(MessageDelta.Added(toMessage(update.message)))
                is TdUpdate.MessageDeleted -> messageUpdates.tryEmit(
                    MessageDelta.Deleted(ChatRef(update.chatId), MessageRef(ChatRef(update.chatId), update.messageId)),
                )
            }
        }
    }

    private suspend fun read(function: TdFunction): TdResult = requestWithRetry(function)

    private suspend fun authorizationRequest(function: TdFunction): TelegramResult<Unit> =
        when (val result = request(function)) {
            TdResult.Ok -> TelegramResult.Success(Unit)
            is TdResult.Error -> TelegramResult.Error(result.toTelegramError())
            else -> TelegramResult.Error(TelegramError.Upstream(-1, "unexpected TDLib result"))
        }

    private suspend fun request(function: TdFunction): TdResult =
        correlator?.request(function) ?: TdResult.Error(TdError.Internal, safeMessage = "engine not started")

    private suspend fun requestWithRetry(function: TdFunction): TdResult {
        var attempt = 1
        var delayMillis = retryPolicy.initialDelayMillis
        while (true) {
            val result = request(function)
            if (result !is TdResult.Error || !result.isRetryable || attempt >= retryPolicy.maxAttempts) {
                return result
            }
            val waitMillis = maxOf(delayMillis, result.retryAfterMillis).coerceAtMost(retryPolicy.maxDelayMillis)
            if (waitMillis > 0) delay(waitMillis)
            attempt++
            delayMillis = (delayMillis * 2).coerceAtMost(retryPolicy.maxDelayMillis)
        }
    }

    private fun toMessage(message: TdMessage): Message =
        Message(
            ref = MessageRef(ChatRef(message.chatId), message.id),
            text = message.text,
            sentAt = Instant.ofEpochSecond(message.sentAtEpochSeconds),
        )

    private fun TdResult.Error.toTelegramError(): TelegramError = when (error) {
        TdError.NetworkUnavailable -> TelegramError.NetworkUnavailable
        TdError.RateLimited -> TelegramError.RateLimited((retryAfterMillis / 1_000).coerceAtLeast(0))
        TdError.NotFound -> TelegramError.NotFound
        TdError.Conflict -> TelegramError.Conflict
        TdError.Unsupported -> TelegramError.UpstreamUnsupported
        TdError.InvalidParameters, TdError.Internal -> TelegramError.Upstream(code, safeMessage)
    }

    private val TdResult.Error.isRetryable: Boolean
        get() = error == TdError.NetworkUnavailable

    private companion object {
        const val PAGE_SIZE = 100
    }
}
