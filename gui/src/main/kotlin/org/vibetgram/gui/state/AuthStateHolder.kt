package org.vibetgram.gui.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.vibetgram.gui.domain.AuthState
import org.vibetgram.gui.domain.AuthorizationService
import org.vibetgram.gui.domain.TelegramError
import org.vibetgram.gui.domain.TelegramResult

data class AuthUiState(
    val authStep: AuthStep = AuthStep.PHONE_ENTRY,
    val phoneNumber: String = "",
    val authCode: String = "",
    val codeLength: Int = 5,
    val password2Fa: String = "",
    val passwordHint: String? = null,
    val qrCodeLink: String? = null,
    val firstName: String = "",
    val lastName: String = "",
    val termsOfService: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    enum class AuthStep {
        PHONE_ENTRY,
        CODE_VERIFY,
        PASSWORD_2FA,
        QR_CODE,
        TERMS_REGISTRATION,
        AUTHORIZED
    }
}

class AuthStateHolder(
    private val authService: AuthorizationService,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            authService.observeAuthState().collect { state ->
                reduceAuthState(state)
            }
        }
    }

    private fun reduceAuthState(state: AuthState) {
        when (state) {
            is AuthState.Uninitialized, is AuthState.WaitTdlibParameters -> {
                _uiState.value = _uiState.value.copy(
                    authStep = AuthUiState.AuthStep.PHONE_ENTRY,
                    isLoading = true,
                    errorMessage = null
                )
            }
            is AuthState.WaitPhoneNumber -> {
                _uiState.value = _uiState.value.copy(
                    authStep = AuthUiState.AuthStep.PHONE_ENTRY,
                    isLoading = false,
                    errorMessage = null
                )
            }
            is AuthState.WaitCode -> {
                _uiState.value = _uiState.value.copy(
                    authStep = AuthUiState.AuthStep.CODE_VERIFY,
                    phoneNumber = state.phone,
                    codeLength = state.codeLength,
                    isLoading = false,
                    errorMessage = null
                )
            }
            is AuthState.WaitPassword -> {
                _uiState.value = _uiState.value.copy(
                    authStep = AuthUiState.AuthStep.PASSWORD_2FA,
                    passwordHint = state.hint,
                    isLoading = false,
                    errorMessage = null
                )
            }
            is AuthState.WaitQrCode -> {
                _uiState.value = _uiState.value.copy(
                    authStep = AuthUiState.AuthStep.QR_CODE,
                    qrCodeLink = state.link,
                    isLoading = false,
                    errorMessage = null
                )
            }
            is AuthState.WaitRegistration -> {
                _uiState.value = _uiState.value.copy(
                    authStep = AuthUiState.AuthStep.TERMS_REGISTRATION,
                    termsOfService = state.termsOfService,
                    isLoading = false,
                    errorMessage = null
                )
            }
            is AuthState.Ready -> {
                _uiState.value = _uiState.value.copy(
                    authStep = AuthUiState.AuthStep.AUTHORIZED,
                    authCode = "",
                    password2Fa = "",
                    isLoading = false,
                    errorMessage = null
                )
            }
            is AuthState.Closed -> {
                _uiState.value = _uiState.value.copy(
                    authStep = AuthUiState.AuthStep.PHONE_ENTRY,
                    authCode = "",
                    password2Fa = "",
                    isLoading = false,
                    errorMessage = null
                )
            }
        }
    }

    fun onPhoneChanged(phone: String) {
        _uiState.value = _uiState.value.copy(phoneNumber = phone, errorMessage = null)
    }

    fun submitPhone() {
        val phone = _uiState.value.phoneNumber
        if (phone.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter a valid phone number")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        scope.launch {
            when (val res = authService.setPhoneNumber(phone)) {
                is TelegramResult.Success -> {
                    // State updated via flow observer
                }
                is TelegramResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = formatError(res.error)
                    )
                }
            }
        }
    }

    fun onCodeChanged(code: String) {
        _uiState.value = _uiState.value.copy(authCode = code, errorMessage = null)
        if (code.length == _uiState.value.codeLength) {
            submitCode(code)
        }
    }

    fun submitCode(code: String = _uiState.value.authCode) {
        if (code.isBlank()) return
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        scope.launch {
            when (val res = authService.checkAuthCode(code)) {
                is TelegramResult.Success -> { /* Handled via flow */ }
                is TelegramResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = formatError(res.error)
                    )
                }
            }
        }
    }

    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(password2Fa = password, errorMessage = null)
    }

    fun submitPassword() {
        val password = _uiState.value.password2Fa
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        scope.launch {
            when (val res = authService.checkPassword(password)) {
                is TelegramResult.Success -> { /* Handled via flow */ }
                is TelegramResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = formatError(res.error)
                    )
                }
            }
        }
    }

    fun switchToQrCode() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        scope.launch {
            when (val res = authService.requestQrCode()) {
                is TelegramResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        authStep = AuthUiState.AuthStep.QR_CODE,
                        qrCodeLink = res.value,
                        isLoading = false
                    )
                }
                is TelegramResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = formatError(res.error)
                    )
                }
            }
        }
    }

    fun switchToPhoneEntry() {
        _uiState.value = _uiState.value.copy(
            authStep = AuthUiState.AuthStep.PHONE_ENTRY,
            qrCodeLink = null,
            errorMessage = null
        )
    }

    fun onFirstNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(firstName = name, errorMessage = null)
    }

    fun onLastNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(lastName = name, errorMessage = null)
    }

    fun submitRegistration() {
        val first = _uiState.value.firstName
        val last = _uiState.value.lastName.takeIf { it.isNotBlank() }
        if (first.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "First name is required")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        scope.launch {
            when (val res = authService.acceptTermsAndRegister(first, last)) {
                is TelegramResult.Success -> { /* Handled via flow */ }
                is TelegramResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = formatError(res.error)
                    )
                }
            }
        }
    }

    private fun formatError(error: TelegramError): String {
        return when (error) {
            is TelegramError.Upstream -> error.safeMessage ?: "Error ${error.safeCode}"
            is TelegramError.NetworkUnavailable -> "Network unavailable. Please check your connection."
            is TelegramError.RateLimited -> "Too many attempts. Please wait ${error.retryAfterSeconds}s."
            is TelegramError.PermissionDenied -> "Permission denied."
            is TelegramError.NotFound -> "Resource not found."
            is TelegramError.Conflict -> "Operation conflict."
            is TelegramError.Unsupported -> "Unsupported operation."
            is TelegramError.Cancelled -> "Operation cancelled."
        }
    }
}
