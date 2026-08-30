package org.vibetgram.core.storage

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LifecyclePortsTest {
    @Test
    fun `background policy uses encrypted push when available`() {
        val account = org.vibetgram.core.api.AccountHandle.issue()
        val policy = BackgroundExecutionCoordinator(
            push = EncryptedPushRegistrationPort { _, encryptionRequired ->
                assertTrue(encryptionRequired)
                PushRegistrationResult.Registered(PushTransport.GMS_FCM)
            },
            foreground = ForegroundSyncPort { BackgroundPortResult.Unavailable("not needed") },
        )

        assertEquals(
            BackgroundActivation.PushActive(PushTransport.GMS_FCM),
            policy.activate(account, allowForegroundFallback = false),
        )
    }

    @Test
    fun `background policy never reports fake success when all transports fail`() {
        val account = org.vibetgram.core.api.AccountHandle.issue()
        val policy = BackgroundExecutionCoordinator(
            push = EncryptedPushRegistrationPort { _, _ ->
                PushRegistrationResult.Unavailable("FCM configuration missing")
            },
            foreground = ForegroundSyncPort {
                BackgroundPortResult.Unavailable("notification permission denied")
            },
        )

        assertEquals(
            BackgroundActivation.SyncOnOpen(
                pushFailure = "FCM configuration missing",
                foregroundFailure = "notification permission denied",
            ),
            policy.activate(account, allowForegroundFallback = true),
        )
    }

    @Test
    fun `failed webview cleanup keeps mini apps disabled and suffix quarantined`() {
        val webView = RecordingWebViewCleanup(deleteResult = false)
        val manager = AccountManager(
            Files.createTempDirectory("vibetgram-accounts"),
            InMemoryKeyProtector(),
            webViewCleanup = webView,
        )
        val account = manager.createAccount()
        manager.start(account.handle)

        assertEquals(LogoutResult.PendingCleanup, manager.logout(account.handle, confirmed = true))
        assertEquals(AccountStatus.PENDING_CLEANUP, manager.status(account.handle))
        assertFalse(manager.miniAppsEnabled(account.handle))
        assertTrue(webView.quarantined.contains(account.paths.webViewSuffix))
    }

    @Test
    fun `pin and biometric lock are explicit UI gates`() = runTest {
        val lock = AppLockController(
            pinVerifier = PinVerifier { pin -> pin.concatToString() == "1234" },
            biometric = BiometricAuthenticator { true },
        )
        assertEquals(AppLockState.LOCKED, lock.state)
        assertFalse(lock.unlockWithPin(charArrayOf('0', '0', '0', '0')))
        assertTrue(lock.unlockWithPin(charArrayOf('1', '2', '3', '4')))
        lock.lock()
        assertTrue(lock.unlockWithBiometrics())
    }

    @Test
    fun `webview broker fails closed on account switch and suffix mismatch`() {
        val first = org.vibetgram.core.api.AccountHandle.issue()
        val second = org.vibetgram.core.api.AccountHandle.issue()
        val broker = WebViewProfileBroker()

        assertEquals(WebViewBindingResult.Bound, broker.bindBeforeWebView(first, "vgt-first"))
        assertEquals(WebViewBindingResult.RestartRequired, broker.bindBeforeWebView(second, "vgt-second"))
        assertTrue(broker.verifyStartup(first, "vgt-first"))
        assertFalse(broker.verifyStartup(first, "wrong"))
        broker.closeAllForRestart()
        assertEquals(WebViewBindingResult.RestartRequired, broker.bindBeforeWebView(first, "vgt-first"))
        broker.resetAfterProcessStart()
        assertEquals(WebViewBindingResult.Bound, broker.bindBeforeWebView(second, "vgt-second"))
    }

    private class RecordingWebViewCleanup(private val deleteResult: Boolean) : WebViewCleanupPort {
        val quarantined = mutableSetOf<String>()

        override fun close(handle: org.vibetgram.core.api.AccountHandle) = Unit

        override fun delete(handle: org.vibetgram.core.api.AccountHandle, suffix: String): Boolean {
            if (!deleteResult) quarantined += suffix
            return deleteResult
        }
    }
}