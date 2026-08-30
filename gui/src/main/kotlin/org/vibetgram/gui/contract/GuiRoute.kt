package org.vibetgram.gui.contract

import org.vibetgram.gui.domain.ChatRef
import org.vibetgram.gui.domain.MessageRef
import org.vibetgram.gui.domain.UserRef

/**
 * Typed routes for VibeTGram presentation navigation.
 * Normative reference: docs/architecture/system-architecture.md section 12 & ADR 0005.
 */
sealed interface GuiRoute {

    sealed interface Auth : GuiRoute {
        data object PhoneEntry : Auth
        data class CodeVerify(val phone: String, val codeLength: Int = 5) : Auth
        data class Password2Fa(val hint: String?, val hasRecoveryEmail: Boolean = true) : Auth
        data class QrCode(val link: String) : Auth
        data class TermsConfirmation(val terms: String?) : Auth
    }

    data class ChatList(
        val folderId: Int? = null,
        val initialSearchQuery: String? = null
    ) : GuiRoute

    data class Conversation(
        val chatRef: ChatRef,
        val initialMessageRef: MessageRef? = null
    ) : GuiRoute

    data class Profile(
        val userRef: UserRef? = null,
        val chatRef: ChatRef? = null
    ) : GuiRoute

    data class Settings(
        val section: SettingsSection = SettingsSection.ROOT
    ) : GuiRoute

    data class ModScreen(
        val addonId: String,
        val screenId: String,
        val title: String
    ) : GuiRoute
}

enum class SettingsSection {
    ROOT,
    MODIFICATIONS,
    APPEARANCE,
    PRIVACY_SECURITY,
    STORAGE_DATA,
    NOTIFICATIONS,
    CHAT_SETTINGS
}
