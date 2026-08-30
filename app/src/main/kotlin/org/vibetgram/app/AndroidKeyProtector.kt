package org.vibetgram.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.vibetgram.core.storage.KeystoreLockedException
import org.vibetgram.core.storage.KeyProtector
import org.vibetgram.core.storage.WrappedAccountKey

/** Wraps each account data key with a non-exportable Android Keystore AES key. */
class AndroidKeyProtector : KeyProtector {
    private val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    override fun wrap(alias: String, key: ByteArray): WrappedAccountKey {
        val wrappingKey = keyStore.getKey(alias, null) as? SecretKey ?: createKey(alias)
        return crypt(Cipher.ENCRYPT_MODE, alias, wrappingKey, key)
    }

    override fun unwrap(alias: String, wrappedKey: WrappedAccountKey): ByteArray {
        val wrappingKey = keyStore.getKey(alias, null) as? SecretKey
            ?: error("Android Keystore account key is unavailable")
        return try {
            decrypt(alias, wrappingKey, wrappedKey.copyBytes())
        } catch (failure: android.security.keystore.UserNotAuthenticatedException) {
            throw KeystoreLockedException()
        }
    }

    override fun delete(alias: String) {
        keyStore.deleteEntry(alias)
    }

    private fun createKey(alias: String): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUnlockedDeviceRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun crypt(mode: Int, alias: String, key: SecretKey, input: ByteArray): WrappedAccountKey {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(mode, key)
        cipher.updateAAD(alias.encodeToByteArray())
        return WrappedAccountKey(cipher.iv + cipher.doFinal(input))
    }

    private fun decrypt(alias: String, key: SecretKey, encoded: ByteArray): ByteArray {
        require(encoded.size > NONCE_BYTES) { "wrapped account key is truncated" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(128, encoded.copyOfRange(0, NONCE_BYTES)),
        )
        cipher.updateAAD(alias.encodeToByteArray())
        return cipher.doFinal(encoded.copyOfRange(NONCE_BYTES, encoded.size))
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val NONCE_BYTES = 12
    }
}
