package org.vibetgram.gui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vibetgram.gui.theme.AccessibilityOverrides
import org.vibetgram.gui.theme.BuiltInPalette
import org.vibetgram.gui.theme.ExpressiveColor
import org.vibetgram.gui.theme.ResourcePackTokens
import org.vibetgram.gui.theme.ThemeResolver

class ThemeResolverTest {

    @Test
    fun testBaseResolutionOrder() {
        val lightTheme = ThemeResolver.resolve(isDark = false)
        val darkTheme = ThemeResolver.resolve(isDark = true)

        assertNotEquals(lightTheme.colorScheme.background.argb, darkTheme.colorScheme.background.argb)
        assertEquals(150L, lightTheme.motion.durationShortMs)
    }

    @Test
    fun testResourcePackPriorityPrecedence() {
        val packLow = ResourcePackTokens(
            packId = "pack.low",
            priority = 10,
            primaryColorHex = "#112233"
        )
        val packHigh = ResourcePackTokens(
            packId = "pack.high",
            priority = 20,
            primaryColorHex = "#AABBCC"
        )

        val resolved = ThemeResolver.resolve(
            isDark = false,
            resourcePacks = listOf(packHigh, packLow)
        )

        // Highest priority pack (20) must win
        assertEquals(ExpressiveColor.fromHex("#AABBCC"), resolved.colorScheme.primary)
    }

    @Test
    fun testTerminalAccessibilityOverrides() {
        val highContrast = ThemeResolver.resolve(
            isDark = false,
            palette = BuiltInPalette.SUNSET_AMBER,
            accessibility = AccessibilityOverrides(isHighContrastEnabled = true, isReducedMotionEnabled = true)
        )

        // High contrast overrides surface/background to absolute white/black
        assertEquals(ExpressiveColor.fromHex("#FFFFFF"), highContrast.colorScheme.surface)
        assertEquals(ExpressiveColor.fromHex("#000000"), highContrast.colorScheme.onSurface)

        // Reduced motion zeroes durations
        assertEquals(0L, highContrast.motion.durationShortMs)
        assertEquals(0L, highContrast.motion.durationMediumMs)
    }

    @Test
    fun testFontScaleIsAppliedToTypographyTokens() {
        val theme = ThemeResolver.resolve(
            isDark = false,
            accessibility = AccessibilityOverrides(fontScale = 1.5f)
        )

        assertEquals(24f, theme.typography.bodyLarge.fontSizeSp, 0.001f)
        assertEquals(36f, theme.typography.bodyLarge.lineHeightSp, 0.001f)
    }
}
