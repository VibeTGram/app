package org.vibetgram.gui.screens

import org.vibetgram.gui.accessibility.AccessibilitySemantics
import org.vibetgram.gui.domain.ChatItem
import org.vibetgram.gui.domain.MessageItem
import org.vibetgram.gui.modui.ModUiNode
import org.vibetgram.gui.state.ConversationUiState
import org.vibetgram.gui.theme.ResolvedTheme

data class ConversationScreenRenderState(
    val chatInfo: ChatItem?,
    val messageList: List<MessageItem>,
    val replyTarget: MessageItem?,
    val editingMessage: MessageItem?,
    val isTyping: Boolean,
    val isLoading: Boolean,
    val slotNodes: Map<String, ModUiNode> = emptyMap(),
    val messageAccessibilityDescriptions: List<String>
)

object ConversationScreenRenderer {

    fun prepareRenderState(
        state: ConversationUiState,
        theme: ResolvedTheme,
        slotNodes: Map<String, ModUiNode> = emptyMap()
    ): ConversationScreenRenderState {
        val a11yDescs = state.messages.map { msg ->
            AccessibilitySemantics.messageBubbleContentDescription(
                senderName = msg.senderName,
                text = msg.text,
                time = "${msg.timestampMs}",
                isOutgoing = msg.isOutgoing,
                status = msg.deliveryStatus.name
            )
        }

        return ConversationScreenRenderState(
            chatInfo = state.chatInfo,
            messageList = state.messages,
            replyTarget = state.replyTarget,
            editingMessage = state.editingMessage,
            isTyping = state.isPeerTyping,
            isLoading = state.isLoading,
            slotNodes = slotNodes,
            messageAccessibilityDescriptions = a11yDescs
        )
    }
}
