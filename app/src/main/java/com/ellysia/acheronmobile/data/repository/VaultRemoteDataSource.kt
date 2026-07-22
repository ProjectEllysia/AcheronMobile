package com.ellysia.acheronmobile.data.repository

import com.ellysia.acheronmobile.data.ApiResult
import com.ellysia.acheronmobile.data.model.ApiErrorResponse
import com.ellysia.acheronmobile.data.model.BulkUpdateRequest
import com.ellysia.acheronmobile.data.model.BulkUpdateResponse
import com.ellysia.acheronmobile.data.model.StorableCreateRequest
import com.ellysia.acheronmobile.data.model.StorableDeleteRequest
import com.ellysia.acheronmobile.data.model.StorableResponse
import com.ellysia.acheronmobile.data.model.VaultUpsertResponse
import com.ellysia.acheronmobile.data.network.NetworkModule
import com.ellysia.acheronmobile.data.network.apiCall
import com.ellysia.acheronmobile.di.SessionEvents
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import retrofit2.Response

class VaultRemoteDataSource(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val api = NetworkModule.apiService

    suspend fun fetchVault(): ApiResult<JsonObject> = call { api.getVault() }

    suspend fun pushVault(vault: JsonObject): ApiResult<VaultUpsertResponse> = call { api.upsertVault(vault) }

    suspend fun changeVaultPassword(metadata: JsonObject): ApiResult<VaultUpsertResponse> =
        call { api.changeVaultPassword(metadata) }

    suspend fun addStorable(request: StorableCreateRequest): ApiResult<StorableResponse> =
        call { api.addStorable(request) }

    suspend fun deleteStorable(internalId: String): ApiResult<StorableResponse> =
        call { api.deleteStorable(StorableDeleteRequest(internalId)) }

    suspend fun bulkUpdate(requests: List<BulkUpdateRequest>): ApiResult<BulkUpdateResponse> =
        call { api.bulkUpdateStorables(requests) }

    private suspend fun <T> call(block: suspend () -> Response<T>): ApiResult<T> =
        apiCall(errorMessage = ::errorMessage, block = block)

    private fun errorMessage(response: Response<*>): String {
        val fallback = when (response.code()) {
            401 -> "Sesion expirada"
            403 -> "No tienes permisos para realizar esta accion"
            404 -> "No encontrado"
            409 -> "Ya existe"
            429 -> "Demasiadas peticiones"
            else -> "Error ${response.code()}"
        }
        val parsed = try {
            val body = response.errorBody()?.string() ?: ""
            json.decodeFromString<ApiErrorResponse>(body)
        } catch (_: Exception) {
            null
        }
        // Un 401 aquí llega DESPUÉS de que TokenAuthenticator ya intentó
        // renovar el access token y no pudo: la sesión ya no es utilizable,
        // así que siempre hay que señalar el fin de sesión global para que
        // la UI lleve a login (con el mensaje dedicado si fue un cambio de
        // contraseña). Antes solo se señalaba en ese caso concreto y un
        // refresh simplemente caducado dejaba al usuario atrapado en la
        // pantalla actual (ver B2 en docs/code-review.md).
        if (response.code() == 401) {
            val reason = if (parsed?.isPasswordChanged() == true) "password_changed" else "expired"
            SessionEvents.signalEnd(reason)
        }
        return parsed?.displayMessage() ?: fallback
    }
}
