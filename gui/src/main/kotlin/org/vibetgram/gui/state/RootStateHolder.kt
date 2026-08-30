package org.vibetgram.gui.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.vibetgram.gui.adaptive.AdaptiveLayoutConfig
import org.vibetgram.gui.adaptive.AdaptiveLayoutStrategy
import org.vibetgram.gui.adaptive.WindowSizeClass
import org.vibetgram.gui.contract.GuiRoute
import org.vibetgram.gui.contract.NavigationState
import org.vibetgram.gui.domain.AccountHandle
import org.vibetgram.gui.domain.AccountManager
import org.vibetgram.gui.domain.AuthState
import org.vibetgram.gui.domain.AuthorizationService
import org.vibetgram.gui.domain.ChatMutation
import org.vibetgram.gui.domain.ChatQuery
import org.vibetgram.gui.domain.ChatRef
import org.vibetgram.gui.domain.DraftService
import org.vibetgram.gui.domain.MessageComposer
import org.vibetgram.gui.domain.MessageMutation
import org.vibetgram.gui.domain.MessageQuery
import org.vibetgram.gui.theme.ResolvedTheme
import org.vibetgram.gui.theme.ThemeResolver

data class RootUiState(
    val navigationState: NavigationState = NavigationState(),
    val activeAccount: AccountHandle? = null,
    val windowSizeClass: WindowSizeClass = WindowSizeClass.compute(400f, 800f),
    val layoutConfig: AdaptiveLayoutConfig = AdaptiveLayoutStrategy.determineConfig(WindowSizeClass.compute(400f, 800f)),
    val theme: ResolvedTheme = ThemeResolver.resolve(isDark = false)
)

class RootStateHolder(
    val authService: AuthorizationService,
    val accountManager: AccountManager,
    val chatQuery: ChatQuery,
    val chatMutation: ChatMutation,
    val messageQuery: MessageQuery,
    val messageComposer: MessageComposer,
    val messageMutation: MessageMutation,
    val draftService: DraftService,
    val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(RootUiState())
    val uiState: StateFlow<RootUiState> = _uiState.asStateFlow()

    val authStateHolder = AuthStateHolder(authService, scope)
    val chatListStateHolder = ChatListStateHolder(accountManager, chatQuery, chatMutation, scope)
    val conversationStateHolder = ConversationStateHolder(chatQuery, messageQuery, messageMutation, scope)
    val composerStateHolder = TextComposerStateHolder(messageComposer, draftService, scope)

    private var isAuthorized = false

    init {
        scope.launch {
            accountManager.observeActiveAccount().collect { account ->
                _uiState.value = _uiState.value.copy(activeAccount = account)
                if (account != null && isAuthorized && _uiState.value.navigationState.currentRoute is GuiRoute.Auth) {
                    conversationStateHolder.setAccount(account)
                    composerStateHolder.setAccount(account)
                    navigateTo(GuiRoute.ChatList())
                }
            }
        }
        scope.launch {
            authService.observeAuthState().collect { authState ->
                when (authState) {
                    is AuthState.Ready -> {
                        isAuthorized = true
                        val account = _uiState.value.activeAccount ?: authState.activeAccount
                        _uiState.value = _uiState.value.copy(activeAccount = account)
                        if (_uiState.value.navigationState.currentRoute is GuiRoute.Auth) {
                            conversationStateHolder.setAccount(account)
                            composerStateHolder.setAccount(account)
                            navigateTo(GuiRoute.ChatList())
                        }
                    }
                    is AuthState.Closed, is AuthState.WaitPhoneNumber -> {
                        isAuthorized = false
                        if (_uiState.value.navigationState.currentRoute !is GuiRoute.Auth) {
                            _uiState.value = _uiState.value.copy(
                                navigationState = NavigationState()
                            )
                        }
                    }
                    else -> {
                        isAuthorized = false
                        if (_uiState.value.navigationState.currentRoute !is GuiRoute.Auth) {
                            _uiState.value = _uiState.value.copy(
                                navigationState = NavigationState()
                            )
                        }
                    }
                }
            }
        }
    }

    fun navigateTo(route: GuiRoute) {
        if (!isAuthorized && route !is GuiRoute.Auth) return
        _uiState.value = _uiState.value.copy(
            navigationState = _uiState.value.navigationState.navigateTo(route)
        )
        if (route is GuiRoute.Conversation) {
            chatListStateHolder.onChatSelected(route.chatRef)
            conversationStateHolder.openChat(route.chatRef)
            composerStateHolder.bindChat(route.chatRef)
        }
    }

    fun popNavigation(): Boolean {
        if (!_uiState.value.navigationState.canGoBack) return false
        _uiState.value = _uiState.value.copy(
            navigationState = _uiState.value.navigationState.pop()
        )
        return true
    }

    fun updateWindowDimensions(
        widthDp: Float,
        heightDp: Float,
        isFoldable: Boolean = false,
        isChromeOs: Boolean = false
    ) {
        val wsc = WindowSizeClass.compute(widthDp, heightDp, isFoldable, isChromeOs)
        val config = AdaptiveLayoutStrategy.determineConfig(wsc)
        _uiState.value = _uiState.value.copy(
            windowSizeClass = wsc,
            layoutConfig = config
        )
    }

    fun updateTheme(isDark: Boolean, highContrast: Boolean = false) {
        val theme = ThemeResolver.resolve(
            isDark = isDark,
            accessibility = _uiState.value.theme.accessibility.copy(isHighContrastEnabled = highContrast)
        )
        _uiState.value = _uiState.value.copy(theme = theme)
    }

    fun updateAccessibility(
        reducedMotion: Boolean = _uiState.value.theme.accessibility.isReducedMotionEnabled,
        highContrast: Boolean = _uiState.value.theme.accessibility.isHighContrastEnabled,
        fontScale: Float = _uiState.value.theme.accessibility.fontScale
    ) {
        val current = _uiState.value.theme
        _uiState.value = _uiState.value.copy(
            theme = ThemeResolver.resolve(
                isDark = current.colorScheme.isDark,
                accessibility = current.accessibility.copy(
                    isReducedMotionEnabled = reducedMotion,
                    isHighContrastEnabled = highContrast,
                    fontScale = fontScale
                )
            )
        )
    }
}
