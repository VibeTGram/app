package org.vibetgram.gui.accessibility

import org.vibetgram.gui.theme.ExpressiveColor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Validator for WCAG 2.2 color contrast ratios, touch target sizes, and screen reader labels.
 */
object A11yValidator {

    const val MIN_CONTRAST_NORMAL_TEXT = 4.5
    const val MIN_CONTRAST_LARGE_TEXT = 3.0
    const val MIN_CONTRAST_HIGH_CONTRAST = 7.0
    const val MIN_TOUCH_TARGET_DP = 48f

    fun calculateLuminance(color: ExpressiveColor): Double {
        fun channelLuminance(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val r = channelLuminance(color.red)
        val g = channelLuminance(color.green)
        val b = channelLuminance(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    fun calculateContrastRatio(fg: ExpressiveColor, bg: ExpressiveColor): Double {
        val l1 = calculateLuminance(fg)
        val l2 = calculateLuminance(bg)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun isContrastCompliant(
        fg: ExpressiveColor,
        bg: ExpressiveColor,
        isLargeText: Boolean = false,
        requiresHighContrast: Boolean = false
    ): Boolean {
        val ratio = calculateContrastRatio(fg, bg)
        val threshold = when {
            requiresHighContrast -> MIN_CONTRAST_HIGH_CONTRAST
            isLargeText -> MIN_CONTRAST_LARGE_TEXT
            else -> MIN_CONTRAST_NORMAL_TEXT
        }
        return ratio >= threshold
    }

    fun validateTouchTarget(widthDp: Float, heightDp: Float): Boolean {
        return widthDp >= MIN_TOUCH_TARGET_DP && heightDp >= MIN_TOUCH_TARGET_DP
    }

    fun validateContentDescription(description: String?): Boolean {
        return !description.isNullOrBlank()
    }
}
