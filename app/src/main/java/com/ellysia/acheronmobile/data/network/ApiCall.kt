package com.ellysia.acheronmobile.data.network

import com.ellysia.acheronmobile.data.ApiResult
import retrofit2.Response
import java.io.IOException

/**
 * Ejecuta una llamada Retrofit y la traduce a [ApiResult], centralizando el
 * manejo de `IOException` (sin conexión) y de excepciones inesperadas — antes
 * repetido, con pequeñas variaciones, en cada método de `VaultRemoteDataSource`
 * y `MfaSettingsRepository` (ver D2 en docs/code-review.md).
 *
 * El mensaje de error para una respuesta no exitosa lo decide el llamante vía
 * [errorMessage], porque cada repositorio mapea los códigos HTTP a texto de
 * forma distinta (y, en el caso del vault, además señala `SessionEvents`).
 *
 * Una respuesta exitosa con cuerpo nulo (p. ej. un 200 vacío inesperado) se
 * trata como error en vez de propagar un cuerpo nulo aguas abajo —
 * antes esto se resolvía con `response.body()!!`, que lanzaba
 * `NullPointerException` en vez de un error manejado (ver B6).
 */
suspend fun <T> apiCall(
    errorMessage: (Response<T>) -> String,
    block: suspend () -> Response<T>,
): ApiResult<T> {
    return try {
        val response = block()
        val body = response.body()
        when {
            response.isSuccessful && body != null -> ApiResult.Success(body)
            response.isSuccessful -> ApiResult.Error(response.code(), "Respuesta vacía del servidor")
            else -> ApiResult.Error(response.code(), errorMessage(response))
        }
    } catch (_: IOException) {
        ApiResult.NetworkError
    } catch (e: Exception) {
        ApiResult.Error(0, e.localizedMessage ?: "Error inesperado")
    }
}
