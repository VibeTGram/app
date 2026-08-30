package org.vibetgram.app

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.vibetgram.core.api.AccountHandle as CoreAccountHandle
import org.vibetgram.core.api.ChatRef as CoreChatRef
import org.vibetgram.core.api.Message as CoreMessage
import org.vibetgram.core.api.MessageDelta as CoreMessageDelta
import org.vibetgram.core.api.MessageRef as CoreMessageRef
import org.vibetgram.core.api.OutgoingContent as CoreOutgoingContent
import org.vibetgram.core.api.SendOptions as CoreSendOptions
import org.vibetgram.core.api.TelegramError as CoreError
import org.vibetgram.core.api.TelegramResult as CoreResult
import org.vibetgram.core.tdlib.AuthorizationDetails
import org.vibetgram.core.tdlib.AuthorizationState
import org.vibetgram.core.tdlib.TelegramEngine
import org.vibetgram.gui.api.GuiDependencies
import org.vibetgram.gui.domain.AccountHandle as GuiAccountHandle
import org.vibetgram.gui.domain.AccountManager as GuiAccountManager
import org.vibetgram.gui.domain.AuthState
import org.vibetgram.gui.domain.AuthorizationService
import org.vibetgram.gui.domain.ChatItem
import org.vibetgram.gui.domain.ChatMutation
import org.vibetgram.gui.domain.ChatQuery
import org.vibetgram.gui.domain.ChatRef as GuiChatRef
import org.vibetgram.gui.domain.ConnectionState
import org.vibetgram.gui.domain.Draft
import org.vibetgram.gui.domain.DraftService
import org.vibetgram.gui.domain.FolderRef
import org.vibetgram.gui.domain.MessageComposer
import org.vibetgram.gui.domain.MessageDeliveryStatus
import org.vibetgram.gui.domain.MessageItem
import org.vibetgram.gui.domain.MessageMutation
import org.vibetgram.gui.domain.MessageQuery
import org.vibetgram.gui.domain.MessageRef as GuiMessageRef
import org.vibetgram.gui.domain.OutgoingContent as GuiOutgoingContent
import org.vibetgram.gui.domain.SendOptions as GuiSendOptions
import org.vibetgram.gui.domain.TelegramError as GuiError
import org.vibetgram.gui.domain.TelegramResult as GuiResult
import org.vibetgram.gui.domain.UserRef

/**
 * The explicit Android composition adapter between Core's stable semantic API and
 * the replaceable GUI contract. It delegates every Telegram operation to the real
 * account-scoped [TelegramEngine]; it never supplies preview or demo records.
 */
