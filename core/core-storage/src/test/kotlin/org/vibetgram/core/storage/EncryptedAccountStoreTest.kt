package org.vibetgram.core.storage

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncryptedAccountStoreTest {
    @Test
    fun `records are encrypted and survive reopen`() {
        val directory = Files.createTempDirectory("vibetgram-store")
        val file = directory.resolve("account.store")
        val key = ByteArray(32) { (it + 1).toByte() }

        EncryptedAccountStore.open(file, key).use { store ->
            store.put("addon-a", "token", "secret-value".encodeToByteArray())
        }

        assertTrue(!Files.readAllBytes(file).toString(Charsets.ISO_8859_1).contains("secret-value"))
        EncryptedAccountStore.open(file, key).use { store ->
            assertContentEquals("secret-value".encodeToByteArray(), store.get("addon-a", "token"))
            assertNull(store.get("addon-b", "token"))
        }
    }

    @Test
    fun `transaction does not publish changes when mutation fails`() {
        val file = Files.createTempDirectory("vibetgram-store").resolve("account.store")
        EncryptedAccountStore.open(file, ByteArray(32) { 7 }).use { store ->
            store.put("core", "stable", byteArrayOf(1))
            assertFailsWith<IllegalStateException> {
                store.transaction {
                    put("core", "stable", byteArrayOf(2))
                    error("abort")
                }
            }
            assertContentEquals(byteArrayOf(1), store.get("core", "stable"))
        }
    }

    @Test
    fun `schema migrations are applied transactionally on open`() {
        val file = Files.createTempDirectory("vibetgram-store").resolve("account.store")
        val key = ByteArray(32) { 9 }
        EncryptedAccountStore.open(file, key).use { it.put("core", "old", byteArrayOf(4)) }

        EncryptedAccountStore.open(
            path = file,
            key = key,
            targetSchemaVersion = 2,
            migrations = listOf(
                StorageMigration(1, 2) { values ->
                    values["core/new"] = values.remove("core/old") ?: error("missing old value")
                },
            ),
        ).use { store ->
            assertNull(store.get("core", "old"))
            assertContentEquals(byteArrayOf(4), store.get("core", "new"))
            assertTrue(store.schemaVersion == 2)
        }
    }

    @Test
    fun `wrong key cannot open the store`() {
        val file = Files.createTempDirectory("vibetgram-store").resolve("account.store")
        EncryptedAccountStore.open(file, ByteArray(32) { 1 }).use { it.put("core", "value", byteArrayOf(1)) }

        assertFailsWith<StorageFormatException> {
            EncryptedAccountStore.open(file, ByteArray(32) { 2 })
        }
    }
}