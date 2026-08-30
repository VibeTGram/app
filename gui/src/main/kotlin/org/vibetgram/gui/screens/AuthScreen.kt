package org.vibetgram.gui.screens

import org.vibetgram.gui.accessibility.AccessibilitySemantics
import org.vibetgram.gui.state.AuthStateHolder
import org.vibetgram.gui.state.AuthUiState
import org.vibetgram.gui.theme.ResolvedTheme

/**
 * Material 3 Expressive Authorization screen placeholder and layout specification.
 * Normative reference: docs/roadmap.md Milestone 1 GUI slice.
 */
data class AuthScreenRenderState(
    val step: AuthUiState.AuthStep,
    val phoneInput: String,
    val codeInput: String,
    val passwordInput: String,
    val qrCodeLink: String?,
    val firstNameInput: String,
    val lastNameInput: String,
    val termsText: String?,
    val isLoading: Boolean,
    val errorMessage: String?,
    val title: String,
    val subtitle: String,
    val primaryButtonLabel: String,
    val accessibilityDescription: String
)

object AuthScreenRenderer {

    fun prepareRenderState(state: AuthUiState, theme: ResolvedTheme): AuthScreenRenderState {
        val (title, subtitle, btnLabel) = when (state.authStep) {
            AuthUiState.AuthStep.PHONE_ENTRY -> Triple(
                "Your Phone",
                "Please confirm your country code and enter your phone number.",
                "Next"
            )
            AuthUiState.AuthStep.CODE_VERIFY -> Triple(
                "Enter Code",
                "We've sent an SMS with an activation code to ${state.phoneNumber}.",
                "Confirm"
            )
            AuthUiState.AuthStep.PASSWORD_2FA -> Triple(
                "Two-Step Verification",
                "Enter your password${state.passwordHint?.let { " (Hint: $it)" } ?: ""}.",
                "Submit Password"
            )
            AuthUiState.AuthStep.QR_CODE -> Triple(
                "Quick Login with QR",
                "Open Telegram on another device and scan this QR code.",
                "Use Phone Instead"
            )
            AuthUiState.AuthStep.TERMS_REGISTRATION -> Triple(
                "Your Profile",
                "Enter your name and review the terms of service.",
                "Accept and Continue"
            )
            AuthUiState.AuthStep.AUTHORIZED -> Triple(
                "Logged In",
                "Account is ready.",
                "Done"
            )
        }

        val a11yDesc = "Authorization screen. Step: $title. $subtitle${state.errorMessage?.let { " Error: $it" } ?: ""}"

        return AuthScreenRenderState(
            step = state.authStep,
            phoneInput = state.phoneNumber,
            codeInput = state.authCode,
            passwordInput = state.password2Fa,
            qrCodeLink = state.qrCodeLink,
            firstNameInput = state.firstName,
            lastNameInput = state.lastName,
            termsText = state.termsOfService,
            isLoading = state.isLoading,
            errorMessage = state.errorMessage,
            title = title,
            subtitle = subtitle,
            primaryButtonLabel = btnLabel,
            accessibilityDescription = a11yDesc
        )
    }
}
