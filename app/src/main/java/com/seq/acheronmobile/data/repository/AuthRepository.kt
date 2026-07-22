package com.seq.acheronmobile.data.repository

import com.seq.acheronmobile.data.model.LoginRequest
import com.seq.acheronmobile.data.model.MfaVerifyRequest
import com.seq.acheronmobile.data.model.TokenResponse
import com.seq.acheronmobile.data.network.NetworkModule
import kotlinx.serialization.json.Json

/**
 * Repositorio de autenticación.
 * Coordina la llamada a la API con el almacenamiento seguro de tokens.
 */
class AuthRepository(
    private val tokenRepository: TokenRepository
) {
    private val api = NetworkModule.seqApiService

    sealed class AuthResult {
        data object Success       : AuthResult()
        data object SessionExpired : AuthResult()   // ← NUEVO
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
    suspend fun login(username: String, password: String): AuthResult {
        return try {
            val response = api.getToken(LoginRequest(username = username, password = password))
            handleTokenResponse(response, invalidCredentialsMessage = "Usuario o contraseña incorrectos")
        } catch (e: java.io.IOException) {
            AuthResult.NetworkError
        } catch (e: Exception) {
            AuthResult.Error("Error inesperado: ${e.localizedMessage}")
        }
    }

    /**
     * Segundo paso del login cuando la cuenta tiene MFA: canjea el
     * challengeToken emitido por login() junto con el código TOTP o un
     * código de recuperación, y persiste los tokens reales si es válido.
     */
    suspend fun verifyMfa(challengeToken: String, code: String? = null, recoveryCode: String? = null): AuthResult {
        return try {
            val response = api.verifyMfa(
                MfaVerifyRequest(challengeToken = challengeToken, code = code, recoveryCode = recoveryCode)
            )
            handleTokenResponse(response, invalidCredentialsMessage = "Código incorrecto o caducado")
        } catch (e: java.io.IOException) {
            AuthResult.NetworkError
        } catch (e: Exception) {
            AuthResult.Error("Error inesperado: ${e.localizedMessage}")
        }
    }

    private fun handleTokenResponse(
        response: retrofit2.Response<TokenResponse>,
        invalidCredentialsMessage: String
    ): AuthResult {
        if (response.isSuccessful) {
            val body = response.body()!!
            if (body.mfaRequired == true) {
                return AuthResult.MfaRequired(body.challengeToken!!)
            }
            tokenRepository.saveTokens(
                accessToken = body.accessToken!!,
                refreshToken = body.refreshToken,
                expiresIn = body.expiresIn!!
            )
            return AuthResult.Success
        }
        // Intentar parsear el error OAuth estándar del cuerpo
        val errorBody = response.errorBody()?.string()
        val description = try {
            Json.decodeFromString<com.seq.acheronmobile.data.model.OAuthErrorResponse>(
                errorBody ?: ""
            ).errorDescription ?: invalidCredentialsMessage
        } catch (_: Exception) {
            when (response.code()) {
                401  -> invalidCredentialsMessage
                429  -> "Demasiados intentos. Espera un momento."
                else -> "Error ${response.code()}"
            }
        }
        return AuthResult.Error(description)
    }
}