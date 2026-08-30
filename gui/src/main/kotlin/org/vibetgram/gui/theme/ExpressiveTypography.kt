package org.vibetgram.gui.theme

/**
 * Material 3 Expressive typography tokens and scale.
 */
data class ExpressiveTextStyle(
    val fontSizeSp: Float,
    val lineHeightSp: Float,
    val fontWeight: Int = 400,
    val letterSpacingSp: Float = 0f
)

data class ExpressiveTypography(
    val displayLarge: ExpressiveTextStyle = ExpressiveTextStyle(57f, 64f, 400, -0.25f),
    val displayMedium: ExpressiveTextStyle = ExpressiveTextStyle(45f, 52f, 400, 0f),
    val displaySmall: ExpressiveTextStyle = ExpressiveTextStyle(36f, 44f, 400, 0f),
    val headlineLarge: ExpressiveTextStyle = ExpressiveTextStyle(32f, 40f, 600, 0f),
    val headlineMedium: ExpressiveTextStyle = ExpressiveTextStyle(28f, 36f, 600, 0f),
    val headlineSmall: ExpressiveTextStyle = ExpressiveTextStyle(24f, 32f, 600, 0f),
    val titleLarge: ExpressiveTextStyle = ExpressiveTextStyle(22f, 28f, 600, 0f),
    val titleMedium: ExpressiveTextStyle = ExpressiveTextStyle(16f, 24f, 600, 0.15f),
    val titleSmall: ExpressiveTextStyle = ExpressiveTextStyle(14f, 20f, 600, 0.1f),
    val bodyLarge: ExpressiveTextStyle = ExpressiveTextStyle(16f, 24f, 400, 0.5f),
    val bodyMedium: ExpressiveTextStyle = ExpressiveTextStyle(14f, 20f, 400, 0.25f),
    val bodySmall: ExpressiveTextStyle = ExpressiveTextStyle(12f, 16f, 400, 0.4f),
    val labelLarge: ExpressiveTextStyle = ExpressiveTextStyle(14f, 20f, 600, 0.1f),
    val labelMedium: ExpressiveTextStyle = ExpressiveTextStyle(12f, 16f, 600, 0.5f),
    val labelSmall: ExpressiveTextStyle = ExpressiveTextStyle(11f, 16f, 600, 0.5f)
) {
    /** Applies the system font scale without changing weight or tracking. */
    fun scaled(scale: Float): ExpressiveTypography = copy(
        displayLarge = displayLarge.scaled(scale),
        displayMedium = displayMedium.scaled(scale),
        displaySmall = displaySmall.scaled(scale),
        headlineLarge = headlineLarge.scaled(scale),
        headlineMedium = headlineMedium.scaled(scale),
        headlineSmall = headlineSmall.scaled(scale),
        titleLarge = titleLarge.scaled(scale),
        titleMedium = titleMedium.scaled(scale),
        titleSmall = titleSmall.scaled(scale),
        bodyLarge = bodyLarge.scaled(scale),
        bodyMedium = bodyMedium.scaled(scale),
        bodySmall = bodySmall.scaled(scale),
        labelLarge = labelLarge.scaled(scale),
        labelMedium = labelMedium.scaled(scale),
        labelSmall = labelSmall.scaled(scale)
    )
}

private fun ExpressiveTextStyle.scaled(scale: Float): ExpressiveTextStyle = copy(
    fontSizeSp = fontSizeSp * scale,
    lineHeightSp = lineHeightSp * scale
)
