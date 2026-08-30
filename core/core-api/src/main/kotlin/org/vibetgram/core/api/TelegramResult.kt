package org.vibetgram.core.api

/** The only representation for expected Telegram operation failures. */
sealed interface TelegramResult<out T> {
    data class Success<T>(val value: T) : TelegramResult<T>
    data class Error(val error: TelegramError) : TelegramResult<Nothing>

    fun <R> map(transform: (T) -> R): TelegramResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Error -> this
    }
}

/** Stable, safe-to-display failure categories. */
sealed interface TelegramError {
    data object PermissionDenied : TelegramError
    data object NotFound : TelegramError

    data class RateLimited(val retryAfterSeconds: Long) : TelegramError {
        init {
            require(retryAfterSeconds >= 0) { "retry delay must not be negative" }
        }
    }

    data object NetworkUnavailable : TelegramError
    data object Conflict : TelegramError
    data object IncompatibleSchema : TelegramError
    data object UserConfirmationRequired : TelegramError
    data object Cancelled : TelegramError
    data object Unsupported : TelegramError
    data object UpstreamUnsupported : TelegramError

    /** Safe upstream code/message only; adapter exception types never cross the seam. */
    data class Upstream(val safeCode: Int, val safeMessage: String? = null) : TelegramError
}
