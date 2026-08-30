package org.vibetgram.gui.compose

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.vibetgram.gui.accessibility.AccessibilitySemantics
import org.vibetgram.gui.accessibility.AccessibleInputNavigation
import org.vibetgram.gui.accessibility.NavigationAction
import org.vibetgram.gui.accessibility.NavigationKey

import org.vibetgram.gui.adaptive.NavigationType
import org.vibetgram.gui.api.GuiDependencies
import org.vibetgram.gui.api.GuiEntryPoint
import org.vibetgram.gui.api.GuiEventHandler
import org.vibetgram.gui.api.GuiRenderContainer
import org.vibetgram.gui.contract.GuiRoute
import org.vibetgram.gui.modui.ModUiNode
import org.vibetgram.gui.modui.ModUiSlot
import org.vibetgram.gui.screens.RootScreenRenderState
import org.vibetgram.gui.state.RootStateHolder
import org.vibetgram.gui.theme.ExpressiveColor
import org.vibetgram.gui.theme.ExpressiveTextStyle
import org.vibetgram.gui.theme.CornerRadiiDp
import org.vibetgram.gui.theme.ResolvedTheme

sealed interface GuiHostState {
    data object Loading : GuiHostState
    data class Ready(val dependencies: GuiDependencies) : GuiHostState
    data class Error(val code: String, val message: String) : GuiHostState
}

/** Fail-closed Android host around the replaceable [GuiEntryPoint]. */
@Composable
fun VibeTGramHost(
    state: GuiHostState,
    entryPoint: GuiEntryPoint,
    windowWidthDp: Float,
    windowHeightDp: Float,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    contentInsets: WindowInsets = WindowInsets(0, 0, 0, 0)
) {
    MaterialTheme {
        Surface(
            modifier = modifier.fillMaxSize().windowInsetsPadding(contentInsets),
            color = MaterialTheme.colorScheme.background
        ) {
            when (state) {
                GuiHostState.Loading -> HostStatus(
                    title = "Starting VibeTGram",
                    detail = "Waiting for Core services",
                    isLoading = true
                )
                is GuiHostState.Error -> HostStatus(
                    title = "Telegram Core unavailable",
                    detail = "${state.code}: ${state.message}",
                    isLoading = false
                )
                is GuiHostState.Ready -> VibeTGramApp(
                    entryPoint = entryPoint,
                    dependencies = state.dependencies,
                    windowWidthDp = windowWidthDp,
                    windowHeightDp = windowHeightDp,
                    reducedMotion = reducedMotion
                )
            }
        }
    }
}

@Composable
private fun HostStatus(title: String, detail: String, isLoading: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) CircularProgressIndicator(Modifier.size(48.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 20.dp))
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isLoading) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp).semantics { contentDescription = detail }
        )
    }
}

/**
 * Android Compose host for the replaceable GUI. The app supplies services and
 * window dimensions; this module owns no Activity, TDLib, or navigation object.
 */
