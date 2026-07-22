package com.seq.acheronmobile.data.repository

import com.seq.acheronmobile.data.model.MfaStatusResponse
import com.seq.acheronmobile.data.model.TotpConfirmRequest
import com.seq.acheronmobile.data.model.TotpConfirmResponse
import com.seq.acheronmobile.data.model.TotpDisableRequest
import com.seq.acheronmobile.data.model.TotpSetupResponse
import com.seq.acheronmobile.data.network.NetworkModule

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
    private val api = NetworkModule.seqApiService

    sealed class MfaResult<out T> {
        data class Success<T>(val data: T) : MfaResult<T>()
        data class Error(val message: String) : MfaResult<Nothing>()
        data object NetworkError : MfaResult<Nothing>()
    }

    suspend fun getStatus(): MfaResult<MfaStatusResponse> =
        call { api.getMfaStatus() }

    suspend fun setupTotp(): MfaResult<TotpSetupResponse> =
        call { api.setupTotp() }

    suspend fun confirmTotp(code: String): MfaResult<TotpConfirmResponse> =
        call { api.confirmTotp(TotpConfirmRequest(code = code)) }

    suspend fun disableTotp(code: String? = null, recoveryCode: String? = null): MfaResult<String> =
        call { api.disableTotp(TotpDisableRequest(code = code, recoveryCode = recoveryCode)) }
            .let { result ->
                when (result) {
                    is MfaResult.Success -> MfaResult.Success(result.data.message)
                    is MfaResult.Error -> result
                    MfaResult.NetworkError -> MfaResult.NetworkError
                }
            }

    private suspend fun <T> call(block: suspend () -> retrofit2.Response<T>): MfaResult<T> {
        return try {
            val response = block()
            if (response.isSuccessful) {
                MfaResult.Success(response.body()!!)
            } else {
                MfaResult.Error(
                    when (response.code()) {
                        401  -> "Sesión expirada. Inicia sesión de nuevo."
                        409  -> "El MFA ya está activado en esta cuenta."
                        429  -> "Demasiados intentos. Espera un momento."
                        else -> "Código incorrecto o error del servidor (${response.code()})"
                    }
                )
            }
        } catch (e: java.io.IOException) {
            MfaResult.NetworkError
        } catch (e: Exception) {
            MfaResult.Error("Error inesperado: ${e.localizedMessage}")
        }
    }
}
