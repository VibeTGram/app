package org.vibetgram.gui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vibetgram.gui.contract.GuiRoute
import org.vibetgram.gui.contract.NavigationState
import org.vibetgram.gui.domain.ChatRef

class NavigationStateTest {

    @Test
    fun testInitialNavigationState() {
        val nav = NavigationState()
        assertEquals(GuiRoute.Auth.PhoneEntry, nav.currentRoute)
        assertFalse(nav.canGoBack)
        assertEquals(1, nav.backStack.size)
    }

    @Test
    fun testPushAndPopRoute() {
        val nav = NavigationState()
            .navigateTo(GuiRoute.Auth.CodeVerify("+1234567890"))
            .navigateTo(GuiRoute.ChatList())

        assertEquals(GuiRoute.ChatList(), nav.currentRoute)
        assertTrue(nav.canGoBack)
        assertEquals(3, nav.backStack.size)

        val popped = nav.pop()
        assertEquals(GuiRoute.Auth.CodeVerify("+1234567890"), popped.currentRoute)

        val root = popped.pop()
        assertEquals(GuiRoute.Auth.PhoneEntry, root.currentRoute)
        assertFalse(root.canGoBack)
    }

    @Test
    fun testPopToPredicate() {
        val chatRef = ChatRef(101L)
        val nav = NavigationState()
            .navigateTo(GuiRoute.ChatList())
            .navigateTo(GuiRoute.Conversation(chatRef))
            .navigateTo(GuiRoute.Settings())

        val backToChatList = nav.popTo({ it is GuiRoute.ChatList })
        assertEquals(GuiRoute.ChatList(), backToChatList.currentRoute)
        assertEquals(2, backToChatList.backStack.size)
    }

    @Test
    fun testReplaceRoute() {
        val nav = NavigationState()
            .navigateTo(GuiRoute.ChatList())
            .replace(GuiRoute.Settings())

        assertEquals(GuiRoute.Settings(), nav.currentRoute)
        assertEquals(2, nav.backStack.size)
    }
}
