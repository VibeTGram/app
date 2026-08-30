package org.vibetgram.app

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import org.vibetgram.core.storage.AccountManager
import org.vibetgram.core.storage.AccountStartResult
import org.vibetgram.core.tdlib.OfficialJsonClientTransport
import org.vibetgram.core.tdlib.TdJsonClientManager
import org.vibetgram.core.tdlib.TdLibAccountRuntimeFactory
import org.vibetgram.core.tdlib.TdLibCredentials

sealed interface NativeLibraryState {
    data object Loaded : NativeLibraryState
    data class Unavailable(val reason: String) : NativeLibraryState
}

object NativeTdlibLoader {
    fun load(): NativeLibraryState = try {
        System.loadLibrary("tdjsonjava")
        NativeLibraryState.Loaded
    } catch (failure: LinkageError) {
        NativeLibraryState.Unavailable(failure.message ?: "libtdjsonjava could not be loaded")
    }
}

/**
 * Production Android composition for the pinned JSON-Java TDLib adapter.
 * Missing native code or operator-supplied Telegram application credentials are
 * visible typed blockers; no fake semantic service is ever returned.
 */
class AndroidCoreBootstrapProvider(
    private val context: Context,
) : GuiDependenciesProvider, AutoCloseable {
    private var accountManager: AccountManager? = null
    private var clientManager: TdJsonClientManager? = null
    private var accountRuntimeFactory: TdLibAccountRuntimeFactory? = null

    override suspend fun load(scope: CoroutineScope): GuiDependenciesLoadResult {
        when (val native = NativeTdlibLoader.load()) {
            NativeLibraryState.Loaded -> Unit
            is NativeLibraryState.Unavailable -> return GuiDependenciesLoadResult.Unavailable(
                code = "TDLIB_NATIVE_LIBRARY_UNAVAILABLE",
                message = native.reason,
            )
        }
        if (BuildConfig.TELEGRAM_API_ID <= 0 || BuildConfig.TELEGRAM_API_HASH.isBlank()) {
            return GuiDependenciesLoadResult.Unavailable(
                code = "TELEGRAM_API_CREDENTIALS_UNAVAILABLE",
                message = "Supply telegramApiId and telegramApiHash through Gradle properties or the documented environment variables.",
            )
        }

        val clients = clientManager ?: TdJsonClientManager(OfficialJsonClientTransport())
            .also { clientManager = it }
        val runtimeFactory = accountRuntimeFactory ?: TdLibAccountRuntimeFactory(
            clientManager = clients,
            credentials = TdLibCredentials(
                apiId = BuildConfig.TELEGRAM_API_ID,
                apiHash = BuildConfig.TELEGRAM_API_HASH,
                deviceModel = listOf(Build.MANUFACTURER, Build.MODEL)
                    .filter(String::isNotBlank)
                    .joinToString(" ")
                    .ifBlank { "Android device" },
                systemVersion = Build.VERSION.RELEASE.ifBlank { "Android" },
                applicationVersion = BuildConfig.VERSION_NAME,
            ),
        ).also { accountRuntimeFactory = it }
        val manager = accountManager ?: AccountManager(
            root = context.noBackupFilesDir.resolve("accounts").toPath(),
            keyProtector = AndroidKeyProtector(),
            runtimeFactory = runtimeFactory,
        ).also { accountManager = it }
        val handle = manager.accounts().firstOrNull() ?: manager.createAccount().handle
        return when (val started = manager.start(handle)) {
            is AccountStartResult.Started -> {
                val engine = runtimeFactory.engine(handle)
                    ?: return GuiDependenciesLoadResult.Unavailable(
                        code = "TDLIB_ACCOUNT_RUNTIME_UNAVAILABLE",
                        message = "The account-scoped TDLib runtime did not start.",
                    )
                GuiDependenciesLoadResult.Ready(
                    CoreGuiDependenciesAdapter(handle, engine, scope).dependencies,
                )
            }
            AccountStartResult.AwaitingFirstUnlock -> GuiDependenciesLoadResult.Unavailable(
                code = "ACCOUNT_KEY_LOCKED",
                message = "Account data is unavailable until the device is unlocked.",
            )
            is AccountStartResult.Failed -> GuiDependenciesLoadResult.Unavailable(
                code = "ACCOUNT_RECOVERY_REQUIRED",
                message = started.reason,
            )
        }
    }

    override fun close() {
        accountManager?.close()
        accountManager = null
        accountRuntimeFactory = null
        clientManager?.close()
        clientManager = null
    }
}