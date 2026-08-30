package org.vibetgram.gui.accessibility

/** Logical keys exposed by keyboard, D-pad and switch-access adapters. */
enum class NavigationKey {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    ENTER,
    BACK,
    TAB
}

enum class NavigationAction {
    MOVE_PREVIOUS,
    MOVE_NEXT,
    MOVE_LEFT,
    MOVE_RIGHT,
    ACTIVATE,
    BACK,
    FOCUS_NEXT,
    NONE
}

/** Shared mapping keeps non-touch input behavior deterministic across screens. */
object AccessibleInputNavigation {
    fun actionFor(key: NavigationKey): NavigationAction = when (key) {
        NavigationKey.UP -> NavigationAction.MOVE_PREVIOUS
        NavigationKey.DOWN -> NavigationAction.MOVE_NEXT
        NavigationKey.LEFT -> NavigationAction.MOVE_LEFT
        NavigationKey.RIGHT -> NavigationAction.MOVE_RIGHT
        NavigationKey.ENTER -> NavigationAction.ACTIVATE
        NavigationKey.BACK -> NavigationAction.BACK
        NavigationKey.TAB -> NavigationAction.FOCUS_NEXT
    }
}
