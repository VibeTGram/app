package org.vibetgram.gui.accessibility

/**
 * Accessibility configuration and preferences for inclusive design.
 * Normative reference: docs/architecture/system-architecture.md section 12.
 */
data class AccessibilityConfig(
    val fontScale: Float = 1.0f,
    val isTalkBackActive: Boolean = false,
    val isHighContrastEnabled: Boolean = false,
    val isReducedMotionEnabled: Boolean = false,
    val minimumTouchTargetDp: Float = 48f
) {
    init {
        require(fontScale in 0.85f..3.0f) { "fontScale must be within [0.85, 3.0] range" }
        require(minimumTouchTargetDp >= 48f) { "minimumTouchTargetDp must be at least 48dp per Material & WCAG guidelines" }
    }
}
