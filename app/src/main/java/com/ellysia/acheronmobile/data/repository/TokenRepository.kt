package com.ellysia.acheronmobile.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit
import com.ellysia.acheronmobile.data.model.ApiErrorResponse
import com.ellysia.acheronmobile.data.model.RefreshTokenRequest
import com.ellysia.acheronmobile.data.network.EllysiaApiService
import com.ellysia.acheronmobile.di.SessionEvents
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * Almacena y recupera los tokens OAuth de forma segura usando
 * EncryptedSharedPreferences respaldado por el Android Keystore.
 *
 * La MasterKey usa AES-256-GCM. Las claves del mapa se cifran
 * con AES-256-SIV y los valores con AES-256-GCM.
 *
 * NUNCA almacenar tokens en SharedPreferences plano, ficheros o Room
 * sin cifrado adicional.
 */
class TokenRepository(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "acheron_secure_prefs",          // nombre del fichero cifrado
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_ACCESS_TOKEN  = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT    = "expires_at"
        private const val KEY_USERNAME      = "username"

        private val errorJson = Json { ignoreUnknownKeys = true; isLenient = true }
    }

    fun saveUsername(username: String) {
        prefs.edit { putString(KEY_USERNAME, username) }
    }

    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)

    fun saveTokens(accessToken: String, refreshToken: String?, expiresIn: Double) {
        val expiresAt = System.currentTimeMillis() + (expiresIn.toLong() * 1000L)
        prefs.edit {
            putString(KEY_ACCESS_TOKEN, accessToken)
                .putLong(KEY_EXPIRES_AT, expiresAt)
                .apply {
                    if (refreshToken != null) putString(KEY_REFRESH_TOKEN, refreshToken)
                }
        }
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun isAccessTokenExpired(): Boolean {
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        // Margen de 60 s para evitar edge cases de expiración en vuelo
        return System.currentTimeMillis() >= (expiresAt - 60_000L)
    }

    /**
     * True si hay una sesión utilizable: el access token sigue vigente, o si
     * no hay un refresh token con el que `TokenAuthenticator` pueda renovarlo
     * de forma transparente en la primera llamada. Mirar solo el access
     * token forzaba un login completo (usuario + contraseña + MFA) cada vez
     * que expiraba, aunque el refresh siguiera siendo válido (ver B3 en
     * docs/code-review.md).
     */
    fun hasValidSession(): Boolean {
        if (getAccessToken() == null) return false
        return !isAccessTokenExpired() || getRefreshToken() != null
    }

    fun clearTokens() {
        prefs.edit {
            remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_EXPIRES_AT)
                .remove(KEY_USERNAME)
        }
    }

    fun refreshAccessTokenSync(apiService: EllysiaApiService): String? {
        val currentRefresh = getRefreshToken() ?: return null

        return try {
            val response = runBlocking {
                apiService.refreshToken(RefreshTokenRequest(refreshToken = currentRefresh))
            }
            if (response.isSuccessful) {
                val body = response.body()!!
                // accessToken/expiresIn son nullable en TokenResponse por el flujo de MFA
                // (ver AuthRepository.onTokenResponse); un refresh nunca deberia devolverlos
                // vacios, pero si ocurre tratamos el refresh como fallido en vez de crashear.
                val accessToken = body.accessToken
                val expiresIn = body.expiresIn
                if (accessToken == null || expiresIn == null) {
                    clearTokens()
                    return null
                }
                saveTokens(
                    accessToken = accessToken,
                    refreshToken = body.refreshToken ?: currentRefresh, // conservar el refresh si la API no devuelve uno nuevo
                    expiresIn = expiresIn
                )
                accessToken
            } else {
                // Si el refresh falló porque la contraseña de acceso cambió,
                // señalar el motivo para que la UI muestre la pantalla dedicada.
                if (refreshFailedByPasswordChange(response.errorBody()?.string())) {
                    SessionEvents.signalEnd("password_changed")
                }
                clearTokens()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun refreshFailedByPasswordChange(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        return try {
            errorJson.decodeFromString<ApiErrorResponse>(body).isPasswordChanged()
        } catch (_: Exception) {
            false
        }
    }
}