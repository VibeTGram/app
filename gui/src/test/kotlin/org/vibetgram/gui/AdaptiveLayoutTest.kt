package org.vibetgram.gui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.vibetgram.gui.adaptive.AdaptiveLayoutStrategy
import org.vibetgram.gui.adaptive.ContentLayoutType
import org.vibetgram.gui.adaptive.NavigationType
import org.vibetgram.gui.adaptive.WindowFormFactor
import org.vibetgram.gui.adaptive.WindowHeightSizeClass
import org.vibetgram.gui.adaptive.WindowSizeClass
import org.vibetgram.gui.adaptive.WindowWidthSizeClass

class AdaptiveLayoutTest {

    @Test
    fun testPhonePortraitCompact() {
        val wsc = WindowSizeClass.compute(widthDp = 390f, heightDp = 844f)
        assertEquals(WindowWidthSizeClass.COMPACT, wsc.widthSizeClass)
        assertEquals(WindowHeightSizeClass.MEDIUM, wsc.heightSizeClass)

        val config = AdaptiveLayoutStrategy.determineConfig(wsc)
        assertEquals(NavigationType.BOTTOM_NAVIGATION_BAR, config.navigationType)
        assertEquals(ContentLayoutType.SINGLE_PANE, config.contentLayoutType)
    }

    @Test
    fun testTabletPortraitMedium() {
        val wsc = WindowSizeClass.compute(widthDp = 768f, heightDp = 1024f)
        assertEquals(WindowWidthSizeClass.MEDIUM, wsc.widthSizeClass)
        assertEquals(WindowHeightSizeClass.EXPANDED, wsc.heightSizeClass)

        val config = AdaptiveLayoutStrategy.determineConfig(wsc)
        assertEquals(NavigationType.NAVIGATION_RAIL, config.navigationType)
        assertEquals(ContentLayoutType.TWO_PANE_MASTER_DETAIL, config.contentLayoutType)
    }

    @Test
    fun testDesktopExpanded() {
        val wsc = WindowSizeClass.compute(widthDp = 1200f, heightDp = 800f)
        assertEquals(WindowWidthSizeClass.EXPANDED, wsc.widthSizeClass)
        assertEquals(WindowHeightSizeClass.MEDIUM, wsc.heightSizeClass)

        val config = AdaptiveLayoutStrategy.determineConfig(wsc)
        assertEquals(NavigationType.PERMANENT_NAVIGATION_DRAWER, config.navigationType)
        assertEquals(ContentLayoutType.TWO_PANE_MASTER_DETAIL, config.contentLayoutType)
    }

    @Test
    fun testFoldableAndChromeOsFormFactorsArePreserved() {
        assertEquals(
            WindowFormFactor.FOLDABLE,
            WindowSizeClass.compute(720f, 800f, isFoldable = true).formFactor
        )
        assertEquals(
            WindowFormFactor.CHROMEOS,
            WindowSizeClass.compute(1200f, 800f, isChromeOs = true).formFactor
        )
    }
}
