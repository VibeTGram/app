package org.vibetgram.gui.screens

import org.vibetgram.gui.contract.GuiRoute
import org.vibetgram.gui.modui.ModUiNode
import org.vibetgram.gui.state.RootUiState

data class RootScreenRenderState(
    val currentRoute: GuiRoute,
    val canGoBack: Boolean,
    val authState: AuthScreenRenderState?,
    val chatListState: ChatListScreenRenderState?,
    val conversationState: ConversationScreenRenderState?,
    val composerState: TextComposerRenderState?,
    val isTwoPaneMasterDetail: Boolean
)

object RootScreenRenderer {

    fun prepareRenderState(
        rootState: RootUiState,
        authRender: AuthScreenRenderState?,
        chatListRender: ChatListScreenRenderState?,
        conversationRender: ConversationScreenRenderState?,
        composerRender: TextComposerRenderState?
    ): RootScreenRenderState {
        return RootScreenRenderState(
            currentRoute = rootState.navigationState.currentRoute,
            canGoBack = rootState.navigationState.canGoBack,
            authState = authRender,
            chatListState = chatListRender,
            conversationState = conversationRender,
            composerState = composerRender,
            isTwoPaneMasterDetail = rootState.layoutConfig.contentLayoutType ==
                    org.vibetgram.gui.adaptive.ContentLayoutType.TWO_PANE_MASTER_DETAIL
        )
    }
}