@Composable
fun VibeTGramApp(
    entryPoint: GuiEntryPoint,
    dependencies: GuiDependencies,
    windowWidthDp: Float,
    windowHeightDp: Float,
    isFoldable: Boolean = false,
    isChromeOs: Boolean = false,
    reducedMotion: Boolean = false,
    modifier: Modifier = Modifier,
    slotNodes: Map<String, ModUiNode> = emptyMap(),
    modUiActionHandler: (String, Map<String, Any>) -> Unit = { _, _ -> }
) {
    val stateHolder = remember(entryPoint, dependencies) {
        entryPoint.createStateHolder(dependencies)
    }
    val rootState by stateHolder.uiState.collectAsState()
    val authState by stateHolder.authStateHolder.uiState.collectAsState()
    val chatListState by stateHolder.chatListStateHolder.uiState.collectAsState()
    val conversationState by stateHolder.conversationStateHolder.uiState.collectAsState()
    val composerState by stateHolder.composerStateHolder.uiState.collectAsState()
    val validSlotNodes = remember(slotNodes) {
        slotNodes.filterValues {
            entryPoint.validateAndRenderSlot(
                ModUiSlot.DeclarativeScreen("host", "slot"), it
            ).isValid
        }
    }

    LaunchedEffect(windowWidthDp, windowHeightDp, isFoldable, isChromeOs, reducedMotion) {
        stateHolder.updateWindowDimensions(windowWidthDp, windowHeightDp, isFoldable, isChromeOs)
        stateHolder.updateAccessibility(reducedMotion = reducedMotion)
    }

    MaterialTheme(
        colorScheme = rootState.theme.composeColorScheme(),
        shapes = rootState.theme.composeShapes(),
        typography = rootState.theme.composeTypography()
    ) {
        val events = remember(stateHolder, modUiActionHandler) {
            object : GuiEventHandler {
                override fun onNavigate(route: GuiRoute) = stateHolder.navigateTo(route)
                override fun onBack() {
                    stateHolder.popNavigation()
                }
                override fun onModUiAction(actionId: String, payload: Map<String, Any>) =
                    modUiActionHandler(actionId, payload)
            }
        }
        val renderState = remember(rootState, authState, chatListState, conversationState, composerState, validSlotNodes) {
            entryPoint.render(NoOpRenderContainer, stateHolder, events, validSlotNodes)
        }
        Box(
            modifier.fillMaxSize().accessibleNavigation { action ->
                if (action == NavigationAction.BACK) events.onBack()
            }
        ) {
            AdaptiveScaffold(
                state = rootState,
                renderState = renderState,
                stateHolder = stateHolder,
                events = events,
                slotNodes = validSlotNodes
            )
        }
    }
}

private object NoOpRenderContainer : GuiRenderContainer {
    override fun setRootState(state: RootScreenRenderState) = Unit
    override fun renderSlotNode(slot: ModUiSlot, node: ModUiNode) = Unit
}

