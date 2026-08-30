package org.vibetgram.gui.contract

/**
 * Immutable navigation backstack and state transitions.
 */
data class NavigationState(
    val backStack: List<GuiRoute> = listOf(GuiRoute.Auth.PhoneEntry)
) {
    val currentRoute: GuiRoute
        get() = backStack.lastOrNull() ?: GuiRoute.Auth.PhoneEntry

    val canGoBack: Boolean
        get() = backStack.size > 1

    fun navigateTo(route: GuiRoute): NavigationState {
        return copy(backStack = backStack + route)
    }

    fun pop(): NavigationState {
        return if (canGoBack) {
            copy(backStack = backStack.dropLast(1))
        } else {
            this
        }
    }

    fun popTo(predicate: (GuiRoute) -> Boolean, inclusive: Boolean = false): NavigationState {
        val index = backStack.indexOfLast(predicate)
        if (index == -1) return this
        val targetIndex = if (inclusive) index else index + 1
        val newStack = backStack.take(targetIndex)
        return if (newStack.isNotEmpty()) copy(backStack = newStack) else this
    }

    fun replace(route: GuiRoute): NavigationState {
        return if (backStack.isEmpty()) {
            copy(backStack = listOf(route))
        } else {
            copy(backStack = backStack.dropLast(1) + route)
        }
    }

    fun resetTo(route: GuiRoute): NavigationState {
        return copy(backStack = listOf(route))
    }
}

sealed interface NavigationEvent {
    data class Navigate(val route: GuiRoute) : NavigationEvent
    data object Back : NavigationEvent
    data class Replace(val route: GuiRoute) : NavigationEvent
    data class ResetTo(val route: GuiRoute) : NavigationEvent
    data class PopTo(val predicate: (GuiRoute) -> Boolean, val inclusive: Boolean = false) : NavigationEvent
}
