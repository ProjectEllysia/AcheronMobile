package com.seq.acheronmobile.ui.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/**
 * Envoltorio fino sobre [BiometricPrompt] para el desbloqueo del vault con huella.
 *
 * Mantiene la criptografía fuera de la UI: el [Cipher] lo crea
 * [com.seq.acheronmobile.data.security.BiometricMasterPasswordStore] y aquí solo
 * se autentica al usuario y se devuelve el `Cipher` ya autenticado.
 */
object BiometricGate {

    /** True si el dispositivo tiene biometría fuerte (clase 3) lista para usar. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Lanza el diálogo biométrico para autenticar el [cipher] indicado.
     *
     * @param onSuccess recibe el `Cipher` autenticado (listo para cifrar/descifrar).
     * @param onError   recibe el código y mensaje de error de [BiometricPrompt]
     *                  (incluye la cancelación del usuario).
     */
    fun authenticate(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String,
        subtitle: String,
        negativeText: String,
        onSuccess: (Cipher) -> Unit,
        onError: (Int, CharSequence) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authenticated = result.cryptoObject?.cipher
                    if (authenticated != null) {
                        onSuccess(authenticated)
                    } else {
                        onError(BiometricPrompt.ERROR_UNABLE_TO_PROCESS, "Cipher no disponible")
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errorCode, errString)
                }

                // onAuthenticationFailed: intento transitorio (huella no reconocida);
                // BiometricPrompt deja reintentar, así que no lo propagamos.
            },
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
