package org.vibetgram.gui.theme

/**
 * Normative Theme Resolver for VibeTGram.
 * Resolution Order:
 * 1. Material 3 Expressive base tokens
 * 2. System Dynamic Color (when enabled)
 * 3. Selected built-in palette
 * 4. Enabled resource packs (bottom to top priority)
 * 5. Terminal accessibility corrections (non-overridable)
 *
 * Reference: docs/architecture/system-architecture.md section 12
 */

enum class BuiltInPalette {
    DEFAULT_EXPRESSIVE,
    OCEAN_CYAN,
    SUNSET_AMBER,
    FOREST_EMERALD,
    MONOCHROME
}

data class ResourcePackTokens(
    val packId: String,
    val priority: Int,
    val primaryColorHex: String? = null,
    val outgoingBubbleColorHex: String? = null,
    val incomingBubbleColorHex: String? = null
)

data class AccessibilityOverrides(
    val isHighContrastEnabled: Boolean = false,
    val isReducedMotionEnabled: Boolean = false,
    val fontScale: Float = 1.0f
) {
    init {
        require(fontScale in 0.85f..3.0f) { "fontScale must be within [0.85, 3.0] range" }
    }
}

data class ResolvedTheme(
    val colorScheme: ExpressiveColorScheme,
    val typography: ExpressiveTypography,
    val shapes: ExpressiveShapes,
    val motion: ExpressiveMotion,
    val accessibility: AccessibilityOverrides
)

object ThemeResolver {

    fun resolve(
        isDark: Boolean,
        dynamicColorHex: String? = null,
        palette: BuiltInPalette = BuiltInPalette.DEFAULT_EXPRESSIVE,
        resourcePacks: List<ResourcePackTokens> = emptyList(),
        accessibility: AccessibilityOverrides = AccessibilityOverrides()
    ): ResolvedTheme {
        // Step 1: Base M3 Expressive Tokens
        var colors = if (isDark) ExpressiveColorScheme.defaultDark() else ExpressiveColorScheme.defaultLight()
        val typography = ExpressiveTypography().scaled(accessibility.fontScale)
        val shapes = ExpressiveShapes()
        var motion = ExpressiveMotion()

        // Step 2: System Dynamic Color
        if (dynamicColorHex != null) {
            val dynColor = ExpressiveColor.fromHex(dynamicColorHex)
            colors = colors.copy(
                primary = dynColor,
                primaryContainer = if (isDark) ExpressiveColor.fromHex("#3A2B5E") else ExpressiveColor.fromHex("#E5D8FC")
            )
        }

        // Step 3: Selected Built-in Palette
        colors = applyBuiltInPalette(colors, palette, isDark)

        // Step 4: Resource Packs in ascending priority order (highest priority wins)
        val sortedPacks = resourcePacks.sortedBy { it.priority }
        for (pack in sortedPacks) {
            colors = applyResourcePack(colors, pack)
        }

        // Step 5: Terminal Accessibility Corrections (Non-overridable)
        if (accessibility.isHighContrastEnabled) {
            colors = applyHighContrast(colors, isDark)
        }
        if (accessibility.isReducedMotionEnabled) {
            motion = motion.copy(
                durationShortMs = 0L,
                durationMediumMs = 0L,
                durationLongMs = 0L,
                durationExpansionMs = 0L,
                durationBubblePopMs = 0L
            )
        }

        return ResolvedTheme(
            colorScheme = colors,
            typography = typography,
            shapes = shapes,
            motion = motion,
            accessibility = accessibility
        )
    }

