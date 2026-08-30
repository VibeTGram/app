package org.vibetgram.gui.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.vibetgram.gui.domain.AccountHandle
import org.vibetgram.gui.domain.AccountManager
import org.vibetgram.gui.domain.ChatItem
import org.vibetgram.gui.domain.ChatMutation
import org.vibetgram.gui.domain.ChatQuery
import org.vibetgram.gui.domain.ChatRef
import org.vibetgram.gui.domain.ConnectionState
import org.vibetgram.gui.domain.FolderRef

data class ChatListUiState(
    val activeAccount: AccountHandle? = null,
    val connectionState: ConnectionState = ConnectionState.Ready,
    val selectedFolderId: Int? = null,
    val folders: List<FolderRef> = emptyList(),
    val chats: List<ChatItem> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val searchResults: List<ChatItem> = emptyList(),
    val selectedChatRef: ChatRef? = null,
    val isLoading: Boolean = false
)

class ChatListStateHolder(
    private val accountManager: AccountManager,
    private val chatQuery: ChatQuery,
    private val chatMutation: ChatMutation,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    private var chatsObserverJob: Job? = null
    private var foldersObserverJob: Job? = null
    private var connectionObserverJob: Job? = null
    private var searchJob: Job? = null

    init {
        scope.launch {
            accountManager.observeActiveAccount().collect { account ->
                _uiState.value = _uiState.value.copy(activeAccount = account)
                if (account != null) {
                    bindAccount(account)
                } else {
                    unbindAccount()
                }
            }
        }
    }

    private fun bindAccount(account: AccountHandle) {
        connectionObserverJob?.cancel()
        connectionObserverJob = scope.launch {
            accountManager.observeConnectionState(account).collect { conn ->
                _uiState.value = _uiState.value.copy(connectionState = conn)
            }
        }

        foldersObserverJob?.cancel()
        foldersObserverJob = scope.launch {
            chatQuery.observeFolders(account).collect { folderList ->
                _uiState.value = _uiState.value.copy(folders = folderList)
            }
        }

        observeChatsForCurrentFolder(account, _uiState.value.selectedFolderId)
    }

    private fun observeChatsForCurrentFolder(account: AccountHandle, folderId: Int?) {
        chatsObserverJob?.cancel()
        chatsObserverJob = scope.launch {
            chatQuery.observeChats(account, folderId).collect { chatList ->
                _uiState.value = _uiState.value.copy(chats = chatList)
            }
        }
    }

    private fun unbindAccount() {
        connectionObserverJob?.cancel()
        foldersObserverJob?.cancel()
        chatsObserverJob?.cancel()
        connectionObserverJob = null
        foldersObserverJob = null
        chatsObserverJob = null
        _uiState.value = _uiState.value.copy(
            connectionState = ConnectionState.WaitingForNetwork,
            folders = emptyList(),
            chats = emptyList(),
            searchResults = emptyList(),
            selectedChatRef = null,
            isSearchActive = false,
            searchQuery = ""
        )
    }

    fun onFolderSelected(folderId: Int?) {
        _uiState.value = _uiState.value.copy(selectedFolderId = folderId)
        val account = _uiState.value.activeAccount ?: return
        observeChatsForCurrentFolder(account, folderId)
    }

    fun onSearchQueryChanged(query: String) {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            isSearchActive = query.isNotBlank()
        )
        if (query.isNotBlank()) {
            val account = _uiState.value.activeAccount ?: return
            searchJob = scope.launch {
                val res = chatQuery.searchChats(account, query)
                if (res.isSuccess && _uiState.value.searchQuery == query) {
                    _uiState.value = _uiState.value.copy(searchResults = res.getOrThrow())
                }
            }
        } else {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
        }
    }

    fun onChatSelected(chatRef: ChatRef) {
        _uiState.value = _uiState.value.copy(selectedChatRef = chatRef)
    }

    fun onPinChat(chatRef: ChatRef, isPinned: Boolean) {
        val account = _uiState.value.activeAccount ?: return
        scope.launch {
            chatMutation.pinChat(account, chatRef, isPinned)
        }
    }

    fun onMuteChat(chatRef: ChatRef, isMuted: Boolean) {
        val account = _uiState.value.activeAccount ?: return
        scope.launch {
            chatMutation.muteChat(account, chatRef, isMuted)
        }
    }

    fun onMarkRead(chatRef: ChatRef) {
        val account = _uiState.value.activeAccount ?: return
        scope.launch {
            chatMutation.markChatAsRead(account, chatRef)
        }
    }
}
