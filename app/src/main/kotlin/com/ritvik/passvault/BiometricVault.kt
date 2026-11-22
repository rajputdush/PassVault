package com.ritvik.passvault

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Optional fingerprint/face unlock. The vault key is encrypted with an AES key that lives in the
 * Android Keystore (hardware-backed where available) and can only be used right after a successful
 * BIOMETRIC_STRONG authentication. If the user enrolls a new fingerprint, Android invalidates the
 * key, and the user falls back to the master password.
 */
class BiometricVault(context: Context) {
    private val file = File(context.filesDir, "biometric_key.json")
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    fun isEnabled(): Boolean = file.exists()

    /** Creates a fresh Keystore key and returns a cipher ready for BiometricPrompt. */
    fun newEncryptCipher(): Cipher {
        deleteKey()
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Timeout 0 = authentication is needed for every single use of the key.
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
            }
            .build()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(spec)
        val key = generator.generateKey()
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    /** Call with the cipher returned by a successful BiometricPrompt. */
    fun store(cipher: Cipher, vaultKey: ByteArray) {
        val encrypted = cipher.doFinal(vaultKey)
        val json = JSONObject().apply {
            put("iv", encoder.encodeToString(cipher.iv))
            put("ciphertext", encoder.encodeToString(encrypted))
        }
        writeAtomically(file, json.toString().toByteArray(Charsets.UTF_8))
    }

    /** @throws android.security.keystore.KeyPermanentlyInvalidatedException if fingerprints changed. */
    fun newDecryptCipher(): Cipher {
        val json = JSONObject(file.readText(Charsets.UTF_8))
        val iv = decoder.decode(json.getString("iv"))
        val key = loadKey() ?: error("Keystore key is missing")
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
    }

    /** Returns the vault key. The caller owns the array. */
    fun read(cipher: Cipher): ByteArray {
        val json = JSONObject(file.readText(Charsets.UTF_8))
        return cipher.doFinal(decoder.decode(json.getString("ciphertext")))
    }

    fun disable() {
        file.delete()
        deleteKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun loadKey(): SecretKey? = keyStore().getKey(ALIAS, null) as? SecretKey

    private fun deleteKey() {
        val ks = keyStore()
        if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "passvault_biometric_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
