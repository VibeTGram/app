package org.vibetgram.core.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.vibetgram.core.api.AccountHandle

fun interface PinVerifier {
    fun verify(pin: CharArray): Boolean
}

fun interface BiometricAuthenticator {
    suspend fun authenticate(): Boolean
}

enum class AppLockState {
    LOCKED,
    UNLOCKED,
}

/** UI-only lock. It does not gate background TDLib/push work after device unlock. */
class AppLockController(
    private val pinVerifier: PinVerifier?,
    private val biometric: BiometricAuthenticator?,
) {
    private val stateFlow = MutableStateFlow(AppLockState.LOCKED)

    val state: AppLockState
        get() = stateFlow.value

    fun observeState(): StateFlow<AppLockState> = stateFlow.asStateFlow()

    fun unlockWithPin(pin: CharArray): Boolean {
        return try {
            if (pinVerifier?.verify(pin) == true) {
                stateFlow.value = AppLockState.UNLOCKED
                true
            } else {
                false
            }
        } finally {
            pin.fill('\u0000')
        }
    }

    suspend fun unlockWithBiometrics(): Boolean {
        if (biometric?.authenticate() != true) return false
        stateFlow.value = AppLockState.UNLOCKED
        return true
    }

    fun lock() {
        stateFlow.value = AppLockState.LOCKED
    }
}

sealed interface WebViewBindingResult {
    data object Bound : WebViewBindingResult
    data object RestartRequired : WebViewBindingResult
    data object Disabled : WebViewBindingResult
}

/**
 * Process-local startup broker for WebView's single-profile fallback.
 *
 * The caller must invoke [bindBeforeWebView] before constructing any WebView.
 * A second account cannot reuse the process; it requires a controlled restart.
 */
class WebViewProfileBroker {
    private var initialized = false
    private var restartRequired = false
    private var owner: AccountHandle? = null
    private var suffix: String? = null
    private val quarantinedSuffixes = mutableSetOf<String>()

    fun bindBeforeWebView(handle: AccountHandle, expectedSuffix: String): WebViewBindingResult {
        if (expectedSuffix.isBlank() || expectedSuffix in quarantinedSuffixes) {
            return WebViewBindingResult.Disabled
        }
        if (restartRequired) return WebViewBindingResult.RestartRequired
        if (!initialized) {
            initialized = true
            owner = handle
            suffix = expectedSuffix
            return WebViewBindingResult.Bound
        }
        return if (owner == handle && suffix == expectedSuffix) {
            WebViewBindingResult.Bound
        } else {
            WebViewBindingResult.RestartRequired
        }
    }

    fun verifyStartup(handle: AccountHandle, actualSuffix: String): Boolean =
        initialized && owner == handle && suffix == actualSuffix && actualSuffix !in quarantinedSuffixes

    fun closeAllForRestart() {
        initialized = false
        owner = null
        suffix = null
        restartRequired = true
    }

    /** Clears the process-switch gate only from the new process startup path. */
    fun resetAfterProcessStart() {
        initialized = false
        owner = null
        suffix = null
        restartRequired = false
    }

    fun quarantineSuffix(suffix: String) {
        quarantinedSuffixes += suffix
        if (this.suffix == suffix) closeAllForRestart()
    }

    fun isQuarantined(suffix: String): Boolean = suffix in quarantinedSuffixes
}
