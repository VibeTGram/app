package org.vibetgram.gui.domain

import kotlinx.coroutines.flow.Flow

/**
 * Semantic service interfaces for VibeTGram core engine consumed by GUI state holders.
 * Normative reference: docs/api/two-level-api.md
 */

interface AuthorizationService {
    fun observeAuthState(): Flow<AuthState>
    suspend fun setPhoneNumber(phone: String): TelegramResult<Unit>
    suspend fun checkAuthCode(code: String): TelegramResult<Unit>
    suspend fun checkPassword(password: String): TelegramResult<Unit>
    suspend fun acceptTermsAndRegister(firstName: String, lastName: String?): TelegramResult<Unit>
    suspend fun requestQrCode(): TelegramResult<String>
    suspend fun logOut(): TelegramResult<Unit>
}

interface AccountManager {
    fun observeAccounts(): Flow<List<AccountHandle>>
    fun observeActiveAccount(): Flow<AccountHandle?>
    fun observeConnectionState(account: AccountHandle): Flow<ConnectionState>
    suspend fun switchAccount(account: AccountHandle): TelegramResult<Unit>
    suspend fun createAccountContext(): TelegramResult<AccountHandle>
    suspend fun removeAccount(account: AccountHandle): TelegramResult<Unit>
}

interface ChatQuery {
    fun observeChats(account: AccountHandle, folderId: Int? = null): Flow<List<ChatItem>>
    fun observeChat(account: AccountHandle, chatRef: ChatRef): Flow<ChatItem?>
    fun observeFolders(account: AccountHandle): Flow<List<FolderRef>>
    suspend fun searchChats(account: AccountHandle, query: String): TelegramResult<List<ChatItem>>
}

interface ChatMutation {
    suspend fun pinChat(account: AccountHandle, chatRef: ChatRef, isPinned: Boolean): TelegramResult<Unit>
    suspend fun muteChat(account: AccountHandle, chatRef: ChatRef, isMuted: Boolean): TelegramResult<Unit>
    suspend fun markChatAsRead(account: AccountHandle, chatRef: ChatRef): TelegramResult<Unit>
}

interface MessageQuery {
    fun observeMessages(account: AccountHandle, chatRef: ChatRef): Flow<List<MessageItem>>
    suspend fun loadHistory(account: AccountHandle, chatRef: ChatRef, fromMessageRef: MessageRef?, limit: Int): TelegramResult<List<MessageItem>>
}

interface MessageComposer {
    suspend fun sendMessage(
        account: AccountHandle,
        chatRef: ChatRef,
        content: OutgoingContent,
        options: SendOptions = SendOptions()
    ): TelegramResult<MessageRef>

    suspend fun cancelSending(account: AccountHandle, chatRef: ChatRef, messageRef: MessageRef): TelegramResult<Unit>
}

interface MessageMutation {
    suspend fun deleteMessage(account: AccountHandle, chatRef: ChatRef, messageRef: MessageRef, forEveryone: Boolean): TelegramResult<Unit>
    suspend fun editMessageText(account: AccountHandle, chatRef: ChatRef, messageRef: MessageRef, newText: String): TelegramResult<Unit>
}

interface DraftService {
    fun observeDraft(account: AccountHandle, chatRef: ChatRef): Flow<Draft?>
    suspend fun saveDraft(account: AccountHandle, chatRef: ChatRef, draft: Draft): TelegramResult<Unit>
    suspend fun clearDraft(account: AccountHandle, chatRef: ChatRef): TelegramResult<Unit>
}
