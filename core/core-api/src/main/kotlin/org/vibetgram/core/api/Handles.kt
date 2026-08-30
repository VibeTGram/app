package org.vibetgram.core.api

import java.util.UUID

/** Opaque host-issued identity for one account context. */
@JvmInline
value class AccountHandle private constructor(private val token: String) {
    override fun toString(): String = "AccountHandle(***)"

    companion object {
        /** The trusted composition root issues handles and passes them to adapters. */
        fun issue(): AccountHandle = AccountHandle(UUID.randomUUID().toString())
    }
}

/** Opaque host-issued identity for an addon or first-party principal. */
@JvmInline
value class PrincipalHandle private constructor(private val token: String) {
    override fun toString(): String = "PrincipalHandle(***)"

    companion object {
        fun issue(): PrincipalHandle = PrincipalHandle(UUID.randomUUID().toString())
    }
}

/** The account and principal scope to which an expiring resource handle is bound. */
data class HandleScope(
    val account: AccountHandle,
    val principal: PrincipalHandle,
)

/** Stable Telegram chat identifier. It is meaningful only with its account handle. */
@JvmInline
value class ChatRef(val value: Long) {
    init {
        // TDLib uses signed identifiers; supergroups and channels are negative.
        require(value != 0L) { "chat reference must not be zero" }
    }
}

/** Stable message identifier, scoped to its chat and account operation. */
data class MessageRef(
    val chat: ChatRef,
    val value: Long,
) {
    init {
        require(value > 0) { "message reference must be positive" }
    }
}

/**
 * Opaque, expiring reference to a TDLib-managed file.
 *
 * No local path or file descriptor is part of this contract. Adapters resolve the
 * reference inside the account/principal scope and only while it is valid.
 */
class FileHandle private constructor(
    val scope: HandleScope,
    val expiresAt: java.time.Instant,
    private val token: String,
) {
    fun isValidAt(now: java.time.Instant): Boolean = now.isBefore(expiresAt)

    override fun equals(other: Any?): Boolean =
        other is FileHandle && scope == other.scope && expiresAt == other.expiresAt && token == other.token

    override fun hashCode(): Int = 31 * (31 * scope.hashCode() + expiresAt.hashCode()) + token.hashCode()

    override fun toString(): String = "FileHandle(scope=$scope, expiresAt=$expiresAt)"

    companion object {
        fun issue(
            scope: HandleScope,
            expiresAt: java.time.Instant,
        ): FileHandle = FileHandle(scope, expiresAt, UUID.randomUUID().toString())
    }
}

/** Opaque, expiring reference to imported or Telegram media. */
class MediaHandle private constructor(
    val scope: HandleScope,
    val expiresAt: java.time.Instant,
    private val token: String,
) {
    fun isValidAt(now: java.time.Instant): Boolean = now.isBefore(expiresAt)

    override fun equals(other: Any?): Boolean =
        other is MediaHandle && scope == other.scope && expiresAt == other.expiresAt && token == other.token

    override fun hashCode(): Int = 31 * (31 * scope.hashCode() + expiresAt.hashCode()) + token.hashCode()

    override fun toString(): String = "MediaHandle(scope=$scope, expiresAt=$expiresAt)"

    companion object {
        fun issue(
            scope: HandleScope,
            expiresAt: java.time.Instant,
        ): MediaHandle = MediaHandle(scope, expiresAt, UUID.randomUUID().toString())
    }
}
