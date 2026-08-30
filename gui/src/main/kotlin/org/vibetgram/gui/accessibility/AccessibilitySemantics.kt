package org.vibetgram.gui.accessibility

/**
 * Standardized semantic labels and TalkBack helpers for VibeTGram UI components.
 */
object AccessibilitySemantics {

    fun chatItemContentDescription(title: String, lastSnippet: String, unreadCount: Int, isPinned: Boolean): String {
        val parts = mutableListOf<String>()
        if (isPinned) parts.add("Pinned")
        parts.add(title)
        parts.add(lastSnippet)
        if (unreadCount > 0) {
            parts.add("$unreadCount unread ${if (unreadCount == 1) "message" else "messages"}")
        }
        return parts.joinToString(", ")
    }

    fun messageBubbleContentDescription(senderName: String, text: String, time: String, isOutgoing: Boolean, status: String): String {
        val direction = if (isOutgoing) "Sent by you" else "Received from $senderName"
        return "$direction: $text at $time. Status: $status"
    }

    fun composerSendContentDescription(hasText: Boolean): String {
        return if (hasText) "Send message" else "Record voice message"
    }
}
