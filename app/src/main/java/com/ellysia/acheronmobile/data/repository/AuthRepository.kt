package com.ellysia.acheronmobile.data.repository

import com.ellysia.acheronmobile.data.ApiResult
import com.ellysia.acheronmobile.data.model.LoginRequest
import com.ellysia.acheronmobile.data.model.MfaVerifyRequest
import com.ellysia.acheronmobile.data.model.OAuthErrorResponse
import com.ellysia.acheronmobile.data.model.TokenResponse
import com.ellysia.acheronmobile.data.network.NetworkModule
import com.ellysia.acheronmobile.data.network.apiCall
import kotlinx.serialization.json.Json
import retrofit2.Response

/**
 * Repositorio de autenticación.
 * Coordina la llamada a la API con el almacenamiento seguro de tokens.
 */
class AuthRepository(
    private val tokenRepository: TokenRepository
) {
    private val api = NetworkModule.apiService

    sealed class AuthResult {
        data object Success       : AuthResult()
        // NOTA: nunca la emite este repositorio hoy (login()/verifyMfa() no
        // detectan esta condición); los consumidores la manejan de forma
        // defensiva. Wiring pendiente (ver D11 en docs/code-review.md): exige
        // conocer qué código/forma de error usa el backend para un
        // challengeToken de MFA caducado, para no confundirlo con un código
        // simplemente incorrecto.
        data object SessionExpired : AuthResult()
        // Cuenta con MFA activo: el login (paso 1) no emitió tokens, hay que
        // canjear challengeToken + código en POST /oauth/mfa/verify (paso 2).
        data class MfaRequired(val challengeToken: String) : AuthResult()
        data class Error(val message: String) : AuthResult()
        data object NetworkError  : AuthResult()
    }

    /**
     * Realiza el login con credenciales (password grant).
     * Si tiene éxito, persiste los tokens de forma cifrada.
     * Si la cuenta tiene MFA activo, devuelve MfaRequired en vez de tokens.
     */
    suspend fun login(username: String, password: String): AuthResult =
        authenticate(invalidCredentialsMessage = "Usuario o contraseña incorrectos") {
            api.getToken(LoginRequest(username = username, password = password))
        }

    /**
     * Segundo paso del login cuando la cuenta tiene MFA: canjea el
     * challengeToken emitido por login() junto con el código TOTP o un
     * código de recuperación, y persiste los tokens reales si es válido.
     */
    suspend fun verifyMfa(challengeToken: String, code: String? = null, recoveryCode: String? = null): AuthResult =
        authenticate(invalidCredentialsMessage = "Código incorrecto o caducado") {
            api.verifyMfa(MfaVerifyRequest(challengeToken = challengeToken, code = code, recoveryCode = recoveryCode))
        }

    /**
     * Ejecuta la llamada OAuth compartida por [login] y [verifyMfa] vía
     * [apiCall] (ver D2 en docs/code-review.md) y traduce el resultado a
     * [AuthResult].
     */
    private suspend fun authenticate(
        invalidCredentialsMessage: String,
        block: suspend () -> Response<TokenResponse>,
    ): AuthResult {
        return when (val result = apiCall(errorMessage = { oauthErrorMessage(it, invalidCredentialsMessage) }, block = block)) {
            is ApiResult.Success -> onTokenResponse(result.data)
            is ApiResult.Error -> AuthResult.Error(result.message)
            ApiResult.NetworkError -> AuthResult.NetworkError
        }
    }

    /**
     * Interpreta un [TokenResponse] exitoso. Sus campos son todos nullable
     * por diseño (la forma de la respuesta cambia si hay MFA), así que se
     * comprueban con `?:` en vez de `!!` — antes un campo ausente producía un
     * cierre inesperado en vez de un error manejado (ver B6).
     */
    private fun onTokenResponse(body: TokenResponse): AuthResult {
        if (body.mfaRequired == true) {
            val challengeToken = body.challengeToken
                ?: return AuthResult.Error("Respuesta inesperada del servidor (falta el reto de verificación)")
            return AuthResult.MfaRequired(challengeToken)
        }
        val accessToken = body.accessToken
            ?: return AuthResult.Error("Respuesta inesperada del servidor (falta el token de acceso)")
        val expiresIn = body.expiresIn
            ?: return AuthResult.Error("Respuesta inesperada del servidor (falta la caducidad del token)")
        tokenRepository.saveTokens(accessToken = accessToken, refreshToken = body.refreshToken, expiresIn = expiresIn)
        return AuthResult.Success
    }

    private fun oauthErrorMessage(response: Response<TokenResponse>, invalidCredentialsMessage: String): String {
        val errorBody = response.errorBody()?.string()
        return try {
            Json.decodeFromString<OAuthErrorResponse>(errorBody ?: "").errorDescription ?: invalidCredentialsMessage
        } catch (_: Exception) {
            when (response.code()) {
                401  -> invalidCredentialsMessage
                429  -> "Demasiados intentos. Espera un momento."
                else -> "Error ${response.code()}"
            }
        }
    }
}
