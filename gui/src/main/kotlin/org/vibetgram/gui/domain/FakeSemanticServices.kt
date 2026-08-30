package org.vibetgram.gui.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicLong

/**
 * Fake implementation of semantic services for UI preview, deterministic local tests, and bootstrap validation.
 */
class FakeSemanticServices :
    AuthorizationService,
    AccountManager,
    ChatQuery,
    ChatMutation,
    MessageQuery,
    MessageComposer,
    MessageMutation,
    DraftService {

    private val authState = MutableStateFlow<AuthState>(AuthState.WaitPhoneNumber)
    private val accounts = MutableStateFlow<List<AccountHandle>>(listOf(AccountHandle("account_primary")))
    private val activeAccount = MutableStateFlow<AccountHandle?>(AccountHandle("account_primary"))
    private val connectionStates = MutableStateFlow<Map<String, ConnectionState>>(
        mapOf("account_primary" to ConnectionState.Ready)
    )

    private val folders = MutableStateFlow<List<FolderRef>>(
        listOf(
            FolderRef(0, "All", isIncludedByDefault = true, unreadCount = 3),
            FolderRef(1, "Personal", isIncludedByDefault = false, unreadCount = 1),
            FolderRef(2, "Work", isIncludedByDefault = false, unreadCount = 2)
        )
    )

    private val chats = MutableStateFlow<List<ChatItem>>(
        listOf(
            ChatItem(
                ref = ChatRef(101L),
                title = "Alice Smith",
                lastMessageSnippet = "Hey, have you checked the new design tokens?",
                lastMessageTimestampMs = System.currentTimeMillis() - 120_000,
                unreadCount = 2,
                isPinned = true,
                folderId = 1
            ),
            ChatItem(
                ref = ChatRef(102L),
                title = "VibeTGram Devs",
                lastMessageSnippet = "GUI bootstrap slice is ready for review!",
                lastMessageTimestampMs = System.currentTimeMillis() - 600_000,
                unreadCount = 1,
                isGroup = true,
                folderId = 2
            ),
            ChatItem(
                ref = ChatRef(103L),
                title = "Telegram News",
                lastMessageSnippet = "TDLib 1.8.x integration updates",
                lastMessageTimestampMs = System.currentTimeMillis() - 3_600_000,
                unreadCount = 0,
                isChannel = true,
                folderId = 0
            )
        )
    )

    private val messagesByChat = MutableStateFlow<Map<Long, List<MessageItem>>>(
        mapOf(
            101L to listOf(
                MessageItem(
                    ref = MessageRef(1L),
                    chatRef = ChatRef(101L),
                    senderRef = UserRef(201L),
                    senderName = "Alice Smith",
                    text = "Hi! How is the Material 3 Expressive UI coming along?",
                    timestampMs = System.currentTimeMillis() - 300_000,
                    isOutgoing = false
                ),
                MessageItem(
                    ref = MessageRef(2L),
                    chatRef = ChatRef(101L),
                    senderRef = UserRef(1L),
                    senderName = "Me",
                    text = "Working on adaptive layouts and accessibility right now.",
                    timestampMs = System.currentTimeMillis() - 200_000,
                    isOutgoing = true,
                    deliveryStatus = MessageDeliveryStatus.READ
                ),
                MessageItem(
                    ref = MessageRef(3L),
                    chatRef = ChatRef(101L),
                    senderRef = UserRef(201L),
                    senderName = "Alice Smith",
                    text = "Hey, have you checked the new design tokens?",
                    timestampMs = System.currentTimeMillis() - 120_000,
                    isOutgoing = false
                )
            )
        )
    )

    private val drafts = MutableStateFlow<Map<Long, Draft>>(emptyMap())
    private val nextMessageId = AtomicLong(100L)

    // AuthorizationService
    override fun observeAuthState(): Flow<AuthState> = authState.asStateFlow()

    override suspend fun setPhoneNumber(phone: String): TelegramResult<Unit> {
        if (phone.isBlank() || phone.length < 5) {
            return TelegramResult.failure(TelegramError.Upstream(400, "PHONE_NUMBER_INVALID"))
        }
        authState.value = AuthState.WaitCode(phone = phone, codeLength = 5)
        return TelegramResult.success(Unit)
    }

    override suspend fun checkAuthCode(code: String): TelegramResult<Unit> {
        return if (code == "12345" || code == "00000") {
            val handle = AccountHandle("account_primary")
            authState.value = AuthState.Ready(handle)
            TelegramResult.success(Unit)
        } else if (code == "2fa") {
            authState.value = AuthState.WaitPassword(hint = "Favorite IDE", hasRecoveryEmail = true)
            TelegramResult.success(Unit)
        } else {
            TelegramResult.failure(TelegramError.Upstream(400, "PHONE_CODE_INVALID"))
        }
    }

    override suspend fun checkPassword(password: String): TelegramResult<Unit> {
        return if (password.isNotEmpty()) {
            val handle = AccountHandle("account_primary")
            authState.value = AuthState.Ready(handle)
            TelegramResult.success(Unit)
        } else {
            TelegramResult.failure(TelegramError.Upstream(400, "PASSWORD_HASH_INVALID"))
        }
    }

    override suspend fun acceptTermsAndRegister(firstName: String, lastName: String?): TelegramResult<Unit> {
        val handle = AccountHandle("account_primary")
        authState.value = AuthState.Ready(handle)
        return TelegramResult.success(Unit)
    }

    override suspend fun requestQrCode(): TelegramResult<String> {
        val qrLink = "tg://login?token=fake_bootstrap_qr_token"
        authState.value = AuthState.WaitQrCode(qrLink)
        return TelegramResult.success(qrLink)
    }

    override suspend fun logOut(): TelegramResult<Unit> {
        authState.value = AuthState.WaitPhoneNumber
        return TelegramResult.success(Unit)
    }

    // AccountManager
    override fun observeAccounts(): Flow<List<AccountHandle>> = accounts.asStateFlow()
    override fun observeActiveAccount(): Flow<AccountHandle?> = activeAccount.asStateFlow()
    override fun observeConnectionState(account: AccountHandle): Flow<ConnectionState> =
        connectionStates.map { it[account.id] ?: ConnectionState.WaitingForNetwork }

    override suspend fun switchAccount(account: AccountHandle): TelegramResult<Unit> {
        activeAccount.value = account
        return TelegramResult.success(Unit)
    }

    override suspend fun createAccountContext(): TelegramResult<AccountHandle> {
        val newHandle = AccountHandle("account_${System.currentTimeMillis()}")
        accounts.value = accounts.value + newHandle
        return TelegramResult.success(newHandle)
    }

    override suspend fun removeAccount(account: AccountHandle): TelegramResult<Unit> {
        accounts.value = accounts.value.filterNot { it.id == account.id }
        if (activeAccount.value?.id == account.id) {
            activeAccount.value = accounts.value.firstOrNull()
        }
        return TelegramResult.success(Unit)
    }

    // ChatQuery
    override fun observeChats(account: AccountHandle, folderId: Int?): Flow<List<ChatItem>> {
        return chats.map { list ->
            if (folderId == null || folderId == 0) list
            else list.filter { it.folderId == folderId }
        }
    }

    override fun observeChat(account: AccountHandle, chatRef: ChatRef): Flow<ChatItem?> {
        return chats.map { list -> list.find { it.ref.id == chatRef.id } }
    }

    override fun observeFolders(account: AccountHandle): Flow<List<FolderRef>> = folders.asStateFlow()

    override suspend fun searchChats(account: AccountHandle, query: String): TelegramResult<List<ChatItem>> {
        val lower = query.lowercase()
        val results = chats.value.filter {
            it.title.lowercase().contains(lower) || it.lastMessageSnippet.lowercase().contains(lower)
        }
        return TelegramResult.success(results)
    }

    // ChatMutation
    override suspend fun pinChat(account: AccountHandle, chatRef: ChatRef, isPinned: Boolean): TelegramResult<Unit> {
        chats.value = chats.value.map {
            if (it.ref.id == chatRef.id) it.copy(isPinned = isPinned) else it
        }
        return TelegramResult.success(Unit)
    }

    override suspend fun muteChat(account: AccountHandle, chatRef: ChatRef, isMuted: Boolean): TelegramResult<Unit> {
        chats.value = chats.value.map {
            if (it.ref.id == chatRef.id) it.copy(isMuted = isMuted) else it
        }
        return TelegramResult.success(Unit)
    }

    override suspend fun markChatAsRead(account: AccountHandle, chatRef: ChatRef): TelegramResult<Unit> {
        chats.value = chats.value.map {
            if (it.ref.id == chatRef.id) it.copy(unreadCount = 0) else it
        }
        return TelegramResult.success(Unit)
    }

    // MessageQuery
    override fun observeMessages(account: AccountHandle, chatRef: ChatRef): Flow<List<MessageItem>> {
        return messagesByChat.map { it[chatRef.id] ?: emptyList() }
    }

    override suspend fun loadHistory(
        account: AccountHandle,
        chatRef: ChatRef,
        fromMessageRef: MessageRef?,
        limit: Int
    ): TelegramResult<List<MessageItem>> {
        val list = messagesByChat.value[chatRef.id] ?: emptyList()
        return TelegramResult.success(list.take(limit))
    }

    // MessageComposer
    override suspend fun sendMessage(
        account: AccountHandle,
        chatRef: ChatRef,
        content: OutgoingContent,
        options: SendOptions
    ): TelegramResult<MessageRef> {
        val id = nextMessageId.incrementAndGet()
        val text = when (content) {
            is OutgoingContent.Text -> content.text
            is OutgoingContent.Photo -> content.caption ?: "[Photo]"
            is OutgoingContent.Document -> "[Document: ${content.fileName}]"
        }
        val newItem = MessageItem(
            ref = MessageRef(id),
            chatRef = chatRef,
            senderRef = UserRef(1L),
            senderName = "Me",
            text = text,
            timestampMs = System.currentTimeMillis(),
            isOutgoing = true,
            deliveryStatus = MessageDeliveryStatus.SENT,
            replyTo = options.replyToMessageRef
        )
        val current = messagesByChat.value[chatRef.id] ?: emptyList()
        messagesByChat.value = messagesByChat.value + (chatRef.id to (current + newItem))
        return TelegramResult.success(MessageRef(id))
    }

    override suspend fun cancelSending(
        account: AccountHandle,
        chatRef: ChatRef,
        messageRef: MessageRef
    ): TelegramResult<Unit> {
        val current = messagesByChat.value[chatRef.id] ?: emptyList()
        messagesByChat.value = messagesByChat.value + (chatRef.id to current.filterNot { it.ref.id == messageRef.id })
        return TelegramResult.success(Unit)
    }

    // MessageMutation
    override suspend fun deleteMessage(
        account: AccountHandle,
        chatRef: ChatRef,
        messageRef: MessageRef,
        forEveryone: Boolean
    ): TelegramResult<Unit> {
        val current = messagesByChat.value[chatRef.id] ?: emptyList()
        messagesByChat.value = messagesByChat.value + (chatRef.id to current.filterNot { it.ref.id == messageRef.id })
        return TelegramResult.success(Unit)
    }

    override suspend fun editMessageText(
        account: AccountHandle,
        chatRef: ChatRef,
        messageRef: MessageRef,
        newText: String
    ): TelegramResult<Unit> {
        val current = messagesByChat.value[chatRef.id] ?: emptyList()
        messagesByChat.value = messagesByChat.value + (chatRef.id to current.map {
            if (it.ref.id == messageRef.id) it.copy(text = newText) else it
        })
        return TelegramResult.success(Unit)
    }

    // DraftService
    override fun observeDraft(account: AccountHandle, chatRef: ChatRef): Flow<Draft?> {
        return drafts.map { it[chatRef.id] }
    }

    override suspend fun saveDraft(account: AccountHandle, chatRef: ChatRef, draft: Draft): TelegramResult<Unit> {
        drafts.value = drafts.value + (chatRef.id to draft)
        return TelegramResult.success(Unit)
    }

    override suspend fun clearDraft(account: AccountHandle, chatRef: ChatRef): TelegramResult<Unit> {
        drafts.value = drafts.value - chatRef.id
        return TelegramResult.success(Unit)
    }
}
