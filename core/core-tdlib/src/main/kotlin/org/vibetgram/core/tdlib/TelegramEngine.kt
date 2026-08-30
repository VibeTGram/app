package org.vibetgram.core.tdlib

import kotlinx.coroutines.flow.StateFlow
import org.vibetgram.core.api.TelegramService

/** Lifecycle-aware Telegram service implemented by the single TDLib adapter. */
interface TelegramEngine : TelegramService, AutoCloseable {
    fun start()

    fun recoverProcess()

    fun observeAuthorization(): StateFlow<AuthorizationState>

    fun observeAuthorizationDetails(): StateFlow<AuthorizationDetails>

    suspend fun setAuthenticationPhoneNumber(phoneNumber: String): org.vibetgram.core.api.TelegramResult<Unit>

    suspend fun checkAuthenticationCode(code: CharArray): org.vibetgram.core.api.TelegramResult<Unit>

    suspend fun checkAuthenticationPassword(password: CharArray): org.vibetgram.core.api.TelegramResult<Unit>

    suspend fun requestQrCodeAuthentication(
        otherUserIds: List<Long> = emptyList(),
    ): org.vibetgram.core.api.TelegramResult<Unit>

    suspend fun registerUser(
        firstName: String,
        lastName: String,
        disableNotification: Boolean = false,
    ): org.vibetgram.core.api.TelegramResult<Unit>

    suspend fun logOut(): org.vibetgram.core.api.TelegramResult<Unit>
}
