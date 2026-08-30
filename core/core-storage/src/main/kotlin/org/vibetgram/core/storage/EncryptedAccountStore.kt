package org.vibetgram.core.storage

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** A schema step applied to a copy of an account store before it is committed. */
data class StorageMigration(
    val fromVersion: Int,
    val toVersion: Int,
    val migrate: (MutableMap<String, ByteArray>) -> Unit,
) {
    init {
        require(fromVersion >= 1) { "migration source version must be positive" }
        require(toVersion > fromVersion) { "migration must move forward" }
    }
}

class StorageFormatException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/**
 * Small encrypted, account-scoped key/value store used by the JVM bootstrap.
 *
 * The file is AES-GCM encrypted and every update is written to a sibling
 * temporary file before an atomic replacement. The implementation deliberately
 * exposes no SQL or filesystem path to callers after construction.
 */
class EncryptedAccountStore private constructor(
    private val path: Path,
    encryptionKey: ByteArray,
    initialSchemaVersion: Int,
    initialValues: Map<String, ByteArray>,
) : AutoCloseable {
    private val key = encryptionKey.copyOf()
    private val lock = Any()
    private var values = copyValues(initialValues)
    private var closed = false

    var schemaVersion: Int = initialSchemaVersion
        private set

    fun get(namespace: String, key: String): ByteArray? = synchronized(lock) {
        ensureOpen()
        values[storageKey(namespace, key)]?.copyOf()
    }

    fun put(namespace: String, key: String, value: ByteArray) {
        require(value.size <= MAX_VALUE_BYTES) { "account value is too large" }
        transaction { put(namespace, key, value) }
    }

    fun delete(namespace: String, key: String) {
        transaction { delete(namespace, key) }
    }

    /** Applies all changes to a copy and commits them as one encrypted file. */
    fun transaction(block: AccountStoreTransaction.() -> Unit) = synchronized(lock) {
        ensureOpen()
        val draft = copyValues(values)
        AccountStoreTransaction(draft).apply(block)
        writeSnapshot(schemaVersion, draft)
        values = draft
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            key.fill(0)
            values = mutableMapOf()
        }
    }

    private fun writeSnapshot(version: Int, snapshot: Map<String, ByteArray>) {
        val plaintext = encodeSnapshot(version, snapshot)
        try {
            val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(AAD)
            val encrypted = cipher.doFinal(plaintext)
            val encoded = ByteArrayOutputStream().use { output ->
                output.write(FILE_MAGIC)
                output.write(nonce)
                output.write(encrypted)
                output.toByteArray()
            }
            val parent = path.parent ?: Path.of(".")
            Files.createDirectories(parent)
            val temporary = Files.createTempFile(parent, path.fileName.toString(), ".tmp")
            try {
                Files.write(temporary, encoded, StandardOpenOption.TRUNCATE_EXISTING)
                try {
                    Files.move(
                        temporary,
                        path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
            encoded.fill(0)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun ensureOpen() {
        check(!closed) { "account store is closed" }
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val NONCE_BYTES = 12
        private const val MAX_VALUE_BYTES = 16 * 1024 * 1024
        private const val MAX_ENTRIES = 100_000
        private val FILE_MAGIC = byteArrayOf('V'.code.toByte(), 'G'.code.toByte(), 'S'.code.toByte(), 1)
        private val AAD = "vibetgram-account-store-v1".encodeToByteArray()

        fun open(
            path: Path,
            key: ByteArray,
            targetSchemaVersion: Int = 1,
            migrations: List<StorageMigration> = emptyList(),
        ): EncryptedAccountStore {
            require(targetSchemaVersion >= 1) { "target schema version must be positive" }
            validateKey(key)
            val loaded = if (Files.exists(path)) {
                decodeSnapshot(path, key)
            } else {
                Snapshot(1, emptyMap())
            }
            require(loaded.version <= targetSchemaVersion) {
                "store schema ${loaded.version} is newer than supported $targetSchemaVersion"
            }
            var version = loaded.version
            var values = copyValues(loaded.values)
            while (version < targetSchemaVersion) {
                val migration = migrations.singleOrNull { it.fromVersion == version }
                    ?: throw StorageFormatException("missing migration from schema $version")
                val before = copyValues(values)
                try {
                    migration.migrate(values)
                } catch (failure: Throwable) {
                    values = before
                    throw StorageFormatException("migration $version -> ${migration.toVersion} failed", failure)
                }
                require(migration.toVersion in (version + 1)..targetSchemaVersion) {
                    "migration target ${migration.toVersion} is outside requested schema $targetSchemaVersion"
                }
                version = migration.toVersion
            }
            val store = EncryptedAccountStore(path, key, version, values)
            if (!Files.exists(path) || version != loaded.version) {
                store.writeSnapshot(version, values)
            }
            return store
        }

        private fun decodeSnapshot(path: Path, key: ByteArray): Snapshot {
            val encoded = try {
                Files.readAllBytes(path)
            } catch (failure: Exception) {
                throw StorageFormatException("cannot read encrypted account store", failure)
            }
            try {
                require(encoded.size > FILE_MAGIC.size + NONCE_BYTES) { "store is truncated" }
                require(encoded.copyOfRange(0, FILE_MAGIC.size).contentEquals(FILE_MAGIC)) {
                    "unknown account store format"
                }
                val nonce = encoded.copyOfRange(FILE_MAGIC.size, FILE_MAGIC.size + NONCE_BYTES)
                val ciphertext = encoded.copyOfRange(FILE_MAGIC.size + NONCE_BYTES, encoded.size)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
                cipher.updateAAD(AAD)
                return decodePlaintext(cipher.doFinal(ciphertext))
            } catch (failure: Exception) {
                throw StorageFormatException("account store authentication or decoding failed", failure)
            } finally {
                encoded.fill(0)
            }
        }

        private fun decodePlaintext(plaintext: ByteArray): Snapshot =
            DataInputStream(ByteArrayInputStream(plaintext)).use { input ->
                val version = input.readInt()
                require(version >= 1) { "invalid store schema" }
                val count = input.readInt()
                require(count in 0..MAX_ENTRIES) { "invalid store entry count" }
                val values = LinkedHashMap<String, ByteArray>(count)
                repeat(count) {
                    val nameLength = input.readInt()
                    require(nameLength in 1..MAX_NAME_BYTES) { "invalid store key" }
                    val name = ByteArray(nameLength).also(input::readFully).decodeToString()
                    val valueLength = input.readInt()
                    require(valueLength in 0..MAX_VALUE_BYTES) { "invalid store value" }
                    values[name] = ByteArray(valueLength).also(input::readFully)
                }
                require(input.read() == -1) { "trailing store data" }
                Snapshot(version, values)
            }

        private fun encodeSnapshot(version: Int, values: Map<String, ByteArray>): ByteArray =
            ByteArrayOutputStream().use { bytes ->
                DataOutputStream(bytes).use { output ->
                    output.writeInt(version)
                    output.writeInt(values.size)
                    values.toSortedMap().forEach { (name, value) ->
                        val nameBytes = name.encodeToByteArray()
                        require(nameBytes.size in 1..MAX_NAME_BYTES) { "invalid store key" }
                        require(value.size <= MAX_VALUE_BYTES) { "account value is too large" }
                        output.writeInt(nameBytes.size)
                        output.write(nameBytes)
                        output.writeInt(value.size)
                        output.write(value)
                    }
                }
                bytes.toByteArray()
            }

        private fun validateKey(key: ByteArray) {
            require(key.size == 16 || key.size == 24 || key.size == 32) {
                "account encryption key must be 128, 192, or 256 bits"
            }
        }

        private fun copyValues(values: Map<String, ByteArray>): MutableMap<String, ByteArray> =
            values.mapValuesTo(LinkedHashMap()) { (_, value) -> value.copyOf() }

        private const val MAX_NAME_BYTES = 4 * 1024
        private data class Snapshot(val version: Int, val values: Map<String, ByteArray>)
    }
}

class AccountStoreTransaction internal constructor(
    private val values: MutableMap<String, ByteArray>,
) {
    fun get(namespace: String, key: String): ByteArray? = values[storageKey(namespace, key)]?.copyOf()

    fun put(namespace: String, key: String, value: ByteArray) {
        require(value.size <= 16 * 1024 * 1024) { "account value is too large" }
        values[storageKey(namespace, key)] = value.copyOf()
    }

    fun delete(namespace: String, key: String) {
        values.remove(storageKey(namespace, key))
    }
}

private fun storageKey(namespace: String, key: String): String {
    require(namespace.isNotBlank() && key.isNotBlank()) { "storage namespace and key must not be blank" }
    require('/' !in namespace && '/' !in key && '\u0000' !in namespace && '\u0000' !in key) {
        "storage namespace and key contain a forbidden character"
    }
    return "$namespace/$key"
}
