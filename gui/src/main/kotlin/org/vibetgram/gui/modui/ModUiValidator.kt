package org.vibetgram.gui.modui

/**
 * Centralized Mod UI validator enforcing node limits, recursion depth, and accessibility constraints.
 * Normative reference: ADR 0005 & docs/modding/capability-matrix.md
 */
object ModUiValidator {

    const val MAX_DEPTH = 6
    const val MAX_NODES = 50
    const val MAX_TEXT_LENGTH = 2048

    sealed interface ValidationResult {
        data object Valid : ValidationResult
        data class Invalid(val reason: String) : ValidationResult

        val isValid: Boolean get() = this is Valid
    }

    fun validateTree(root: ModUiNode): ValidationResult {
        var totalNodes = 0

        fun checkNode(node: ModUiNode, depth: Int): ValidationResult {
            totalNodes++
            if (totalNodes > MAX_NODES) {
                return ValidationResult.Invalid("Exceeded maximum node quota ($MAX_NODES)")
            }
            if (depth > MAX_DEPTH) {
                return ValidationResult.Invalid("Exceeded maximum tree depth ($MAX_DEPTH)")
            }

            return when (node) {
                is ModUiNode.Text -> {
                    if (node.text.length > MAX_TEXT_LENGTH) {
                        ValidationResult.Invalid("Text length ${node.text.length} exceeds maximum $MAX_TEXT_LENGTH")
                    } else {
                        ValidationResult.Valid
                    }
                }
                is ModUiNode.Button -> {
                    if (node.label.isBlank()) {
                        ValidationResult.Invalid("Button label cannot be blank")
                    } else if (node.actionId.isBlank()) {
                        ValidationResult.Invalid("Button actionId cannot be blank")
                    } else {
                        ValidationResult.Valid
                    }
                }
                is ModUiNode.Icon -> {
                    if (node.contentDescription.isBlank()) {
                        ValidationResult.Invalid("Accessibility violation: Icon node requires non-blank contentDescription")
                    } else if (node.iconName.isBlank()) {
                        ValidationResult.Invalid("Icon name cannot be blank")
                    } else {
                        ValidationResult.Valid
                    }
                }
                is ModUiNode.Badge -> {
                    if (node.text.isBlank()) {
                        ValidationResult.Invalid("Badge text cannot be blank")
                    } else {
                        ValidationResult.Valid
                    }
                }
                is ModUiNode.Input -> {
                    if (node.actionId.isBlank()) {
                        ValidationResult.Invalid("Input actionId cannot be blank")
                    } else {
                        ValidationResult.Valid
                    }
                }
                is ModUiNode.Card -> {
                    for (child in node.children) {
                        val res = checkNode(child, depth + 1)
                        if (!res.isValid) return res
                    }
                    ValidationResult.Valid
                }
                is ModUiNode.Column -> {
                    for (child in node.children) {
                        val res = checkNode(child, depth + 1)
                        if (!res.isValid) return res
                    }
                    ValidationResult.Valid
                }
                is ModUiNode.Row -> {
                    for (child in node.children) {
                        val res = checkNode(child, depth + 1)
                        if (!res.isValid) return res
                    }
                    ValidationResult.Valid
                }
            }
        }

        return checkNode(root, 1)
    }
}
