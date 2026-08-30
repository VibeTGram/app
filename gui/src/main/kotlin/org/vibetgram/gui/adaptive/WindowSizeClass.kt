package org.vibetgram.gui.adaptive

/**
 * Window Size Classes for responsive and adaptive layout handling.
 * Normative reference: docs/architecture/system-architecture.md section 12.
 */
enum class WindowWidthSizeClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

enum class WindowHeightSizeClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

enum class WindowFormFactor {
    PHONE,
    TABLET,
    FOLDABLE,
    CHROMEOS
}

data class WindowSizeClass(
    val widthSizeClass: WindowWidthSizeClass,
    val heightSizeClass: WindowHeightSizeClass,
    val formFactor: WindowFormFactor = WindowFormFactor.PHONE
) {
    companion object {
        fun compute(
            widthDp: Float,
            heightDp: Float,
            isFoldable: Boolean = false,
            isChromeOs: Boolean = false
        ): WindowSizeClass {
            val widthClass = when {
                widthDp < 600f -> WindowWidthSizeClass.COMPACT
                widthDp < 840f -> WindowWidthSizeClass.MEDIUM
                else -> WindowWidthSizeClass.EXPANDED
            }
            val heightClass = when {
                heightDp < 480f -> WindowHeightSizeClass.COMPACT
                heightDp < 900f -> WindowHeightSizeClass.MEDIUM
                else -> WindowHeightSizeClass.EXPANDED
            }
            val formFactor = when {
                isFoldable -> WindowFormFactor.FOLDABLE
                isChromeOs -> WindowFormFactor.CHROMEOS
                widthClass == WindowWidthSizeClass.COMPACT -> WindowFormFactor.PHONE
                else -> WindowFormFactor.TABLET
            }
            return WindowSizeClass(widthClass, heightClass, formFactor)
        }
    }
}