    private fun applyBuiltInPalette(base: ExpressiveColorScheme, palette: BuiltInPalette, isDark: Boolean): ExpressiveColorScheme {
        return when (palette) {
            BuiltInPalette.DEFAULT_EXPRESSIVE -> base
            BuiltInPalette.OCEAN_CYAN -> base.copy(
                primary = if (isDark) ExpressiveColor.fromHex("#80D8FF") else ExpressiveColor.fromHex("#00658B"),
                primaryContainer = if (isDark) ExpressiveColor.fromHex("#004C6A") else ExpressiveColor.fromHex("#C3E7FF"),
                outgoingBubble = if (isDark) ExpressiveColor.fromHex("#004C6A") else ExpressiveColor.fromHex("#C3E7FF")
            )
            BuiltInPalette.SUNSET_AMBER -> base.copy(
                primary = if (isDark) ExpressiveColor.fromHex("#FFB74D") else ExpressiveColor.fromHex("#8C5000"),
                primaryContainer = if (isDark) ExpressiveColor.fromHex("#663800") else ExpressiveColor.fromHex("#FFDDB3"),
                outgoingBubble = if (isDark) ExpressiveColor.fromHex("#663800") else ExpressiveColor.fromHex("#FFDDB3")
            )
            BuiltInPalette.FOREST_EMERALD -> base.copy(
                primary = if (isDark) ExpressiveColor.fromHex("#81C784") else ExpressiveColor.fromHex("#1B6D2F"),
                primaryContainer = if (isDark) ExpressiveColor.fromHex("#00531D") else ExpressiveColor.fromHex("#A3F5A8"),
                outgoingBubble = if (isDark) ExpressiveColor.fromHex("#00531D") else ExpressiveColor.fromHex("#A3F5A8")
            )
            BuiltInPalette.MONOCHROME -> base.copy(
                primary = if (isDark) ExpressiveColor.fromHex("#E0E0E0") else ExpressiveColor.fromHex("#212121"),
                primaryContainer = if (isDark) ExpressiveColor.fromHex("#424242") else ExpressiveColor.fromHex("#EEEEEE"),
                outgoingBubble = if (isDark) ExpressiveColor.fromHex("#424242") else ExpressiveColor.fromHex("#EEEEEE")
            )
        }
    }

    private fun applyResourcePack(base: ExpressiveColorScheme, pack: ResourcePackTokens): ExpressiveColorScheme {
        var res = base
        if (pack.primaryColorHex != null) {
            res = res.copy(primary = ExpressiveColor.fromHex(pack.primaryColorHex))
        }
        if (pack.outgoingBubbleColorHex != null) {
            res = res.copy(outgoingBubble = ExpressiveColor.fromHex(pack.outgoingBubbleColorHex))
        }
        if (pack.incomingBubbleColorHex != null) {
            res = res.copy(incomingBubble = ExpressiveColor.fromHex(pack.incomingBubbleColorHex))
        }
        return res
    }

    private fun applyHighContrast(base: ExpressiveColorScheme, isDark: Boolean): ExpressiveColorScheme {
        return if (isDark) {
            base.copy(
                background = ExpressiveColor.fromHex("#000000"),
                surface = ExpressiveColor.fromHex("#000000"),
                onBackground = ExpressiveColor.fromHex("#FFFFFF"),
                onSurface = ExpressiveColor.fromHex("#FFFFFF"),
                outline = ExpressiveColor.fromHex("#FFFFFF"),
                primary = ExpressiveColor.fromHex("#FFFFFF"),
                onPrimary = ExpressiveColor.fromHex("#000000"),
                outgoingBubble = ExpressiveColor.fromHex("#282828"),
                onOutgoingBubble = ExpressiveColor.fromHex("#FFFFFF"),
                incomingBubble = ExpressiveColor.fromHex("#121212"),
                onIncomingBubble = ExpressiveColor.fromHex("#FFFFFF")
            )
        } else {
            base.copy(
                background = ExpressiveColor.fromHex("#FFFFFF"),
                surface = ExpressiveColor.fromHex("#FFFFFF"),
                onBackground = ExpressiveColor.fromHex("#000000"),
                onSurface = ExpressiveColor.fromHex("#000000"),
                outline = ExpressiveColor.fromHex("#000000"),
                primary = ExpressiveColor.fromHex("#000000"),
                onPrimary = ExpressiveColor.fromHex("#FFFFFF"),
                outgoingBubble = ExpressiveColor.fromHex("#E0E0E0"),
                onOutgoingBubble = ExpressiveColor.fromHex("#000000"),
                incomingBubble = ExpressiveColor.fromHex("#F5F5F5"),
                onIncomingBubble = ExpressiveColor.fromHex("#000000")
            )
        }
    }
}
