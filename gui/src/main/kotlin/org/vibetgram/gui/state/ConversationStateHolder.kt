package org.vibetgram.gui.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.vibetgram.gui.domain.AccountHandle
import org.vibetgram.gui.domain.ChatItem
import org.vibetgram.gui.domain.ChatQuery
import org.vibetgram.gui.domain.ChatRef
import org.vibetgram.gui.domain.MessageItem
import org.vibetgram.gui.domain.MessageMutation
import org.vibetgram.gui.domain.MessageQuery
import org.vibetgram.gui.domain.MessageRef

data class ConversationUiState(
    val chatRef: ChatRef? = null,
    val chatInfo: ChatItem? = null,
    val messages: List<MessageItem> = emptyList(),
    val replyTarget: MessageItem? = null,
    val editingMessage: MessageItem? = null,
    val isPeerTyping: Boolean = false,
    val isLoading: Boolean = false
)

class ConversationStateHolder(
    private val chatQuery: ChatQuery,
    private val messageQuery: MessageQuery,
    private val messageMutation: MessageMutation,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private var activeAccount: AccountHandle? = null
    private var chatObserverJob: Job? = null
    private var messagesObserverJob: Job? = null

    fun setAccount(account: AccountHandle) {
        activeAccount = account
        _uiState.value.chatRef?.let { openChat(it) }
    }

    fun openChat(chatRef: ChatRef) {
        _uiState.value = _uiState.value.copy(
            chatRef = chatRef,
            replyTarget = null,
            editingMessage = null,
            isLoading = true
        )
        val account = activeAccount ?: return

        chatObserverJob?.cancel()
        chatObserverJob = scope.launch {
            chatQuery.observeChat(account, chatRef).collect { info ->
                _uiState.value = _uiState.value.copy(chatInfo = info)
            }
        }

        messagesObserverJob?.cancel()
        messagesObserverJob = scope.launch {
            messageQuery.observeMessages(account, chatRef).collect { msgList ->
                _uiState.value = _uiState.value.copy(
                    messages = msgList,
                    isLoading = false
                )
            }
        }
    }

    fun setReplyTarget(message: MessageItem?) {
        _uiState.value = _uiState.value.copy(replyTarget = message, editingMessage = null)
    }

    fun setEditingMessage(message: MessageItem?) {
        _uiState.value = _uiState.value.copy(editingMessage = message, replyTarget = null)
    }

    fun deleteMessage(messageRef: MessageRef, forEveryone: Boolean = false) {
        val account = activeAccount ?: return
        val chat = _uiState.value.chatRef ?: return
        scope.launch {
            messageMutation.deleteMessage(account, chat, messageRef, forEveryone)
        }
    }
}