@Composable
private fun AdaptiveScaffold(
    state: org.vibetgram.gui.state.RootUiState,
    renderState: RootScreenRenderState,
    stateHolder: RootStateHolder,
    events: GuiEventHandler,
    slotNodes: Map<String, ModUiNode>
) {
    val content: @Composable () -> Unit = {
        RootContent(renderState, stateHolder, events, slotNodes)
    }

    when (state.layoutConfig.navigationType) {
        NavigationType.BOTTOM_NAVIGATION_BAR -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
            NavigationBar {
                NavigationBarItem(
                    selected = renderState.currentRoute is GuiRoute.ChatList,
                    onClick = { events.onNavigate(GuiRoute.ChatList()) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                    label = { Text("Chats") }
                )
                NavigationBarItem(
                    selected = renderState.currentRoute is GuiRoute.Settings,
                    onClick = { events.onNavigate(GuiRoute.Settings()) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") }
                )
            }
        }
        NavigationType.NAVIGATION_RAIL -> Row(Modifier.fillMaxSize()) {
            NavigationRail {
                NavigationRailItem(
                    selected = renderState.currentRoute is GuiRoute.ChatList,
                    onClick = { events.onNavigate(GuiRoute.ChatList()) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                    label = { Text("Chats") }
                )
                NavigationRailItem(
                    selected = renderState.currentRoute is GuiRoute.Settings,
                    onClick = { events.onNavigate(GuiRoute.Settings()) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") }
                )
            }
            Box(Modifier.weight(1f).fillMaxSize()) { content() }
        }
        NavigationType.PERMANENT_NAVIGATION_DRAWER -> PermanentNavigationDrawer(
            drawerContent = {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("VibeTGram", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = { events.onNavigate(GuiRoute.ChatList()) }) { Text("Chats") }
                    TextButton(onClick = { events.onNavigate(GuiRoute.Settings()) }) { Text("Settings") }
                }
            }
        ) { content() }
    }
}

@Composable
private fun RootContent(
    renderState: RootScreenRenderState,
    stateHolder: RootStateHolder,
    events: GuiEventHandler,
    slotNodes: Map<String, ModUiNode>
) {
    when {
        renderState.authState != null -> AuthContent(renderState.authState, stateHolder)
        renderState.isTwoPaneMasterDetail && renderState.chatListState != null -> Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            ChatListContent(
                renderState.chatListState,
                stateHolder,
                events,
                Modifier.weight(0.4f),
                slotNodes = slotNodes.filterKeys { it.startsWith("chat_list.") }
            )
            ConversationContent(
                renderState.conversationState,
                renderState.composerState,
                stateHolder,
                events,
                slotNodes,
                Modifier.weight(0.6f)
            )
        }
        renderState.chatListState != null -> ChatListContent(
            renderState.chatListState,
            stateHolder,
            events,
            slotNodes = slotNodes.filterKeys { it.startsWith("chat_list.") }
        )
        renderState.conversationState != null -> ConversationContent(
            renderState.conversationState,
            renderState.composerState,
            stateHolder,
            events,
            slotNodes
        )
        else -> Text("No screen selected", Modifier.padding(24.dp))
    }
}

@Composable
private fun AuthContent(
    state: org.vibetgram.gui.screens.AuthScreenRenderState,
    stateHolder: RootStateHolder
) {
    val auth = stateHolder.authStateHolder
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(state.title, style = MaterialTheme.typography.headlineMedium)
        Text(state.subtitle, modifier = Modifier.semantics { contentDescription = state.accessibilityDescription })
        if (state.isLoading) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Text("Waiting for Telegram Core…")
            }
        } else when (state.step) {
            org.vibetgram.gui.state.AuthUiState.AuthStep.PHONE_ENTRY -> {
                OutlinedTextField(
                    value = state.phoneInput,
                    onValueChange = auth::onPhoneChanged,
                    label = { Text("Phone number") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = auth::submitPhone, enabled = !state.isLoading) { Text("Next") }
                TextButton(onClick = auth::switchToQrCode) { Text("Use QR code") }
            }
            org.vibetgram.gui.state.AuthUiState.AuthStep.CODE_VERIFY -> {
                OutlinedTextField(
                    value = state.codeInput,
                    onValueChange = auth::onCodeChanged,
                    label = { Text("Code") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = auth::submitCode, enabled = !state.isLoading) { Text("Confirm") }
            }
            org.vibetgram.gui.state.AuthUiState.AuthStep.PASSWORD_2FA -> {
                OutlinedTextField(
                    value = state.passwordInput,
                    onValueChange = auth::onPasswordChanged,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = auth::submitPassword, enabled = !state.isLoading) { Text("Submit") }
            }
            org.vibetgram.gui.state.AuthUiState.AuthStep.QR_CODE -> {
                Card(Modifier.fillMaxWidth().semantics { contentDescription = "Login QR code" }) {
                    Text(state.qrCodeLink ?: "QR code unavailable", Modifier.padding(24.dp))
                }
                TextButton(onClick = auth::switchToPhoneEntry) { Text("Use phone instead") }
            }
            org.vibetgram.gui.state.AuthUiState.AuthStep.TERMS_REGISTRATION -> {
                OutlinedTextField(state.firstNameInput, auth::onFirstNameChanged, label = { Text("First name") })
                OutlinedTextField(state.lastNameInput, auth::onLastNameChanged, label = { Text("Last name") })
                Text(state.termsText ?: "Review Telegram terms before continuing.")
                Button(onClick = auth::submitRegistration, enabled = !state.isLoading) { Text("Accept and continue") }
            }
            org.vibetgram.gui.state.AuthUiState.AuthStep.AUTHORIZED -> Text("Account is ready")
        }
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ChatListContent(
    state: org.vibetgram.gui.screens.ChatListScreenRenderState,
    stateHolder: RootStateHolder,
    events: GuiEventHandler,
    modifier: Modifier = Modifier,
    slotNodes: Map<String, ModUiNode> = emptyMap()
) {
    Column(
        modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp)
    ) {
        Text("Chats", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(12.dp))
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = stateHolder.chatListStateHolder::onSearchQueryChanged,
            label = { Text("Search chats") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        state.connectionStatusBanner?.let { status ->
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) { Text(status, Modifier.padding(12.dp)) }
        }
        HorizontalDivider()
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (state.chatItems.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (state.searchQuery.isBlank()) "No chats yet" else "No matching chats")
            }
        } else LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(slotNodes.values.toList()) { node -> ModUiContent(node, events) }
            items(state.chatItems, key = { it.ref.id }) { chat ->
                val description = AccessibilitySemantics.chatItemContentDescription(
                    chat.title, chat.lastMessageSnippet, chat.unreadCount, chat.isPinned
                )
                TextButton(
                    onClick = { events.onNavigate(GuiRoute.Conversation(chat.ref)) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                        .semantics { contentDescription = description }
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(chat.lastMessageSnippet, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationContent(
    state: org.vibetgram.gui.screens.ConversationScreenRenderState?,
    composer: org.vibetgram.gui.screens.TextComposerRenderState?,
    stateHolder: RootStateHolder,
    events: GuiEventHandler,
    slotNodes: Map<String, ModUiNode>,
    modifier: Modifier = Modifier
) {
    if (state == null) {
        Text("Select a chat", modifier.padding(24.dp))
        return
    }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(state.chatInfo?.title ?: "Conversation", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = events::onBack, modifier = Modifier.semantics { contentDescription = "Back" }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
            items(state.messageList, key = { it.ref.id }) { message ->
                val description = state.messageAccessibilityDescriptions
                    .getOrNull(state.messageList.indexOf(message)) ?: message.text
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        color = if (message.isOutgoing) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        shape = if (message.isOutgoing) {
                            RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
                        } else {
                            RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
                        },
                        modifier = Modifier.widthIn(max = 360.dp)
                            .animateContentSize(
                                tween(stateHolder.uiState.value.theme.motion.durationBubblePopMs.toInt())
                            )
                            .semantics { contentDescription = description }
                    ) {
                        Text(message.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                }
            }
            items(slotNodes.filterKeys {
                it.startsWith("conversation.") || it.startsWith("message_bubble.")
            }.values.toList()) { node -> ModUiContent(node, events) }
        }
        if (composer != null) ComposerContent(
            composer,
            stateHolder,
            events,
            slotNodes.filterKeys { it.startsWith("composer.") }
        )
    }
}

@Composable
private fun ComposerContent(
    state: org.vibetgram.gui.screens.TextComposerRenderState,
    stateHolder: RootStateHolder,
    events: GuiEventHandler,
    slotNodes: Map<String, ModUiNode>
) {
    Column {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.text,
                onValueChange = stateHolder.composerStateHolder::onTextChanged,
                modifier = Modifier.weight(1f),
                label = { Text("Message") }
            )
            IconButton(
                onClick = stateHolder.composerStateHolder::sendMessage,
                enabled = state.canSend && !state.isSending,
                modifier = Modifier.size(56.dp).semantics { contentDescription = state.sendButtonContentDescription }
            ) {
                if (state.isSending) CircularProgressIndicator(Modifier.size(24.dp))
                else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
            }
        }
        slotNodes.values.forEach { node -> ModUiContent(node, events) }
    }
}

@Composable
private fun ModUiInput(node: ModUiNode.Input, events: GuiEventHandler) {
    var value by remember(node.actionId, node.initialValue) { mutableStateOf(node.initialValue) }
    OutlinedTextField(
        value = value,
        onValueChange = {
            value = it
            events.onModUiAction(node.actionId, mapOf("value" to it))
        },
        label = { Text(node.placeholder) },
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = node.contentDescription ?: node.placeholder
        }
    )
}

@Composable
private fun ModUiContent(node: ModUiNode, events: GuiEventHandler) {
    when (node) {
        is ModUiNode.Text -> Text(node.text)
        is ModUiNode.Button -> Button(
            onClick = { events.onModUiAction(node.actionId) },
            modifier = Modifier.semantics {
                contentDescription = node.contentDescription ?: node.label
            }
        ) { Text(node.label) }
        is ModUiNode.Icon -> Text(
            node.iconName,
            modifier = Modifier.semantics { contentDescription = node.contentDescription }
        )
        is ModUiNode.Badge -> Text(node.text)
        is ModUiNode.Input -> ModUiInput(node, events)
        is ModUiNode.Card -> Card { node.children.forEach { ModUiContent(it, events) } }
        is ModUiNode.Column -> Column(verticalArrangement = Arrangement.spacedBy(node.spacingDp.dp)) {
            node.children.forEach { ModUiContent(it, events) }
        }
        is ModUiNode.Row -> Row(horizontalArrangement = Arrangement.spacedBy(node.spacingDp.dp)) {
            node.children.forEach { ModUiContent(it, events) }
        }
    }
}

private fun Modifier.accessibleNavigation(onAction: (NavigationAction) -> Unit): Modifier =
    focusable().onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) {
            false
        } else {
            val navigationKey = when (event.key) {
                Key.DirectionUp -> NavigationKey.UP
                Key.DirectionDown -> NavigationKey.DOWN
                Key.DirectionLeft -> NavigationKey.LEFT
                Key.DirectionRight -> NavigationKey.RIGHT
                Key.Enter, Key.NumPadEnter -> NavigationKey.ENTER
                Key.Escape -> NavigationKey.BACK
                Key.Tab -> NavigationKey.TAB
                else -> null
            }
            navigationKey?.let {
                onAction(AccessibleInputNavigation.actionFor(it))
                it == NavigationKey.BACK
            } ?: false
        }
    }

