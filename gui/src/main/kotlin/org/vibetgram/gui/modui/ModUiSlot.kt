package org.vibetgram.gui.modui

import org.vibetgram.gui.domain.ChatRef
import org.vibetgram.gui.domain.MessageRef
import org.vibetgram.gui.domain.UserRef

/**
 * Extension slot identifiers for declarative Mod UI.
 * Normative reference: docs/architecture/system-architecture.md section 12 & ADR 0005.
 */
sealed interface ModUiSlot {
    val slotId: String

    data class ChatListBadge(val chatRef: ChatRef) : ModUiSlot {
        override val slotId: String = "chat_list.badge.${chatRef.id}"
    }

    data class ChatListHeader(val folderId: Int?) : ModUiSlot {
        override val slotId: String = "chat_list.header.${folderId ?: 0}"
    }

    data class ChatListItemAction(val chatRef: ChatRef) : ModUiSlot {
        override val slotId: String = "chat_list.item_action.${chatRef.id}"
    }

    data class ConversationToolbarAction(val chatRef: ChatRef) : ModUiSlot {
        override val slotId: String = "conversation.toolbar_action.${chatRef.id}"
    }

    data class ComposerAction(val chatRef: ChatRef) : ModUiSlot {
        override val slotId: String = "composer.action.${chatRef.id}"
    }

    data class MessageBubbleAction(val chatRef: ChatRef, val messageRef: MessageRef) : ModUiSlot {
        override val slotId: String = "message_bubble.action.${chatRef.id}.${messageRef.id}"
    }

    data class ProfileSection(val userRef: UserRef) : ModUiSlot {
        override val slotId: String = "profile.section.${userRef.id}"
    }

    data class SettingsSection(val sectionKey: String) : ModUiSlot {
        override val slotId: String = "settings.section.$sectionKey"
    }

    data class DeclarativeScreen(val addonId: String, val screenId: String) : ModUiSlot {
        override val slotId: String = "screen.$addonId.$screenId"
    }
}
