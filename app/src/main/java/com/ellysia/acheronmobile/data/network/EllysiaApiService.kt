package com.ellysia.acheronmobile.data.network

import com.ellysia.acheronmobile.data.model.BulkUpdateRequest
import com.ellysia.acheronmobile.data.model.BulkUpdateResponse
import com.ellysia.acheronmobile.data.model.LoginRequest
import com.ellysia.acheronmobile.data.model.MessageResponse
import com.ellysia.acheronmobile.data.model.MfaStatusResponse
import com.ellysia.acheronmobile.data.model.MfaVerifyRequest
import com.ellysia.acheronmobile.data.model.RefreshTokenRequest
import com.ellysia.acheronmobile.data.model.StorableCreateRequest
import com.ellysia.acheronmobile.data.model.StorableDeleteRequest
import com.ellysia.acheronmobile.data.model.StorableResponse
import com.ellysia.acheronmobile.data.model.TokenResponse
import com.ellysia.acheronmobile.data.model.TotpConfirmRequest
import com.ellysia.acheronmobile.data.model.TotpConfirmResponse
import com.ellysia.acheronmobile.data.model.TotpDisableRequest
import com.ellysia.acheronmobile.data.model.TotpSetupResponse
import com.ellysia.acheronmobile.data.model.VaultUpsertResponse
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST

interface EllysiaApiService {

    // ── OAuth ──────────────────────────────────────────────────────────

    @POST("oauth/token")
    suspend fun getToken(
        @Body body: LoginRequest
    ): Response<TokenResponse>

    @POST("oauth/token")
    suspend fun refreshToken(
        @Body body: RefreshTokenRequest
    ): Response<TokenResponse>

    @POST("oauth/mfa/verify")
    suspend fun verifyMfa(
        @Body body: MfaVerifyRequest
    ): Response<TokenResponse>

    // ── MFA: enrolamiento (sección "Cuenta / Ajustes", no forma parte del Vault) ──

    @GET("users/mfa")
    suspend fun getMfaStatus(): Response<MfaStatusResponse>

    @POST("users/mfa/totp/setup")
    suspend fun setupTotp(): Response<TotpSetupResponse>

    @POST("users/mfa/totp/confirm")
    suspend fun confirmTotp(
        @Body body: TotpConfirmRequest
    ): Response<TotpConfirmResponse>

    @DELETE("users/mfa/totp")
    suspend fun disableTotp(
        @Body body: TotpDisableRequest
    ): Response<MessageResponse>

    // ── Acheron Vault ──────────────────────────────────────────────────

    @GET("acheron/vault")
    suspend fun getVault(): Response<JsonObject>

    @POST("acheron/vault")
    suspend fun upsertVault(
        @Body body: JsonObject
    ): Response<VaultUpsertResponse>

    // `ifMatch` lleva la revisión que el cliente cree tener. Retrofit omite la
    // cabecera si el valor es null, así que una escritura sin revisión conocida
    // sigue funcionando (el backend solo la exige en el reemplazo completo).
    @PATCH("acheron/vault")
    suspend fun changeVaultPassword(
        @Body body: JsonObject,
        @Header("If-Match") ifMatch: String? = null
    ): Response<VaultUpsertResponse>

    // ── Acheron Storables ──────────────────────────────────────────────

    @POST("acheron/storables")
    suspend fun addStorable(
        @Body body: StorableCreateRequest,
        @Header("If-Match") ifMatch: String? = null
    ): Response<StorableResponse>

    @DELETE("acheron/storables")
    suspend fun deleteStorable(
        @Body body: StorableDeleteRequest,
        @Header("If-Match") ifMatch: String? = null
    ): Response<StorableResponse>

    @PATCH("acheron/storables")
    suspend fun bulkUpdateStorables(
        @Body body: List<BulkUpdateRequest>,
        @Header("If-Match") ifMatch: String? = null
    ): Response<BulkUpdateResponse>
}