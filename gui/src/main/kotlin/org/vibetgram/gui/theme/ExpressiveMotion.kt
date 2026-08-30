package org.vibetgram.gui.theme

/**
 * Material 3 Expressive motion and timing tokens.
 */
data class ExpressiveMotion(
    val durationShortMs: Long = 150L,
    val durationMediumMs: Long = 300L,
    val durationLongMs: Long = 450L,
    val durationExpansionMs: Long = 250L,
    val durationBubblePopMs: Long = 180L,
    val easingStandard: String = "cubic-bezier(0.2, 0.0, 0.0, 1.0)",
    val easingEmphasized: String = "cubic-bezier(0.05, 0.7, 0.1, 1.0)",
    val easingBouncy: String = "spring(damping=0.75, stiffness=350)"
)
