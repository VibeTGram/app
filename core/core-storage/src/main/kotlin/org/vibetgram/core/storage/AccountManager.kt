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
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.vibetgram.core.api.AccountHandle

/** A wrapped account data key. Implementations must not expose the wrapping key. */
class WrappedAccountKey(bytes: ByteArray) {
    private val encoded = bytes.copyOf()

    fun copyBytes(): ByteArray = encoded.copyOf()
}

/** Android's Keystore adapter implements this port; tests can use the in-memory port. */
interface KeyProtector {
    fun wrap(alias: String, key: ByteArray): WrappedAccountKey
    fun unwrap(alias: String, wrappedKey: WrappedAccountKey): ByteArray
    fun delete(alias: String)
}

/** Signals that the Android Keystore is unavailable until the first device unlock. */
class KeystoreLockedException : IllegalStateException("keystore is locked until first device unlock")

/** Deterministic test port with the same locked/unlock behavior as a device-protected key. */
class InMemoryKeyProtector(
    @Volatile var locked: Boolean = false,
) : KeyProtector {
    private val keys = mutableMapOf<String, ByteArray>()
    val deletedAliases: MutableSet<String> = linkedSetOf()

    override fun wrap(alias: String, key: ByteArray): WrappedAccountKey = synchronized(keys) {
        require(alias.isNotBlank())
        val wrappingKey = ByteArray(32).also(SecureRandom()::nextBytes)
        keys.put(alias, wrappingKey)?.fill(0)
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(wrappingKey, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(alias.encodeToByteArray())
        WrappedAccountKey(nonce + cipher.doFinal(key))
    }

    override fun unwrap(alias: String, wrappedKey: WrappedAccountKey): ByteArray = synchronized(keys) {
        if (locked) throw KeystoreLockedException()
        val wrappingKey = keys[alias] ?: error("wrapping key is unavailable")
        val encoded = wrappedKey.copyBytes()
        require(encoded.size > 12) { "wrapped account key is truncated" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(wrappingKey, "AES"),
            GCMParameterSpec(128, encoded.copyOfRange(0, 12)),
        )
        cipher.updateAAD(alias.encodeToByteArray())
        cipher.doFinal(encoded.copyOfRange(12, encoded.size))
    }

    override fun delete(alias: String) = synchronized(keys) {
        keys.remove(alias)?.fill(0)
        deletedAliases += alias
    }
}

/** All account-owned paths. The directory name and suffix are random, never Telegram IDs. */
data class AccountDataPaths(
    val root: Path,
    val tdlibDirectory: Path,
    val databaseFile: Path,
    val filesDirectory: Path,
    val addonDirectory: Path,
    val webViewDirectory: Path,
    val keyAlias: String,
    val webViewSuffix: String,
)

private fun createAccountDataPaths(
    root: Path,
    storageId: String = UUID.randomUUID().toString(),
    webViewSuffix: String = "vgt-${UUID.randomUUID()}",
): AccountDataPaths {
    val accountRoot = root.resolve("accounts").resolve(storageId)
    return AccountDataPaths(
        root = accountRoot,
        tdlibDirectory = accountRoot.resolve("tdlib"),
        databaseFile = accountRoot.resolve("database").resolve("account.store"),
        filesDirectory = accountRoot.resolve("files"),
        addonDirectory = accountRoot.resolve("addons"),
        webViewDirectory = accountRoot.resolve("webview"),
        keyAlias = "vibetgram.account.$storageId",
        webViewSuffix = webViewSuffix,
    )
}

/** A running account context; the encryption key is intentionally absent. */
class AccountContext internal constructor(
    val handle: AccountHandle,
    val paths: AccountDataPaths,
    val store: EncryptedAccountStore,
)

interface AccountRuntime : AutoCloseable {
    fun start()
    fun recoverProcess()
    override fun close()
}

private object NoopAccountRuntime : AccountRuntime {
    override fun start() = Unit
    override fun recoverProcess() = Unit
    override fun close() = Unit
}

fun interface AccountRuntimeFactory {
    /**
     * Creates an account runtime while the unwrapped database key is available.
     * Implementations must copy only what their native boundary needs; the manager
     * clears [databaseEncryptionKey] immediately after this call completes.
     */
    fun create(context: AccountContext, databaseEncryptionKey: ByteArray): AccountRuntime
}

fun interface AddonCleanupPort {
    fun delete(handle: AccountHandle): Boolean

    fun stop(handle: AccountHandle) = Unit
}

fun interface WebViewCleanupPort {
    fun delete(handle: AccountHandle, suffix: String): Boolean

    fun close(handle: AccountHandle) = Unit
}

private val defaultAddonCleanup = AddonCleanupPort { true }
private val defaultWebViewCleanup = object : WebViewCleanupPort {
    override fun delete(handle: AccountHandle, suffix: String): Boolean = true
}

enum class AccountStatus {
    CREATED,
    AWAITING_FIRST_UNLOCK,
    RUNNING,
    RECOVERY_REQUIRED,
    PENDING_CLEANUP,
    REMOVED,
}

sealed interface AccountStartResult {
    data class Started(val context: AccountContext) : AccountStartResult
    data object AwaitingFirstUnlock : AccountStartResult
    data class Failed(val reason: String) : AccountStartResult
}

sealed interface LogoutResult {
    data object ConfirmationRequired : LogoutResult
    data object Deleted : LogoutResult
    data object PendingCleanup : LogoutResult
    data object NotFound : LogoutResult
}

/** Stable registration view; callers still use only the opaque handle for APIs. */
class AccountRegistration internal constructor(
    private val manager: AccountManager,
    val handle: AccountHandle,
    val paths: AccountDataPaths,
) {
    val store: EncryptedAccountStore
        get() = manager.requireContext(handle).store
}

/**
 * Owns all account contexts and the ordering of account teardown.
 *
 * A manager never derives identity from a caller-supplied number. Every account
 * receives a fresh opaque handle, a random directory, a random key alias, and a
 * random WebView suffix. The real Android Keystore and TDLib runtime are ports.
 */
class AccountManager(
    root: Path,
    private val keyProtector: KeyProtector,
    private val targetSchemaVersion: Int = 1,
    private val migrations: List<StorageMigration> = emptyList(),
    private val runtimeFactory: AccountRuntimeFactory = AccountRuntimeFactory { _, _ -> NoopAccountRuntime },
    private val addonCleanup: AddonCleanupPort = defaultAddonCleanup,
    private val webViewCleanup: WebViewCleanupPort = defaultWebViewCleanup,
) : AutoCloseable {
    private val root = root.toAbsolutePath().normalize()
    private val registryPath = this.root.resolve("accounts.registry")
    private val lock = Any()
    private val records = LinkedHashMap<AccountHandle, Record>()

    init {
        require(!Files.exists(this.root) || !Files.isRegularFile(this.root)) {
            "account root must be a directory"
        }
        Files.createDirectories(this.root)
        loadRegistry()
    }

    fun createAccount(): AccountRegistration = synchronized(lock) {
        val handle = AccountHandle.issue()
        val storageId = UUID.randomUUID().toString()
        val paths = createAccountDataPaths(root, storageId)
        Files.createDirectories(paths.tdlibDirectory)
        Files.createDirectories(paths.databaseFile.parent)
        Files.createDirectories(paths.filesDirectory)
        Files.createDirectories(paths.addonDirectory)
        Files.createDirectories(paths.webViewDirectory)
        val rawKey = ByteArray(32).also(SecureRandom()::nextBytes)
        val wrappedKey = try {
            keyProtector.wrap(paths.keyAlias, rawKey)
        } catch (failure: Exception) {
            deleteTree(paths.root)
            throw failure
        } finally {
            rawKey.fill(0)
        }
        records[handle] = Record(handle, storageId, paths, wrappedKey)
        try {
            persistRegistry()
        } catch (failure: Exception) {
            records.remove(handle)
            runCatching { keyProtector.delete(paths.keyAlias) }
            runCatching { deleteTree(paths.root) }
            throw failure
        }
        AccountRegistration(this, handle, paths)
    }

    fun accounts(): List<AccountHandle> = synchronized(lock) {
        records.values.filter { it.status != AccountStatus.PENDING_CLEANUP }.map { it.handle }
    }

    fun status(handle: AccountHandle): AccountStatus = synchronized(lock) {
        records[handle]?.status ?: AccountStatus.REMOVED
    }

    fun miniAppsEnabled(handle: AccountHandle): Boolean = synchronized(lock) {
        records[handle]?.status == AccountStatus.RUNNING
    }

    fun start(handle: AccountHandle): AccountStartResult = synchronized(lock) {
        val record = records[handle] ?: return AccountStartResult.Failed("account not found")
        if (record.status == AccountStatus.PENDING_CLEANUP) {
            return AccountStartResult.Failed("account cleanup is pending")
        }
        if (record.status == AccountStatus.RUNNING) {
            return AccountStartResult.Started(record.context!!)
        }
        openRecord(record)
    }

    /** Retries every account that was blocked by a device-protected Keystore. */
    fun onFirstUnlock() = synchronized(lock) {
        records.values.filter { it.status == AccountStatus.AWAITING_FIRST_UNLOCK }.toList().forEach {
            openRecord(it)
        }
    }

    /** Rebuilds running runtimes after process/native death while retaining handles and paths. */
    fun recoverProcess() = synchronized(lock) {
        records.values.filter { it.status == AccountStatus.RUNNING }.toList().forEach { record ->
            runCatching { record.runtime?.close() }
            runCatching { record.context?.store?.close() }
            record.runtime = null
            record.context = null
            openRecord(record, recovering = true)
        }
    }

    /** Closes live runtimes without deleting account data or keys. */
    override fun close() = synchronized(lock) {
        records.values.forEach { record ->
            runCatching { record.runtime?.close() }
            runCatching { record.context?.store?.close() }
            record.runtime = null
            record.context = null
            if (record.status == AccountStatus.RUNNING) record.status = AccountStatus.CREATED
        }
    }

    /** Stops execution before deleting account data, keys, addon data, and WebView data. */
    fun logout(handle: AccountHandle, confirmed: Boolean): LogoutResult = synchronized(lock) {
        val record = records[handle] ?: return LogoutResult.NotFound
        if (!confirmed) return LogoutResult.ConfirmationRequired
        if (record.status == AccountStatus.PENDING_CLEANUP) {
            return finishCleanup(record)
        }
        record.status = AccountStatus.PENDING_CLEANUP
        persistRegistry()
        runCatching { record.runtime?.close() }
        record.runtime = null
        runCatching { record.context?.store?.close() }
        record.context = null
        runCatching { addonCleanup.stop(handle) }
        runCatching { webViewCleanup.close(handle) }
        finishCleanup(record)
    }

    internal fun requireContext(handle: AccountHandle): AccountContext = synchronized(lock) {
        records[handle]?.context ?: error("account is not running")
    }

    private fun openRecord(record: Record, recovering: Boolean = false): AccountStartResult {
        val rawKey = try {
            keyProtector.unwrap(record.paths.keyAlias, record.wrappedKey)
        } catch (_: KeystoreLockedException) {
            record.status = AccountStatus.AWAITING_FIRST_UNLOCK
            return AccountStartResult.AwaitingFirstUnlock
        } catch (failure: Exception) {
            record.status = AccountStatus.RECOVERY_REQUIRED
            return AccountStartResult.Failed("account key could not be opened: ${failure.message ?: "unknown error"}")
        }
        var store: EncryptedAccountStore? = null
        var runtime: AccountRuntime? = null
        return try {
            val openedStore = EncryptedAccountStore.open(
                path = record.paths.databaseFile,
                key = rawKey,
                targetSchemaVersion = targetSchemaVersion,
                migrations = migrations,
            )
            store = openedStore
            val context = AccountContext(record.handle, record.paths, openedStore)
            val runtimeInstance = runtimeFactory.create(context, rawKey)
            runtime = runtimeInstance
            if (recovering) runtimeInstance.recoverProcess() else runtimeInstance.start()
            record.context = context
            record.runtime = runtimeInstance
            record.status = AccountStatus.RUNNING
            AccountStartResult.Started(context)
        } catch (failure: Exception) {
            runCatching { runtime?.close() }
            runCatching { store?.close() }
            record.status = AccountStatus.RECOVERY_REQUIRED
            AccountStartResult.Failed("account recovery required: ${failure.message ?: "unknown error"}")
        } finally {
            rawKey.fill(0)
        }
    }

    private fun finishCleanup(record: Record): LogoutResult {
        val webViewDeleted = runCatching {
            webViewCleanup.delete(record.handle, record.paths.webViewSuffix)
        }.getOrDefault(false)
        val addonDeleted = runCatching { addonCleanup.delete(record.handle) }.getOrDefault(false)
        val filesDeleted = runCatching {
            deleteTree(record.paths.root)
            true
        }.getOrDefault(false)
        val keyDeleted = runCatching {
            keyProtector.delete(record.paths.keyAlias)
            true
        }.getOrDefault(false)
        return if (webViewDeleted && addonDeleted && filesDeleted && keyDeleted) {
            records.remove(record.handle)
            persistRegistry()
            LogoutResult.Deleted
        } else {
            // Keep the suffix and record quarantined; it must never be reused.
            record.status = AccountStatus.PENDING_CLEANUP
            persistRegistry()
            LogoutResult.PendingCleanup
        }
    }

    private fun loadRegistry() {
        if (!Files.exists(registryPath)) return
        val encoded = Files.readAllBytes(registryPath)
        try {
            DataInputStream(ByteArrayInputStream(encoded)).use { input ->
                require(input.readInt() == REGISTRY_MAGIC) { "unknown account registry format" }
                require(input.readInt() == REGISTRY_VERSION) { "unsupported account registry version" }
                val count = input.readInt()
                require(count in 0..MAX_REGISTRY_ACCOUNTS) { "invalid account registry count" }
                repeat(count) {
                    val storageId = input.readUTF()
                    val suffix = input.readUTF()
                    val pendingCleanup = input.readBoolean()
                    val wrappedSize = input.readInt()
                    require(wrappedSize in 1..MAX_WRAPPED_KEY_BYTES) { "invalid wrapped key size" }
                    val wrapped = ByteArray(wrappedSize).also(input::readFully)
                    val handle = AccountHandle.issue()
                    val paths = createAccountDataPaths(root, storageId, suffix)
                    records[handle] = Record(
                        handle = handle,
                        storageId = storageId,
                        paths = paths,
                        wrappedKey = WrappedAccountKey(wrapped),
                        status = if (pendingCleanup) AccountStatus.PENDING_CLEANUP else AccountStatus.CREATED,
                    )
                }
                require(input.read() == -1) { "trailing account registry data" }
            }
        } finally {
            encoded.fill(0)
        }
    }

    private fun persistRegistry() {
        val encoded = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(REGISTRY_MAGIC)
                output.writeInt(REGISTRY_VERSION)
                output.writeInt(records.size)
                records.values.sortedBy { it.storageId }.forEach { record ->
                    output.writeUTF(record.storageId)
                    output.writeUTF(record.paths.webViewSuffix)
                    output.writeBoolean(record.status == AccountStatus.PENDING_CLEANUP)
                    val wrapped = record.wrappedKey.copyBytes()
                    output.writeInt(wrapped.size)
                    output.write(wrapped)
                    wrapped.fill(0)
                }
            }
            bytes.toByteArray()
        }
        val temporary = Files.createTempFile(root, "accounts.registry", ".tmp")
        try {
            Files.write(temporary, encoded, StandardOpenOption.TRUNCATE_EXISTING)
            try {
                Files.move(
                    temporary,
                    registryPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, registryPath, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            encoded.fill(0)
            Files.deleteIfExists(temporary)
        }
    }

    private fun deleteTree(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    private class Record(
        val handle: AccountHandle,
        val storageId: String,
        val paths: AccountDataPaths,
        val wrappedKey: WrappedAccountKey,
        var status: AccountStatus = AccountStatus.CREATED,
        var context: AccountContext? = null,
        var runtime: AccountRuntime? = null,
    )

    private companion object {
        const val REGISTRY_MAGIC = 0x56475231
        const val REGISTRY_VERSION = 1
        const val MAX_REGISTRY_ACCOUNTS = 100
        const val MAX_WRAPPED_KEY_BYTES = 4 * 1024
    }
}