private fun ResolvedTheme.composeColorScheme() = (if (colorScheme.isDark) {
    androidx.compose.material3.darkColorScheme()
} else {
    androidx.compose.material3.lightColorScheme()
}).copy(
    primary = color(colorScheme.primary),
    onPrimary = color(colorScheme.onPrimary),
    primaryContainer = color(colorScheme.primaryContainer),
    onPrimaryContainer = color(colorScheme.onPrimaryContainer),
    secondary = color(colorScheme.secondary),
    onSecondary = color(colorScheme.onSecondary),
    secondaryContainer = color(colorScheme.secondaryContainer),
    onSecondaryContainer = color(colorScheme.onSecondaryContainer),
    tertiary = color(colorScheme.tertiary),
    onTertiary = color(colorScheme.onTertiary),
    tertiaryContainer = color(colorScheme.tertiaryContainer),
    onTertiaryContainer = color(colorScheme.onTertiaryContainer),
    background = color(colorScheme.background),
    onBackground = color(colorScheme.onBackground),
    surface = color(colorScheme.surface),
    onSurface = color(colorScheme.onSurface),
    surfaceVariant = color(colorScheme.surfaceVariant),
    onSurfaceVariant = color(colorScheme.onSurfaceVariant),
    surfaceContainer = color(colorScheme.surfaceContainer),
    surfaceContainerHigh = color(colorScheme.surfaceContainerHigh),
    surfaceContainerHighest = color(colorScheme.surfaceContainerHighest),
    outline = color(colorScheme.outline),
    outlineVariant = color(colorScheme.outlineVariant),
    error = color(colorScheme.error),
    onError = color(colorScheme.onError),
    errorContainer = color(colorScheme.errorContainer),
    onErrorContainer = color(colorScheme.onErrorContainer)
)

