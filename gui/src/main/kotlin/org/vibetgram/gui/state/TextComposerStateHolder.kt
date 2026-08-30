package org.vibetgram.gui.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.vibetgram.gui.domain.AccountHandle
import org.vibetgram.gui.domain.ChatRef
import org.vibetgram.gui.domain.Draft
import org.vibetgram.gui.domain.DraftService
import org.vibetgram.gui.domain.MessageComposer
import org.vibetgram.gui.domain.MessageRef
import org.vibetgram.gui.domain.OutgoingContent
import org.vibetgram.gui.domain.SendOptions
import org.vibetgram.gui.domain.TelegramResult

data class TextComposerUiState(
    val chatRef: ChatRef? = null,
    val inputText: String = "",
    val isSending: Boolean = false,
    val canSend: Boolean = false,
    val isRecordingVoice: Boolean = false,
    val errorMessage: String? = null
)

class TextComposerStateHolder(
    private val composer: MessageComposer,
    private val draftService: DraftService,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(TextComposerUiState())
    val uiState: StateFlow<TextComposerUiState> = _uiState.asStateFlow()

    private var activeAccount: AccountHandle? = null
    private var draftObserverJob: Job? = null
    private var draftSaveJob: Job? = null

    fun setAccount(account: AccountHandle) {
        activeAccount = account
    }

    fun bindChat(chatRef: ChatRef) {
        draftObserverJob?.cancel()
        draftSaveJob?.cancel()
        _uiState.value = _uiState.value.copy(
            chatRef = chatRef,
            inputText = "",
            canSend = false,
            errorMessage = null
        )
        val account = activeAccount ?: return
        draftObserverJob = scope.launch {
            draftService.observeDraft(account, chatRef).collect { draft ->
                if (draft != null && _uiState.value.inputText.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        inputText = draft.text,
                        canSend = draft.text.isNotBlank()
                    )
                }
            }
        }
    }

    fun onTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(
            inputText = text,
            canSend = text.isNotBlank(),
            errorMessage = null
        )
        val account = activeAccount ?: return
        val chat = _uiState.value.chatRef ?: return
        draftSaveJob?.cancel()
        draftSaveJob = scope.launch {
            if (text.isNotBlank()) {
                draftService.saveDraft(account, chat, Draft(text))
            } else {
                draftService.clearDraft(account, chat)
            }
        }
    }

    fun sendMessage(replyTo: MessageRef? = null, onSent: ((MessageRef) -> Unit)? = null) {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        val account = activeAccount ?: return
        val chat = _uiState.value.chatRef ?: return

        _uiState.value = _uiState.value.copy(isSending = true, errorMessage = null)
        draftSaveJob?.cancel()
        scope.launch {
            val content = OutgoingContent.Text(text)
            val options = SendOptions(replyToMessageRef = replyTo)
            when (val res = composer.sendMessage(account, chat, content, options)) {
                is TelegramResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        inputText = "",
                        canSend = false,
                        isSending = false
                    )
                    draftService.clearDraft(account, chat)
                    onSent?.invoke(res.value)
                }
                is TelegramResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        errorMessage = "Failed to send message: ${res.error}"
                    )
                }
            }
        }
    }

    fun clear() {
        _uiState.value = _uiState.value.copy(inputText = "", canSend = false, errorMessage = null)
        val account = activeAccount ?: return
        val chat = _uiState.value.chatRef ?: return
        scope.launch {
            draftService.clearDraft(account, chat)
        }
    }
}
