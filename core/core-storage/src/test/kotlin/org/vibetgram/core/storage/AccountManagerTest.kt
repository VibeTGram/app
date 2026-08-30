package org.vibetgram.core.storage

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class AccountManagerTest {
    @Test
    fun `account registry and encrypted data survive manager recreation`() {
        val root = Files.createTempDirectory("vibetgram-accounts")
        val protector = InMemoryKeyProtector()
        val firstManager = AccountManager(root, protector)
        val first = firstManager.createAccount()
        assertIs<AccountStartResult.Started>(firstManager.start(first.handle))
        first.store.put("bootstrap", "marker", "persisted".encodeToByteArray())
        val originalRoot = first.paths.root
        firstManager.close()

        val secondManager = AccountManager(root, protector)
        val restoredHandle = assertNotNull(secondManager.accounts().singleOrNull())
        val restored = assertIs<AccountStartResult.Started>(secondManager.start(restoredHandle))

        assertEquals(originalRoot, restored.context.paths.root)
        assertEquals(
            "persisted",
            restored.context.store.get("bootstrap", "marker")?.decodeToString(),
        )
    }

    @Test
    fun `accounts get opaque isolated directories and keys`() {
        val manager = AccountManager(
            root = Files.createTempDirectory("vibetgram-accounts"),
            keyProtector = InMemoryKeyProtector(),
        )
        val first = manager.createAccount()
        val second = manager.createAccount()

        assertNotEquals(first.handle, second.handle)
        assertNotEquals(first.paths.root, second.paths.root)
        assertNotEquals(first.paths.keyAlias, second.paths.keyAlias)
        assertFalse(first.paths.root.toString().contains(first.handle.toString()))
        assertIs<AccountStartResult.Started>(manager.start(first.handle))
        assertIs<AccountStartResult.Started>(manager.start(second.handle))
        first.store.put("addon", "key", byteArrayOf(1))
        assertEquals(null, second.store.get("addon", "key"))
    }

    @Test
    fun `locked keystore defers account until first unlock`() {
        val protector = InMemoryKeyProtector(locked = true)
        val manager = AccountManager(Files.createTempDirectory("vibetgram-accounts"), protector)
        val account = manager.createAccount()

        assertIs<AccountStartResult.AwaitingFirstUnlock>(manager.start(account.handle))
        assertEquals(AccountStatus.AWAITING_FIRST_UNLOCK, manager.status(account.handle))
        protector.locked = false
        manager.onFirstUnlock()

        assertEquals(AccountStatus.RUNNING, manager.status(account.handle))
    }

    @Test
    fun `process recovery recreates runtime without changing account handle`() {
        val runtimes = mutableListOf<RecordingRuntime>()
        val manager = AccountManager(
            Files.createTempDirectory("vibetgram-accounts"),
            InMemoryKeyProtector(),
            runtimeFactory = AccountRuntimeFactory { _, _ -> RecordingRuntime().also(runtimes::add) },
        )
        val account = manager.createAccount()
        manager.start(account.handle)
        manager.recoverProcess()

        assertEquals(2, runtimes.size)
        assertTrue(runtimes[0].closed)
        assertTrue(runtimes[1].started)
        assertTrue(manager.accounts().contains(account.handle))
    }

    @Test
    fun `logout requires confirmation and removes key and account data`() {
        val protector = InMemoryKeyProtector()
        val root = Files.createTempDirectory("vibetgram-accounts")
        val manager = AccountManager(root, protector)
        val account = manager.createAccount()
        manager.start(account.handle)
        account.store.put("core", "value", byteArrayOf(8))

        assertIs<LogoutResult.ConfirmationRequired>(manager.logout(account.handle, confirmed = false))
        assertIs<LogoutResult.Deleted>(manager.logout(account.handle, confirmed = true))
        assertFalse(Files.exists(account.paths.root))
        assertTrue(protector.deletedAliases.contains(account.paths.keyAlias))
        assertFalse(manager.accounts().contains(account.handle))
    }

    private class RecordingRuntime : AccountRuntime {
        var started = false
        var closed = false
        override fun start() { started = true }
        override fun recoverProcess() { started = true }
        override fun close() { closed = true }
    }
}