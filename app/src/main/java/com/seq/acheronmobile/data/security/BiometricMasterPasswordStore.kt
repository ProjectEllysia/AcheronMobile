package com.seq.acheronmobile.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Custodia la **contraseña maestra** del vault detrás de la biometría del
 * dispositivo.
 *
 * Diseño (alineado con el plan de huella de Acheron):
 *  - Una clave AES-256-GCM vive en el **Android Keystore** con
 *    `setUserAuthenticationRequired(true)` e `setInvalidatedByBiometricEnrollment(true)`,
 *    así que solo se puede usar tras una autenticación biométrica reciente y se
 *    invalida si el usuario registra una nueva huella.
 *  - La contraseña maestra se cifra con esa clave (vía un [Cipher] autenticado por
 *    [androidx.biometric.BiometricPrompt]) y el `IV + ciphertext` se persiste en
 *    [EncryptedSharedPreferences] (defensa en profundidad).
 *
 * Se guarda la **contraseña maestra** (no la vaultKey) a propósito: conserva la
 * validación zero-knowledge por `checker` y hace que un cambio remoto de la
 * contraseña invalide el desbloqueo por huella (el `checker` fallará).
 *
 * Esta clase NO muestra UI: solo crea los [Cipher] y persiste el secreto. El
 * `BiometricPrompt` se ejecuta en la capa de UI (ver `BiometricGate`).
 */
class BiometricMasterPasswordStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "acheron_biometric_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    // ── Estado de enrolado ──────────────────────────────────────────────────

    /** True si el usuario activó el desbloqueo por huella y hay secreto guardado. */
    fun isEnabled(): Boolean =
        prefs.getBoolean(KEY_ENABLED, false) &&
            prefs.contains(KEY_CIPHERTEXT) &&
            prefs.contains(KEY_IV)

    /** Versión de metadatos del vault con la que se enroló (para detectar cambios). */
    fun knownMetadataVersion(): Int = prefs.getInt(KEY_METADATA_VERSION, -1)

    fun setKnownMetadataVersion(version: Int) {
        prefs.edit { putInt(KEY_METADATA_VERSION, version) }
    }

    /** Borra el secreto biométrico y la clave del Keystore. Idempotente. */
    fun clear() {
        prefs.edit {
            remove(KEY_CIPHERTEXT)
                .remove(KEY_IV)
                .remove(KEY_ENABLED)
                .remove(KEY_METADATA_VERSION)
        }
        runCatching {
            keyStore().deleteEntry(KEY_ALIAS)
        }
    }

    // ── Ciphers para BiometricPrompt.CryptoObject ───────────────────────────

    /**
     * [Cipher] en modo cifrado, listo para envolver en un `CryptoObject` y pasar
     * a `BiometricPrompt`. Tras la autenticación, usar [finishEnroll].
     */
    fun encryptCipher(): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return cipher
    }

    /**
     * [Cipher] en modo descifrado, inicializado con el IV guardado. Devuelve
     * `null` si no hay secreto o si la clave fue invalidada (p.ej. el usuario
     * registró una nueva huella) — en ese caso el secreto se borra y hay que
     * re-enrolar.
     */
    fun decryptCipher(): Cipher? {
        val ivB64 = prefs.getString(KEY_IV, null) ?: return null
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            cipher
        } catch (_: KeyPermanentlyInvalidatedException) {
            // Nueva huella registrada / biometría reseteada: el secreto ya no sirve.
            clear()
            null
        }
    }

    // ── Persistencia del secreto (tras autenticación biométrica) ────────────

    /** Cifra y persiste la contraseña maestra usando un [cipher] ya autenticado. */
    fun finishEnroll(cipher: Cipher, masterPassword: String) {
        val ciphertext = cipher.doFinal(masterPassword.toByteArray(Charsets.UTF_8))
        prefs.edit {
            putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putBoolean(KEY_ENABLED, true)
        }
    }

    /** Descifra y devuelve la contraseña maestra usando un [cipher] ya autenticado. */
    fun finishUnlock(cipher: Cipher): String {
        val ctB64 = prefs.getString(KEY_CIPHERTEXT, null)
            ?: throw IllegalStateException("No hay contraseña maestra biométrica guardada")
        val ciphertext = Base64.decode(ctB64, Base64.NO_WRAP)
        val plain = cipher.doFinal(ciphertext)
        return String(plain, Charsets.UTF_8)
    }

    // ── Keystore ────────────────────────────────────────────────────────────

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        val ks = keyStore()
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            // Invalida la clave si cambian las huellas registradas en el dispositivo.
            .setInvalidatedByBiometricEnrollment(true)
            .build()

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "acheron_biometric_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128

        private const val KEY_CIPHERTEXT = "bio_master_ct"
        private const val KEY_IV = "bio_master_iv"
        private const val KEY_ENABLED = "bio_enabled"
        private const val KEY_METADATA_VERSION = "vault_metadata_version"
    }
}
