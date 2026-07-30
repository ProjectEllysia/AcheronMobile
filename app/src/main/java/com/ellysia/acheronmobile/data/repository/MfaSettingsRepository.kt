package com.ellysia.acheronmobile.data.repository

import com.ellysia.acheronmobile.data.ApiResult
import com.ellysia.acheronmobile.data.model.MfaStatusResponse
import com.ellysia.acheronmobile.data.model.TotpConfirmRequest
import com.ellysia.acheronmobile.data.model.TotpConfirmResponse
import com.ellysia.acheronmobile.data.model.TotpDisableRequest
import com.ellysia.acheronmobile.data.model.TotpSetupResponse
import com.ellysia.acheronmobile.data.network.NetworkModule
import com.ellysia.acheronmobile.data.network.apiCall
import retrofit2.Response

/**
 * Repositorio de enrolamiento MFA (TOTP): activar/desactivar el segundo
 * factor de la cuenta Ellysia. Deliberadamente distinto de AuthRepository
 * (login/sesión) y de VaultRemoteDataSource (Vault): esto es configuración
 * de la cuenta, no del vault ni del propio login.
 *
 * Todas las llamadas requieren sesión activa; el interceptor de
 * NetworkModule añade el Authorization: Bearer automáticamente.
 */
class MfaSettingsRepository {
    private val api = NetworkModule.apiService

    suspend fun getStatus(): ApiResult<MfaStatusResponse> =
        call { api.getMfaStatus() }

    suspend fun setupTotp(): ApiResult<TotpSetupResponse> =
        call { api.setupTotp() }

    suspend fun confirmTotp(code: String): ApiResult<TotpConfirmResponse> =
        call { api.confirmTotp(TotpConfirmRequest(code = code)) }

    suspend fun disableTotp(code: String? = null, recoveryCode: String? = null): ApiResult<String> =
        when (val result = call { api.disableTotp(TotpDisableRequest(code = code, recoveryCode = recoveryCode)) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.message)
            is ApiResult.Error -> result
            ApiResult.NetworkError -> ApiResult.NetworkError
        }

    private suspend fun <T> call(block: suspend () -> Response<T>): ApiResult<T> =
        apiCall(errorMessage = ::mfaErrorMessage, block = block)

    private fun mfaErrorMessage(response: Response<*>): String = when (response.code()) {
        401 -> "Sesión expirada. Inicia sesión de nuevo."
        409 -> "El MFA ya está activado en esta cuenta."
        429 -> "Demasiados intentos. Espera un momento."
        else -> "Código incorrecto o error del servidor (${response.code()})"
    }
}
