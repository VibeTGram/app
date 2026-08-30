package org.vibetgram.gui.modui

/**
 * Declarative UI tree nodes returned by sandboxed Luau addons.
 * Normative reference: docs/architecture/adr/0005-replaceable-gui.md
 */
sealed interface ModUiNode {

    enum class TextStyle {
        HEADLINE, TITLE, BODY, LABEL, CAPTION
    }

    enum class ButtonVariant {
        FILLED, TONAL, OUTLINED, TEXT
    }

    enum class BadgeVariant {
        DEFAULT, PRIMARY, SUCCESS, WARNING, ERROR
    }

    data class Text(
        val text: String,
        val style: TextStyle = TextStyle.BODY,
        val contentDescription: String? = null
    ) : ModUiNode

    data class Button(
        val label: String,
        val actionId: String,
        val variant: ButtonVariant = ButtonVariant.FILLED,
        val contentDescription: String? = null
    ) : ModUiNode

    data class Icon(
        val iconName: String,
        val contentDescription: String
    ) : ModUiNode

    data class Badge(
        val text: String,
        val variant: BadgeVariant = BadgeVariant.DEFAULT
    ) : ModUiNode

    data class Card(
        val children: List<ModUiNode>,
        val contentDescription: String? = null
    ) : ModUiNode

    data class Column(
        val children: List<ModUiNode>,
        val spacingDp: Int = 8
    ) : ModUiNode

    data class Row(
        val children: List<ModUiNode>,
        val spacingDp: Int = 8
    ) : ModUiNode

    data class Input(
        val placeholder: String,
        val initialValue: String = "",
        val actionId: String,
        val contentDescription: String? = null
    ) : ModUiNode
}
