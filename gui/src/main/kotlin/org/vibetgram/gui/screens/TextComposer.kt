package org.vibetgram.gui.screens

import org.vibetgram.gui.accessibility.AccessibilitySemantics
import org.vibetgram.gui.modui.ModUiNode
import org.vibetgram.gui.state.TextComposerUiState
import org.vibetgram.gui.theme.ResolvedTheme

data class TextComposerRenderState(
    val text: String,
    val isSending: Boolean,
    val canSend: Boolean,
    val sendButtonContentDescription: String,
    val errorMessage: String?,
    val slotNodes: Map<String, ModUiNode> = emptyMap()
)

object TextComposerRenderer {

    fun prepareRenderState(
        state: TextComposerUiState,
        theme: ResolvedTheme,
        slotNodes: Map<String, ModUiNode> = emptyMap()
    ): TextComposerRenderState {
        return TextComposerRenderState(
            text = state.inputText,
            isSending = state.isSending,
            canSend = state.canSend,
            sendButtonContentDescription = AccessibilitySemantics.composerSendContentDescription(state.canSend),
            errorMessage = state.errorMessage,
            slotNodes = slotNodes
        )
    }
}
