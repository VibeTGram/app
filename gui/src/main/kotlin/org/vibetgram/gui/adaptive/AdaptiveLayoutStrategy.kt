package org.vibetgram.gui.adaptive

enum class NavigationType {
    BOTTOM_NAVIGATION_BAR,
    NAVIGATION_RAIL,
    PERMANENT_NAVIGATION_DRAWER
}

enum class ContentLayoutType {
    SINGLE_PANE,
    TWO_PANE_MASTER_DETAIL
}

data class AdaptiveLayoutConfig(
    val navigationType: NavigationType,
    val contentLayoutType: ContentLayoutType,
    val masterPaneWeight: Float = 0.4f,
    val detailPaneWeight: Float = 0.6f
)

object AdaptiveLayoutStrategy {

    fun determineConfig(windowSizeClass: WindowSizeClass): AdaptiveLayoutConfig {
        return when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.COMPACT -> AdaptiveLayoutConfig(
                navigationType = NavigationType.BOTTOM_NAVIGATION_BAR,
                contentLayoutType = ContentLayoutType.SINGLE_PANE
            )
            WindowWidthSizeClass.MEDIUM -> AdaptiveLayoutConfig(
                navigationType = NavigationType.NAVIGATION_RAIL,
                contentLayoutType = ContentLayoutType.TWO_PANE_MASTER_DETAIL,
                masterPaneWeight = 0.45f,
                detailPaneWeight = 0.55f
            )
            WindowWidthSizeClass.EXPANDED -> AdaptiveLayoutConfig(
                navigationType = NavigationType.PERMANENT_NAVIGATION_DRAWER,
                contentLayoutType = ContentLayoutType.TWO_PANE_MASTER_DETAIL,
                masterPaneWeight = 0.35f,
                detailPaneWeight = 0.65f
            )
        }
    }
}
