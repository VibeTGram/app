package org.vibetgram.gui.adaptive

/**
 * Adaptive Scaffold presentation state holding layout dimensions and pane visibility.
 */
data class AdaptiveScaffoldState(
    val windowSizeClass: WindowSizeClass,
    val layoutConfig: AdaptiveLayoutConfig,
    val isDetailOpen: Boolean = false
) {
    val showMaster: Boolean
        get() = layoutConfig.contentLayoutType == ContentLayoutType.TWO_PANE_MASTER_DETAIL || !isDetailOpen

    val showDetail: Boolean
        get() = layoutConfig.contentLayoutType == ContentLayoutType.TWO_PANE_MASTER_DETAIL || isDetailOpen
}
