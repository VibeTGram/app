package org.vibetgram.gui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vibetgram.gui.modui.ModUiNode
import org.vibetgram.gui.modui.ModUiValidator

class ModUiValidatorTest {

    @Test
    fun testValidModUiTree() {
        val tree = ModUiNode.Card(
            children = listOf(
                ModUiNode.Text("Welcome to VibeTGram Addon"),
                ModUiNode.Button(
                    label = "Open Details",
                    actionId = "open_details",
                    contentDescription = "Open details button"
                ),
                ModUiNode.Icon(
                    iconName = "ic_star",
                    contentDescription = "Favorite star icon"
                )
            )
        )

        val result = ModUiValidator.validateTree(tree)
        assertTrue(result.isValid)
    }

    @Test
    fun testIconMissingContentDescriptionFails() {
        val invalidIcon = ModUiNode.Icon(
            iconName = "ic_warning",
            contentDescription = ""
        )
        val result = ModUiValidator.validateTree(invalidIcon)
        assertFalse(result.isValid)
        assertTrue((result as ModUiValidator.ValidationResult.Invalid).reason.contains("Accessibility violation"))
    }

    @Test
    fun testTreeDepthLimitExceeded() {
        var node: ModUiNode = ModUiNode.Text("Deep leaf")
        repeat(7) {
            node = ModUiNode.Column(children = listOf(node))
        }

        val result = ModUiValidator.validateTree(node)
        assertFalse(result.isValid)
        assertTrue((result as ModUiValidator.ValidationResult.Invalid).reason.contains("tree depth"))
    }

    @Test
    fun testNodeQuotaLimitExceeded() {
        val nodes = (1..60).map { ModUiNode.Text("Node $it") }
        val column = ModUiNode.Column(children = nodes)

        val result = ModUiValidator.validateTree(column)
        assertFalse(result.isValid)
        assertTrue((result as ModUiValidator.ValidationResult.Invalid).reason.contains("node quota"))
    }
}
