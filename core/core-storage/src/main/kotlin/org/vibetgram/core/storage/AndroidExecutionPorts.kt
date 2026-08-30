package org.vibetgram.core.storage

import org.vibetgram.core.api.AccountHandle

enum class PushTransport {
    GMS_FCM,
    MICROG_FCM,
}

sealed interface PushRegistrationResult {
    data class Registered(val transport: PushTransport) : PushRegistrationResult
    data class Unavailable(val reason: String) : PushRegistrationResult
}

fun interface EncryptedPushRegistrationPort {
    fun register(account: AccountHandle, encryptionRequired: Boolean): PushRegistrationResult
}

sealed interface BackgroundPortResult {
    data object Started : BackgroundPortResult
    data class Unavailable(val reason: String) : BackgroundPortResult
}

/** Starts foreground synchronization together with its mandatory persistent notification. */
fun interface ForegroundSyncPort {
    fun startWithPersistentNotification(account: AccountHandle): BackgroundPortResult
}

sealed interface BackgroundActivation {
    data class PushActive(val transport: PushTransport) : BackgroundActivation
    data object ForegroundActive : BackgroundActivation
    data class SyncOnOpen(
        val pushFailure: String,
        val foregroundFailure: String?,
    ) : BackgroundActivation
}

/**
 * Selects the documented background path without converting missing FCM or a
 * rejected notification/foreground-service request into a successful state.
 */
class BackgroundExecutionCoordinator(
    private val push: EncryptedPushRegistrationPort,
    private val foreground: ForegroundSyncPort,
) {
    fun activate(account: AccountHandle, allowForegroundFallback: Boolean): BackgroundActivation {
        return when (val pushResult = push.register(account, encryptionRequired = true)) {
            is PushRegistrationResult.Registered -> BackgroundActivation.PushActive(pushResult.transport)
            is PushRegistrationResult.Unavailable -> {
                if (!allowForegroundFallback) {
                    BackgroundActivation.SyncOnOpen(pushResult.reason, foregroundFailure = null)
                } else {
                    when (val foregroundResult = foreground.startWithPersistentNotification(account)) {
                        BackgroundPortResult.Started -> BackgroundActivation.ForegroundActive
                        is BackgroundPortResult.Unavailable -> BackgroundActivation.SyncOnOpen(
                            pushFailure = pushResult.reason,
                            foregroundFailure = foregroundResult.reason,
                        )
                    }
                }
            }
        }
    }
}
