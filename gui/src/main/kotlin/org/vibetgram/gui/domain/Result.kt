package org.vibetgram.gui.domain

/**
 * Result and error types for semantic operations.
 * Normative reference: docs/architecture/system-architecture.md section 6.1
 */

sealed interface TelegramError {
    data object PermissionDenied : TelegramError
    data object NotFound : TelegramError
    data class RateLimited(val retryAfterSeconds: Long) : TelegramError
    data object NetworkUnavailable : TelegramError
    data object Conflict : TelegramError
    data object Unsupported : TelegramError
    data object Cancelled : TelegramError
    data class Upstream(val safeCode: Int, val safeMessage: String?) : TelegramError
}

sealed interface TelegramResult<out T> {
    data class Success<out T>(val value: T) : TelegramResult<T>
    data class Failure(val error: TelegramError) : TelegramResult<Nothing>

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw IllegalStateException("Telegram operation failed with error: $error")
    }

    companion object {
        fun <T> success(value: T): TelegramResult<T> = Success(value)
        fun <T> failure(error: TelegramError): TelegramResult<T> = Failure(error)
    }
}
