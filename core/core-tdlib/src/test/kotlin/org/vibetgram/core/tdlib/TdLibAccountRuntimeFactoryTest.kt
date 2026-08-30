package org.vibetgram.core.tdlib

import java.nio.file.Files
import org.vibetgram.core.storage.AccountManager
import org.vibetgram.core.storage.AccountStartResult
import org.vibetgram.core.storage.InMemoryKeyProtector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TdLibAccountRuntimeFactoryTest {
    @Test
    fun `binds isolated account paths and unwrapped key to one engine`() {
        val client = CapturingClient()
        val factory = TdLibAccountRuntimeFactory(
            clientManager = object : ClientManager {
                override fun createClient(): TdClient = client
            },
            credentials = TdLibCredentials(123, "api-hash", "Pixel", "Android 16", "VibeTGram test"),
        )
        val manager = AccountManager(
            Files.createTempDirectory("vibetgram-tdlib-account"),
            InMemoryKeyProtector(),
            runtimeFactory = factory,
        )
        val account = manager.createAccount()

        assertIs<AccountStartResult.Started>(manager.start(account.handle))

        val setup = assertIs<TdFunction.SetTdlibParameters>(client.functions.single())
        assertEquals(account.paths.tdlibDirectory.toString(), setup.parameters.databaseDirectory)
        assertEquals(account.paths.filesDirectory.toString(), setup.parameters.filesDirectory)
        assertEquals(32, client.databaseEncryptionKeySizeAtSend)
        assertTrue(client.receivedNonZeroDatabaseEncryptionKey)
        assertTrue(setup.parameters.databaseEncryptionKey.all { it == 0.toByte() })
        assertTrue(factory.engine(account.handle) != null)

        manager.close()
        assertTrue(client.closed)
    }
}

private class CapturingClient : TdClient {
    val functions = mutableListOf<TdFunction>()
    var closed = false
    var databaseEncryptionKeySizeAtSend = -1
        private set
    var receivedNonZeroDatabaseEncryptionKey = false
        private set
    private var requestId = 0L

    override fun setUpdateHandler(handler: (TdUpdate) -> Unit) = Unit

    override fun send(function: TdFunction, callback: (TdResult) -> Unit): Long {
        if (function is TdFunction.SetTdlibParameters) {
            databaseEncryptionKeySizeAtSend = function.parameters.databaseEncryptionKey.size
            receivedNonZeroDatabaseEncryptionKey =
                function.parameters.databaseEncryptionKey.any { it != 0.toByte() }
        }
        functions += function
        return ++requestId
    }

    override fun close() {
        closed = true
    }
}
