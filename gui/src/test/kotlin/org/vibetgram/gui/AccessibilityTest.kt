package org.vibetgram.gui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vibetgram.gui.accessibility.A11yValidator
import org.vibetgram.gui.accessibility.AccessibilityConfig
import org.vibetgram.gui.accessibility.AccessibleInputNavigation
import org.vibetgram.gui.accessibility.NavigationAction
import org.vibetgram.gui.accessibility.NavigationKey
import org.vibetgram.gui.theme.ExpressiveColor

class AccessibilityTest {

    @Test
    fun testContrastCalculation() {
        val black = ExpressiveColor.fromHex("#000000")
        val white = ExpressiveColor.fromHex("#FFFFFF")

        val ratio = A11yValidator.calculateContrastRatio(black, white)
        assertTrue(ratio >= 21.0)
        assertTrue(A11yValidator.isContrastCompliant(black, white))

        val lightGray = ExpressiveColor.fromHex("#CCCCCC")
        val badRatio = A11yValidator.calculateContrastRatio(lightGray, white)
        assertFalse(A11yValidator.isContrastCompliant(lightGray, white))
    }

    @Test
    fun testTouchTargetSizeConstraint() {
        assertTrue(A11yValidator.validateTouchTarget(48f, 48f))
        assertTrue(A11yValidator.validateTouchTarget(56f, 48f))
        assertFalse(A11yValidator.validateTouchTarget(40f, 48f))
        assertFalse(A11yValidator.validateTouchTarget(48f, 32f))
    }

    @Test
    fun testAccessibilityConfigBounds() {
        val validConfig = AccessibilityConfig(fontScale = 1.5f, minimumTouchTargetDp = 48f)
        assertTrue(validConfig.fontScale == 1.5f)

        var thrown = false
        try {
            AccessibilityConfig(fontScale = 4.0f)
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun testKeyboardDpadAndSwitchNavigationMapToStableActions() {
        assertEquals(NavigationAction.MOVE_PREVIOUS, AccessibleInputNavigation.actionFor(NavigationKey.UP))
        assertEquals(NavigationAction.MOVE_NEXT, AccessibleInputNavigation.actionFor(NavigationKey.DOWN))
        assertEquals(NavigationAction.ACTIVATE, AccessibleInputNavigation.actionFor(NavigationKey.ENTER))
        assertEquals(NavigationAction.BACK, AccessibleInputNavigation.actionFor(NavigationKey.BACK))
    }
}
