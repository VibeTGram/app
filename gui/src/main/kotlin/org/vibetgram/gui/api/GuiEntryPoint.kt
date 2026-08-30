package org.vibetgram.gui.api

import kotlinx.coroutines.CoroutineScope
import org.vibetgram.gui.contract.GuiRoute
import org.vibetgram.gui.domain.AccountManager
import org.vibetgram.gui.domain.AuthorizationService
import org.vibetgram.gui.domain.ChatMutation
import org.vibetgram.gui.domain.ChatQuery
import org.vibetgram.gui.domain.DraftService
import org.vibetgram.gui.domain.MessageComposer
import org.vibetgram.gui.domain.MessageMutation
import org.vibetgram.gui.domain.MessageQuery
import org.vibetgram.gui.modui.ModUiNode
import org.vibetgram.gui.modui.ModUiSlot
import org.vibetgram.gui.modui.ModUiValidator
import org.vibetgram.gui.screens.AuthScreenRenderState
import org.vibetgram.gui.screens.AuthScreenRenderer
import org.vibetgram.gui.screens.ChatListScreenRenderState
import org.vibetgram.gui.screens.ChatListScreenRenderer
import org.vibetgram.gui.screens.ConversationScreenRenderState
import org.vibetgram.gui.screens.ConversationScreenRenderer
import org.vibetgram.gui.screens.RootScreenRenderState
import org.vibetgram.gui.screens.RootScreenRenderer
import org.vibetgram.gui.screens.TextComposerRenderState
import org.vibetgram.gui.screens.TextComposerRenderer
import org.vibetgram.gui.state.RootStateHolder

/**
 * Descriptor metadata for replaceable GUI implementations.
 * Normative reference: docs/architecture/system-architecture.md section 12 & ADR 0005.
 */
data class GuiDescriptor(
    val id: String,
    val name: String,
    val version: String,
    val semanticApiCompatibilityRange: String = "^1.0.0",
    val modUiContractVersion: String = "1.0.0",
    val supportsExpressiveTheming: Boolean = true,
    val supportsAdaptiveTwoPane: Boolean = true
)

data class GuiDependencies(
    val authService: AuthorizationService,
    val accountManager: AccountManager,
    val chatQuery: ChatQuery,
    val chatMutation: ChatMutation,
    val messageQuery: MessageQuery,
    val messageComposer: MessageComposer,
    val messageMutation: MessageMutation,
    val draftService: DraftService,
    val coroutineScope: CoroutineScope
)

interface GuiRenderContainer {
    fun setRootState(state: RootScreenRenderState)
    fun renderSlotNode(slot: ModUiSlot, node: ModUiNode)
}

interface GuiEventHandler {
    fun onNavigate(route: GuiRoute)
    fun onBack()
    fun onModUiAction(actionId: String, payload: Map<String, Any> = emptyMap())
}

/**
 * Replaceable GUI entry point contract.
 * Normative reference: docs/architecture/system-architecture.md section 12 & ADR 0005.
 */
interface GuiEntryPoint {
    val descriptor: GuiDescriptor

    fun createStateHolder(dependencies: GuiDependencies): RootStateHolder

    fun render(
        container: GuiRenderContainer,
        stateHolder: RootStateHolder,
        events: GuiEventHandler,
        slotNodes: Map<String, ModUiNode> = emptyMap()
    ): RootScreenRenderState

    fun validateAndRenderSlot(slot: ModUiSlot, node: ModUiNode): ModUiValidator.ValidationResult
}