private fun ResolvedTheme.composeShapes() = Shapes(
    extraSmall = shape(shapes.extraSmall),
    small = shape(shapes.small),
    medium = shape(shapes.medium),
    large = shape(shapes.large),
    extraLarge = shape(shapes.extraLarge)
)

private fun shape(radii: CornerRadiiDp): CornerBasedShape = RoundedCornerShape(
    topStart = radii.topStart.dp,
    topEnd = radii.topEnd.dp,
    bottomEnd = radii.bottomEnd.dp,
    bottomStart = radii.bottomStart.dp
)

private fun ResolvedTheme.composeTypography() = Typography(
    bodyLarge = composeStyle(typography.bodyLarge),
    bodyMedium = composeStyle(typography.bodyMedium),
    bodySmall = composeStyle(typography.bodySmall),
    titleLarge = composeStyle(typography.titleLarge),
    titleMedium = composeStyle(typography.titleMedium),
    titleSmall = composeStyle(typography.titleSmall),
    headlineLarge = composeStyle(typography.headlineLarge),
    headlineMedium = composeStyle(typography.headlineMedium),
    headlineSmall = composeStyle(typography.headlineSmall),
    labelLarge = composeStyle(typography.labelLarge),
    labelMedium = composeStyle(typography.labelMedium),
    labelSmall = composeStyle(typography.labelSmall)
)

private fun composeStyle(style: ExpressiveTextStyle) = androidx.compose.ui.text.TextStyle(
    fontSize = style.fontSizeSp.sp,
    lineHeight = style.lineHeightSp.sp,
    letterSpacing = style.letterSpacingSp.sp,
    fontWeight = androidx.compose.ui.text.font.FontWeight(style.fontWeight)
)

private fun color(color: ExpressiveColor) = androidx.compose.ui.graphics.Color(color.argb.toULong())
