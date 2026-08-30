package org.vibetgram.gui.theme

/**
 * Shape tokens for Material 3 Expressive with asymmetric message bubbles.
 */
data class CornerRadiiDp(
    val topStart: Float,
    val topEnd: Float,
    val bottomEnd: Float,
    val bottomStart: Float
) {
    companion object {
        fun all(radius: Float) = CornerRadiiDp(radius, radius, radius, radius)
    }
}

data class ExpressiveShapes(
    val extraSmall: CornerRadiiDp = CornerRadiiDp.all(4f),
    val small: CornerRadiiDp = CornerRadiiDp.all(8f),
    val medium: CornerRadiiDp = CornerRadiiDp.all(12f),
    val large: CornerRadiiDp = CornerRadiiDp.all(16f),
    val extraLarge: CornerRadiiDp = CornerRadiiDp.all(28f),
    val pill: CornerRadiiDp = CornerRadiiDp.all(999f),
    val outgoingBubble: CornerRadiiDp = CornerRadiiDp(18f, 18f, 4f, 18f),
    val incomingBubble: CornerRadiiDp = CornerRadiiDp(18f, 18f, 18f, 4f)
)
