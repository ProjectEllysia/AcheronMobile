package com.ellysia.acheronmobile.data

/**
 * Resultado unificado de una llamada de red, compartido por
 * `VaultRemoteDataSource` y `MfaSettingsRepository` (ver D3 en
 * docs/code-review.md). `AuthRepository` mantiene su propio `AuthResult`
 * porque el login tiene estados genuinamente distintos (MFA requerido,
 * sesión expirada) que no son un simple éxito/error de API, pero internamente
 * también se apoya en [com.ellysia.acheronmobile.data.network.apiCall].
 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val code: Int, val message: String) : ApiResult<Nothing>()
    data object NetworkError : ApiResult<Nothing>()
}
