package org.vibetgram.core.api

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandlesTest {
    @Test
    fun `chat references accept signed Telegram chat identifiers`() {
        assertEquals(-100123L, ChatRef(-100123L).value)
    }

    @Test
    fun `media and file handles are scoped and expire without exposing tokens`() {
        val account = AccountHandle.issue()
        val principal = PrincipalHandle.issue()
        val scope = HandleScope(account, principal)
        val expiresAt = Instant.parse("2030-01-01T00:00:00Z")
        val file = FileHandle.issue(scope, expiresAt)
        val media = MediaHandle.issue(scope, expiresAt)

        assertTrue(file.isValidAt(Instant.parse("2029-12-31T23:59:59Z")))
        assertFalse(file.isValidAt(expiresAt))
        assertTrue(media.isValidAt(Instant.parse("2029-12-31T23:59:59Z")))
        assertFalse(media.isValidAt(expiresAt))
        assertEquals(
            "FileHandle(scope=HandleScope(account=AccountHandle(***), principal=PrincipalHandle(***)), " +
                "expiresAt=2030-01-01T00:00:00Z)",
            file.toString(),
        )
        assertEquals(
            "MediaHandle(scope=HandleScope(account=AccountHandle(***), principal=PrincipalHandle(***)), " +
                "expiresAt=2030-01-01T00:00:00Z)",
            media.toString(),
        )
    }
}
