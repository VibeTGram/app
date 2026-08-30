package org.vibetgram.gui.theme

/**
 * Color tokens for Material 3 Expressive presentation in VibeTGram.
 * Normative reference: docs/architecture/system-architecture.md section 12.
 */
data class ExpressiveColor(val argb: Long) {
    val alpha: Int get() = ((argb shr 24) and 0xFF).toInt()
    val red: Int get() = ((argb shr 16) and 0xFF).toInt()
    val green: Int get() = ((argb shr 8) and 0xFF).toInt()
    val blue: Int get() = (argb and 0xFF).toInt()

    fun toHex(): String = String.format("#%08X", argb)

    companion object {
        fun fromRgb(r: Int, g: Int, b: Int, a: Int = 255): ExpressiveColor {
            val v = ((a and 0xFF).toLong() shl 24) or
                    ((r and 0xFF).toLong() shl 16) or
                    ((g and 0xFF).toLong() shl 8) or
                    (b and 0xFF).toLong()
            return ExpressiveColor(v)
        }

        fun fromHex(hex: String): ExpressiveColor {
            val clean = hex.removePrefix("#")
            val parsed = clean.toLong(16)
            return if (clean.length == 6) {
                ExpressiveColor(0xFF000000L or parsed)
            } else {
                ExpressiveColor(parsed)
            }
        }
    }
}

