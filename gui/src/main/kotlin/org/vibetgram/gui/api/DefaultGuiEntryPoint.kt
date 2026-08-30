package org.vibetgram.gui.api

import org.vibetgram.gui.contract.GuiRoute
import org.vibetgram.gui.modui.ModUiNode
import org.vibetgram.gui.modui.ModUiSlot
import org.vibetgram.gui.modui.ModUiValidator
import org.vibetgram.gui.screens.AuthScreenRenderer
import org.vibetgram.gui.screens.ChatListScreenRenderer
import org.vibetgram.gui.screens.ConversationScreenRenderer
import org.vibetgram.gui.screens.RootScreenRenderState
import org.vibetgram.gui.screens.RootScreenRenderer
import org.vibetgram.gui.screens.TextComposerRenderer
import org.vibetgram.gui.state.RootStateHolder

/**
 * Default Material 3 Expressive GUI entrypoint implementation for VibeTGram.
 */
class DefaultGuiEntryPoint : GuiEntryPoint {

    override val descriptor: GuiDescriptor = GuiDescriptor(
        id = "org.vibetgram.gui.default",
        name = "Material 3 Expressive GUI",
        version = "1.0.0",
        semanticApiCompatibilityRange = "^1.0.0",
        modUiContractVersion = "1.0.0",
        supportsExpressiveTheming = true,
        supportsAdaptiveTwoPane = true
    )

    override fun createStateHolder(dependencies: GuiDependencies): RootStateHolder {
        return RootStateHolder(
            authService = dependencies.authService,
            accountManager = dependencies.accountManager,
            chatQuery = dependencies.chatQuery,
            chatMutation = dependencies.chatMutation,
            messageQuery = dependencies.messageQuery,
            messageComposer = dependencies.messageComposer,
            messageMutation = dependencies.messageMutation,
            draftService = dependencies.draftService,
            scope = dependencies.coroutineScope
        )
    }

    override fun render(
        container: GuiRenderContainer,
        stateHolder: RootStateHolder,
        events: GuiEventHandler,
        slotNodes: Map<String, ModUiNode>
    ): RootScreenRenderState {
        val rootUiState = stateHolder.uiState.value
        val theme = rootUiState.theme
        val validSlotNodes = slotNodes.filterValues {
            ModUiValidator.validateTree(it).isValid
        }

        val authRender = if (rootUiState.navigationState.currentRoute is GuiRoute.Auth) {
            AuthScreenRenderer.prepareRenderState(stateHolder.authStateHolder.uiState.value, theme)
        } else null

        val chatListRender = if (rootUiState.navigationState.currentRoute is GuiRoute.ChatList ||
            rootUiState.layoutConfig.contentLayoutType == org.vibetgram.gui.adaptive.ContentLayoutType.TWO_PANE_MASTER_DETAIL
        ) {
            ChatListScreenRenderer.prepareRenderState(stateHolder.chatListStateHolder.uiState.value, theme, validSlotNodes)
        } else null

        val conversationRender = if (rootUiState.navigationState.currentRoute is GuiRoute.Conversation ||
            (rootUiState.layoutConfig.contentLayoutType == org.vibetgram.gui.adaptive.ContentLayoutType.TWO_PANE_MASTER_DETAIL &&
                    stateHolder.conversationStateHolder.uiState.value.chatRef != null)
        ) {
            ConversationScreenRenderer.prepareRenderState(stateHolder.conversationStateHolder.uiState.value, theme, validSlotNodes)
        } else null

        val composerRender = if (conversationRender != null) {
            TextComposerRenderer.prepareRenderState(stateHolder.composerStateHolder.uiState.value, theme, validSlotNodes)
        } else null

        val renderState = RootScreenRenderer.prepareRenderState(
            rootState = rootUiState,
            authRender = authRender,
            chatListRender = chatListRender,
            conversationRender = conversationRender,
            composerRender = composerRender
        )

        container.setRootState(renderState)
        return renderState
    }

    override fun validateAndRenderSlot(slot: ModUiSlot, node: ModUiNode): ModUiValidator.ValidationResult {
        return ModUiValidator.validateTree(node)
    }
}
