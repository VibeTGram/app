package org.vibetgram.core.tdlib

import java.util.concurrent.ConcurrentHashMap
import org.vibetgram.core.api.AccountHandle
import org.vibetgram.core.storage.AccountContext
import org.vibetgram.core.storage.AccountRuntime
import org.vibetgram.core.storage.AccountRuntimeFactory

data class TdLibCredentials(
    val apiId: Int,
    val apiHash: String,
    val deviceModel: String,
    val systemVersion: String,
    val applicationVersion: String,
) {
    init {
        require(apiId > 0)
        require(apiHash.isNotBlank())
        require(deviceModel.isNotBlank())
        require(systemVersion.isNotBlank())
        require(applicationVersion.isNotBlank())
    }
}

/** Binds AccountManager's opaque encrypted context to exactly one TDLib engine. */
class TdLibAccountRuntimeFactory(
    private val clientManager: ClientManager,
    private val credentials: TdLibCredentials,
) : AccountRuntimeFactory {
    private val engines = ConcurrentHashMap<AccountHandle, TdLibEngine>()

    override fun create(
        context: AccountContext,
        databaseEncryptionKey: ByteArray,
    ): AccountRuntime {
        val engine = TdLibEngine(
            account = context.handle,
            clientManager = clientManager,
            config = TdLibConfig(
                databaseDirectory = context.paths.tdlibDirectory.toString(),
                filesDirectory = context.paths.filesDirectory.toString(),
                apiId = credentials.apiId,
                apiHash = credentials.apiHash,
                deviceModel = credentials.deviceModel,
                systemVersion = credentials.systemVersion,
                applicationVersion = credentials.applicationVersion,
                encryptionKey = databaseEncryptionKey,
            ),
        )
        check(engines.putIfAbsent(context.handle, engine) == null) {
            "account already has a TDLib runtime"
        }
        return Runtime(context.handle, engine)
    }

    fun engine(handle: AccountHandle): TelegramEngine? = engines[handle]

    private inner class Runtime(
        private val handle: AccountHandle,
        private val engine: TdLibEngine,
    ) : AccountRuntime {
        override fun start() = engine.start()

        override fun recoverProcess() = engine.start()

        override fun close() {
            if (engines.remove(handle, engine)) engine.close()
        }
    }
}
