package com.seq.acheronmobile.data.network

import com.seq.acheronmobile.data.model.BulkUpdateRequest
import com.seq.acheronmobile.data.model.BulkUpdateResponse
import com.seq.acheronmobile.data.model.LoginRequest
import com.seq.acheronmobile.data.model.MessageResponse
import com.seq.acheronmobile.data.model.MfaStatusResponse
import com.seq.acheronmobile.data.model.MfaVerifyRequest
import com.seq.acheronmobile.data.model.RefreshTokenRequest
import com.seq.acheronmobile.data.model.StorableCreateRequest
import com.seq.acheronmobile.data.model.StorableDeleteRequest
import com.seq.acheronmobile.data.model.StorableResponse
import com.seq.acheronmobile.data.model.TokenResponse
import com.seq.acheronmobile.data.model.TotpConfirmRequest
import com.seq.acheronmobile.data.model.TotpConfirmResponse
import com.seq.acheronmobile.data.model.TotpDisableRequest
import com.seq.acheronmobile.data.model.TotpSetupResponse
import com.seq.acheronmobile.data.model.VaultUpsertResponse
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST

interface SeqApiService {

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

    @PATCH("acheron/vault")
    suspend fun changeVaultPassword(
        @Body body: JsonObject
    ): Response<VaultUpsertResponse>

    // ── Acheron Storables ──────────────────────────────────────────────

    @POST("acheron/storables")
    suspend fun addStorable(
        @Body body: StorableCreateRequest
    ): Response<StorableResponse>

    @DELETE("acheron/storables")
    suspend fun deleteStorable(
        @Body body: StorableDeleteRequest
    ): Response<StorableResponse>

    @PATCH("acheron/storables")
    suspend fun bulkUpdateStorables(
        @Body body: List<BulkUpdateRequest>
    ): Response<BulkUpdateResponse>
}