internal class CoreGuiDependenciesAdapter(
    private val coreAccount: CoreAccountHandle,
    private val engine: TelegramEngine,
    private val scope: CoroutineScope,
    private val logoutAccount: suspend () -> CoreResult<Unit> = engine::logOut,
) : AuthorizationService,
    GuiAccountManager,
    ChatQuery,
    ChatMutation,
    MessageQuery,
    MessageComposer,
    MessageMutation,
    DraftService {

    private val guiAccount = GuiAccountHandle("active-telegram-account")
    private val submittedPhone = MutableStateFlow("")
    private val messageFlows = ConcurrentHashMap<Long, MutableStateFlow<List<MessageItem>>>()
    private val observedChats = ConcurrentHashMap.newKeySet<Long>()
    private val drafts = ConcurrentHashMap<Pair<Long, Long>, MutableStateFlow<Draft?>>()

    val dependencies = GuiDependencies(
        authService = this,
        accountManager = this,
        chatQuery = this,
        chatMutation = this,
        messageQuery = this,
        messageComposer = this,
        messageMutation = this,
        draftService = this,
        coroutineScope = scope,
    )

    override fun observeAuthState(): Flow<AuthState> = combine(
        engine.observeAuthorizationDetails(),
        submittedPhone,
    ) { details, phone -> details.toGuiAuthState(phone) }

    override suspend fun setPhoneNumber(phone: String): GuiResult<Unit> {
        submittedPhone.value = phone
        return engine.setAuthenticationPhoneNumber(phone).toGuiResult()
    }

    override suspend fun checkAuthCode(code: String): GuiResult<Unit> =
        engine.checkAuthenticationCode(code.toCharArray()).toGuiResult()

    override suspend fun checkPassword(password: String): GuiResult<Unit> =
        engine.checkAuthenticationPassword(password.toCharArray()).toGuiResult()

    override suspend fun acceptTermsAndRegister(firstName: String, lastName: String?): GuiResult<Unit> =
        engine.registerUser(firstName, lastName.orEmpty()).toGuiResult()

    override suspend fun requestQrCode(): GuiResult<String> {
        return when (val result = engine.requestQrCodeAuthentication()) {
            is CoreResult.Success -> {
                val link = withTimeoutOrNull(QR_LINK_TIMEOUT_MILLIS) {
                    engine.observeAuthorizationDetails()
                        .map { details -> details.qrCodeLink }
                        .first { candidate -> !candidate.isNullOrBlank() }
                }
                if (link == null) GuiResult.failure(GuiError.NetworkUnavailable) else GuiResult.success(link)
            }
            is CoreResult.Error -> GuiResult.failure(result.error.toGuiError())
        }
    }

    override suspend fun logOut(): GuiResult<Unit> = logoutAccount().toGuiResult()

    override fun observeAccounts(): Flow<List<GuiAccountHandle>> = flow { emit(listOf(guiAccount)) }

    override fun observeActiveAccount(): Flow<GuiAccountHandle?> = flow { emit(guiAccount) }

    override fun observeConnectionState(account: GuiAccountHandle): Flow<ConnectionState> {
        if (account != guiAccount) return flow { emit(ConnectionState.WaitingForNetwork) }
        return engine.observeAuthorization().map { state ->
            when (state) {
                AuthorizationState.READY -> ConnectionState.Ready
                AuthorizationState.UNKNOWN -> ConnectionState.WaitingForNetwork
                else -> ConnectionState.Connecting
            }
        }
    }

    override suspend fun switchAccount(account: GuiAccountHandle): GuiResult<Unit> =
        if (account == guiAccount) GuiResult.success(Unit) else GuiResult.failure(GuiError.NotFound)

    override suspend fun createAccountContext(): GuiResult<GuiAccountHandle> =
        GuiResult.failure(GuiError.Unsupported)

    override suspend fun removeAccount(account: GuiAccountHandle): GuiResult<Unit> =
        GuiResult.failure(GuiError.Unsupported)

    override fun observeChats(account: GuiAccountHandle, folderId: Int?): Flow<List<ChatItem>> = flow {
        if (account != guiAccount || (folderId != null && folderId != 0)) {
            emit(emptyList())
            return@flow
        }
        emit(loadChats())
    }

    override fun observeChat(account: GuiAccountHandle, chatRef: GuiChatRef): Flow<ChatItem?> = flow {
        emit(if (account == guiAccount) loadChats().firstOrNull { it.ref == chatRef } else null)
    }

    override fun observeFolders(account: GuiAccountHandle): Flow<List<FolderRef>> = flow { emit(emptyList()) }

    override suspend fun searchChats(account: GuiAccountHandle, query: String): GuiResult<List<ChatItem>> {
        if (account != guiAccount) return GuiResult.failure(GuiError.NotFound)
        return GuiResult.success(loadChats().filter {
            it.title.contains(query, ignoreCase = true) || it.lastMessageSnippet.contains(query, ignoreCase = true)
        })
    }

    override suspend fun pinChat(
        account: GuiAccountHandle,
        chatRef: GuiChatRef,
        isPinned: Boolean,
    ): GuiResult<Unit> = GuiResult.failure(GuiError.Unsupported)

    override suspend fun muteChat(
        account: GuiAccountHandle,
        chatRef: GuiChatRef,
        isMuted: Boolean,
    ): GuiResult<Unit> = GuiResult.failure(GuiError.Unsupported)

    override suspend fun markChatAsRead(account: GuiAccountHandle, chatRef: GuiChatRef): GuiResult<Unit> =
        GuiResult.failure(GuiError.Unsupported)

    override fun observeMessages(account: GuiAccountHandle, chatRef: GuiChatRef): Flow<List<MessageItem>> {
        if (account != guiAccount) return flow { emit(emptyList()) }
        val state = messageFlows.computeIfAbsent(chatRef.id) { MutableStateFlow(emptyList()) }
        if (observedChats.add(chatRef.id)) {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                engine.observeMessages(coreAccount, CoreChatRef(chatRef.id)).collect { delta ->
                    state.value = applyDelta(state.value, delta)
                }
            }
            scope.launch {
                when (val initial = engine.listMessages(coreAccount, CoreChatRef(chatRef.id))) {
                    is CoreResult.Success -> state.value = (
                        initial.value.map(::toGuiMessage) + state.value
                    ).distinctBy { it.ref }.sortedByDescending { it.timestampMs }
                    is CoreResult.Error -> Unit
                }
            }
        }
        return state
    }

    override suspend fun loadHistory(
        account: GuiAccountHandle,
        chatRef: GuiChatRef,
        fromMessageRef: GuiMessageRef?,
        limit: Int,
    ): GuiResult<List<MessageItem>> {
        if (account != guiAccount) return GuiResult.failure(GuiError.NotFound)
        return engine.listMessages(coreAccount, CoreChatRef(chatRef.id)).map { messages ->
            messages
                .let { rows ->
                    if (fromMessageRef == null) rows else rows.dropWhile { it.ref.value != fromMessageRef.id }.drop(1)
                }
                .take(limit)
                .map(::toGuiMessage)
        }.toGuiResult()
    }

    override suspend fun sendMessage(
        account: GuiAccountHandle,
        chatRef: GuiChatRef,
        content: GuiOutgoingContent,
        options: GuiSendOptions,
    ): GuiResult<GuiMessageRef> {
        if (account != guiAccount) return GuiResult.failure(GuiError.NotFound)
        val text = (content as? GuiOutgoingContent.Text)?.text
            ?: return GuiResult.failure(GuiError.Unsupported)
        val result = engine.sendMessage(
            coreAccount,
            CoreChatRef(chatRef.id),
            CoreOutgoingContent.Text(text),
            CoreSendOptions(
                replyTo = options.replyToMessageRef?.let { CoreMessageRef(CoreChatRef(chatRef.id), it.id) },
                disableNotification = options.silent,
            ),
        )
        return when (result) {
            is CoreResult.Success -> {
                val ref = GuiMessageRef(result.value.value)
                val outgoing = MessageItem(
                    ref = ref,
                    chatRef = chatRef,
                    senderRef = UserRef(0),
                    senderName = "You",
                    text = text,
                    timestampMs = Instant.now().toEpochMilli(),
                    isOutgoing = true,
                    deliveryStatus = MessageDeliveryStatus.SENT,
                    replyTo = options.replyToMessageRef,
                )
                messageFlows.computeIfAbsent(chatRef.id) { MutableStateFlow(emptyList()) }
                    .let { it.value = (it.value + outgoing).distinctBy { row -> row.ref } }
                GuiResult.success(ref)
            }
            is CoreResult.Error -> GuiResult.failure(result.error.toGuiError())
        }
    }

    override suspend fun cancelSending(
        account: GuiAccountHandle,
        chatRef: GuiChatRef,
        messageRef: GuiMessageRef,
    ): GuiResult<Unit> = GuiResult.failure(GuiError.Unsupported)

    override suspend fun deleteMessage(
        account: GuiAccountHandle,
        chatRef: GuiChatRef,
        messageRef: GuiMessageRef,
        forEveryone: Boolean,
    ): GuiResult<Unit> = GuiResult.failure(GuiError.Unsupported)

    override suspend fun editMessageText(
        account: GuiAccountHandle,
        chatRef: GuiChatRef,
        messageRef: GuiMessageRef,
        newText: String,
    ): GuiResult<Unit> = GuiResult.failure(GuiError.Unsupported)

    override fun observeDraft(account: GuiAccountHandle, chatRef: GuiChatRef): Flow<Draft?> =
        draftFlow(account, chatRef)

    override suspend fun saveDraft(
        account: GuiAccountHandle,
        chatRef: GuiChatRef,
        draft: Draft,
    ): GuiResult<Unit> {
        if (account != guiAccount) return GuiResult.failure(GuiError.NotFound)
        draftFlowState(chatRef).value = draft
        return GuiResult.success(Unit)
    }

    override suspend fun clearDraft(account: GuiAccountHandle, chatRef: GuiChatRef): GuiResult<Unit> {
        if (account != guiAccount) return GuiResult.failure(GuiError.NotFound)
        draftFlowState(chatRef).value = null
        return GuiResult.success(Unit)
    }

    private suspend fun loadChats(): List<ChatItem> = when (val result = engine.listChats(coreAccount)) {
        is CoreResult.Success -> result.value.map { chat ->
            ChatItem(
                ref = GuiChatRef(chat.ref.value),
                title = chat.title,
                lastMessageSnippet = "",
                lastMessageTimestampMs = 0,
                unreadCount = chat.unreadCount,
            )
        }
        is CoreResult.Error -> emptyList()
    }

    private fun draftFlow(account: GuiAccountHandle, chatRef: GuiChatRef): Flow<Draft?> =
        if (account == guiAccount) draftFlowState(chatRef) else flow { emit(null) }

    private fun draftFlowState(chatRef: GuiChatRef): MutableStateFlow<Draft?> =
        drafts.computeIfAbsent(chatRef.id to 0L) { MutableStateFlow(null) }

    private fun toGuiMessage(message: CoreMessage): MessageItem = MessageItem(
        ref = GuiMessageRef(message.ref.value),
        chatRef = GuiChatRef(message.ref.chat.value),
        senderRef = UserRef(0),
        senderName = "Telegram",
        text = message.text,
        timestampMs = message.sentAt.toEpochMilli(),
        isOutgoing = false,
    )

    private fun applyDelta(current: List<MessageItem>, delta: CoreMessageDelta): List<MessageItem> = when (delta) {
        is CoreMessageDelta.Added -> (current + toGuiMessage(delta.message)).distinctBy { it.ref }
        is CoreMessageDelta.Edited -> current.map {
            if (it.ref.id == delta.message.ref.value) toGuiMessage(delta.message) else it
        }
        is CoreMessageDelta.Deleted -> current.filterNot { it.ref.id == delta.message.value }
    }

    private fun AuthorizationDetails.toGuiAuthState(phone: String): AuthState = when (state) {
        AuthorizationState.UNKNOWN -> AuthState.Uninitialized
        AuthorizationState.WAITING_PARAMETERS -> AuthState.WaitTdlibParameters
        AuthorizationState.WAITING_PHONE_NUMBER -> AuthState.WaitPhoneNumber
        AuthorizationState.WAITING_CODE -> AuthState.WaitCode(phone)
        AuthorizationState.WAITING_PASSWORD -> AuthState.WaitPassword(passwordHint, hasRecoveryEmail = false)
        AuthorizationState.WAITING_QR_CODE -> AuthState.WaitQrCode(qrCodeLink.orEmpty())
        AuthorizationState.WAITING_REGISTRATION -> AuthState.WaitRegistration(terms?.text)
        AuthorizationState.READY -> AuthState.Ready(guiAccount)
        AuthorizationState.LOGGING_OUT,
        AuthorizationState.CLOSING,
        AuthorizationState.CLOSED,
        -> AuthState.Closed
    }

    private fun CoreError.toGuiError(): GuiError = when (this) {
        CoreError.PermissionDenied -> GuiError.PermissionDenied
        CoreError.NotFound -> GuiError.NotFound
        is CoreError.RateLimited -> GuiError.RateLimited(retryAfterSeconds)
        CoreError.NetworkUnavailable -> GuiError.NetworkUnavailable
        CoreError.Conflict -> GuiError.Conflict
        CoreError.Cancelled -> GuiError.Cancelled
        CoreError.Unsupported,
        CoreError.UpstreamUnsupported,
        CoreError.IncompatibleSchema,
        CoreError.UserConfirmationRequired,
        -> GuiError.Unsupported
        is CoreError.Upstream -> GuiError.Upstream(safeCode, safeMessage)
    }

    private fun <T> CoreResult<T>.toGuiResult(): GuiResult<T> = when (this) {
        is CoreResult.Success -> GuiResult.success(value)
        is CoreResult.Error -> GuiResult.failure(error.toGuiError())
    }

    private companion object {
        const val QR_LINK_TIMEOUT_MILLIS = 15_000L
    }
}