data class ExpressiveColorScheme(
    val primary: ExpressiveColor,
    val onPrimary: ExpressiveColor,
    val primaryContainer: ExpressiveColor,
    val onPrimaryContainer: ExpressiveColor,
    val secondary: ExpressiveColor,
    val onSecondary: ExpressiveColor,
    val secondaryContainer: ExpressiveColor,
    val onSecondaryContainer: ExpressiveColor,
    val tertiary: ExpressiveColor,
    val onTertiary: ExpressiveColor,
    val tertiaryContainer: ExpressiveColor,
    val onTertiaryContainer: ExpressiveColor,
    val background: ExpressiveColor,
    val onBackground: ExpressiveColor,
    val surface: ExpressiveColor,
    val onSurface: ExpressiveColor,
    val surfaceVariant: ExpressiveColor,
    val onSurfaceVariant: ExpressiveColor,
    val surfaceContainer: ExpressiveColor,
    val surfaceContainerHigh: ExpressiveColor,
    val surfaceContainerHighest: ExpressiveColor,
    val outline: ExpressiveColor,
    val outlineVariant: ExpressiveColor,
    val error: ExpressiveColor,
    val onError: ExpressiveColor,
    val errorContainer: ExpressiveColor,
    val onErrorContainer: ExpressiveColor,
    val outgoingBubble: ExpressiveColor,
    val onOutgoingBubble: ExpressiveColor,
    val incomingBubble: ExpressiveColor,
    val onIncomingBubble: ExpressiveColor,
    val expressiveAccent: ExpressiveColor,
    val isDark: Boolean = false
) {
    companion object {
        fun defaultLight(): ExpressiveColorScheme = ExpressiveColorScheme(
            primary = ExpressiveColor.fromHex("#6750A4"),
            onPrimary = ExpressiveColor.fromHex("#FFFFFF"),
            primaryContainer = ExpressiveColor.fromHex("#EADDFF"),
            onPrimaryContainer = ExpressiveColor.fromHex("#21005D"),
            secondary = ExpressiveColor.fromHex("#625B71"),
            onSecondary = ExpressiveColor.fromHex("#FFFFFF"),
            secondaryContainer = ExpressiveColor.fromHex("#E8DEF8"),
            onSecondaryContainer = ExpressiveColor.fromHex("#1D192B"),
            tertiary = ExpressiveColor.fromHex("#7D5260"),
            onTertiary = ExpressiveColor.fromHex("#FFFFFF"),
            tertiaryContainer = ExpressiveColor.fromHex("#FFD8E4"),
            onTertiaryContainer = ExpressiveColor.fromHex("#31111D"),
            background = ExpressiveColor.fromHex("#FEF7FF"),
            onBackground = ExpressiveColor.fromHex("#1D1B20"),
            surface = ExpressiveColor.fromHex("#FEF7FF"),
            onSurface = ExpressiveColor.fromHex("#1D1B20"),
            surfaceVariant = ExpressiveColor.fromHex("#E7E0EC"),
            onSurfaceVariant = ExpressiveColor.fromHex("#49454F"),
            surfaceContainer = ExpressiveColor.fromHex("#F3EDF7"),
            surfaceContainerHigh = ExpressiveColor.fromHex("#ECE6F0"),
            surfaceContainerHighest = ExpressiveColor.fromHex("#E6E0E9"),
            outline = ExpressiveColor.fromHex("#79747E"),
            outlineVariant = ExpressiveColor.fromHex("#CAC4D0"),
            error = ExpressiveColor.fromHex("#B3261E"),
            onError = ExpressiveColor.fromHex("#FFFFFF"),
            errorContainer = ExpressiveColor.fromHex("#F9DEDC"),
            onErrorContainer = ExpressiveColor.fromHex("#410E0B"),
            outgoingBubble = ExpressiveColor.fromHex("#EADDFF"),
            onOutgoingBubble = ExpressiveColor.fromHex("#21005D"),
            incomingBubble = ExpressiveColor.fromHex("#F3EDF7"),
            onIncomingBubble = ExpressiveColor.fromHex("#1D1B20"),
            expressiveAccent = ExpressiveColor.fromHex("#825500"),
            isDark = false
        )

        fun defaultDark(): ExpressiveColorScheme = ExpressiveColorScheme(
            primary = ExpressiveColor.fromHex("#D0BCFF"),
            onPrimary = ExpressiveColor.fromHex("#381E72"),
            primaryContainer = ExpressiveColor.fromHex("#4F378B"),
            onPrimaryContainer = ExpressiveColor.fromHex("#EADDFF"),
            secondary = ExpressiveColor.fromHex("#CCC2DC"),
            onSecondary = ExpressiveColor.fromHex("#332D41"),
            secondaryContainer = ExpressiveColor.fromHex("#4A4458"),
            onSecondaryContainer = ExpressiveColor.fromHex("#E8DEF8"),
            tertiary = ExpressiveColor.fromHex("#EFB8C8"),
            onTertiary = ExpressiveColor.fromHex("#492532"),
            tertiaryContainer = ExpressiveColor.fromHex("#633B48"),
            onTertiaryContainer = ExpressiveColor.fromHex("#FFD8E4"),
            background = ExpressiveColor.fromHex("#141218"),
            onBackground = ExpressiveColor.fromHex("#E6E0E9"),
            surface = ExpressiveColor.fromHex("#141218"),
            onSurface = ExpressiveColor.fromHex("#E6E0E9"),
            surfaceVariant = ExpressiveColor.fromHex("#49454F"),
            onSurfaceVariant = ExpressiveColor.fromHex("#CAC4D0"),
            surfaceContainer = ExpressiveColor.fromHex("#211F26"),
            surfaceContainerHigh = ExpressiveColor.fromHex("#2B2930"),
            surfaceContainerHighest = ExpressiveColor.fromHex("#36343B"),
            outline = ExpressiveColor.fromHex("#938F99"),
            outlineVariant = ExpressiveColor.fromHex("#44474F"),
            error = ExpressiveColor.fromHex("#F2B8B5"),
            onError = ExpressiveColor.fromHex("#601410"),
            errorContainer = ExpressiveColor.fromHex("#8C1D18"),
            onErrorContainer = ExpressiveColor.fromHex("#F9DEDC"),
            outgoingBubble = ExpressiveColor.fromHex("#4F378B"),
            onOutgoingBubble = ExpressiveColor.fromHex("#EADDFF"),
            incomingBubble = ExpressiveColor.fromHex("#211F26"),
            onIncomingBubble = ExpressiveColor.fromHex("#E6E0E9"),
            expressiveAccent = ExpressiveColor.fromHex("#FFB951"),
            isDark = true
        )
    }
}
