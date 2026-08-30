package org.vibetgram.gui.screens

import org.vibetgram.gui.accessibility.AccessibilitySemantics
import org.vibetgram.gui.domain.ChatItem
import org.vibetgram.gui.domain.ConnectionState
import org.vibetgram.gui.domain.FolderRef
import org.vibetgram.gui.modui.ModUiNode
import org.vibetgram.gui.modui.ModUiSlot
import org.vibetgram.gui.state.ChatListUiState
import org.vibetgram.gui.theme.ResolvedTheme

data class ChatListScreenRenderState(
    val activeFolderId: Int?,
    val folderTabs: List<FolderRef>,
    val chatItems: List<ChatItem>,
    val searchQuery: String,
    val isSearchOpen: Boolean,
    val isLoading: Boolean,
    val connectionStatusBanner: String?,
    val slotNodes: Map<String, ModUiNode> = emptyMap(),
    val accessibilityDescriptions: List<String>
)

object ChatListScreenRenderer {

    fun prepareRenderState(
        state: ChatListUiState,
        theme: ResolvedTheme,
        slotNodes: Map<String, ModUiNode> = emptyMap()
    ): ChatListScreenRenderState {
        val items = if (state.isSearchActive) state.searchResults else state.chats
        val a11yList = items.map {
            AccessibilitySemantics.chatItemContentDescription(
                title = it.title,
                lastSnippet = it.lastMessageSnippet,
                unreadCount = it.unreadCount,
                isPinned = it.isPinned
            )
        }

        return ChatListScreenRenderState(
            activeFolderId = state.selectedFolderId,
            folderTabs = state.folders,
            chatItems = items,
            searchQuery = state.searchQuery,
            isSearchOpen = state.isSearchActive,
            isLoading = state.isLoading,
            connectionStatusBanner = when (state.connectionState) {
                ConnectionState.Ready -> null
                ConnectionState.Connecting -> "Connecting…"
                ConnectionState.ConnectingToProxy -> "Connecting through proxy…"
                ConnectionState.Updating -> "Updating chats…"
                ConnectionState.WaitingForNetwork -> "Waiting for network"
            },
            slotNodes = slotNodes,
            accessibilityDescriptions = a11yList
        )
    }
}
