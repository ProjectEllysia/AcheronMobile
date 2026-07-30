package com.ellysia.acheronmobile.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class LoginRequest(
    @SerialName("grantType") val grantType: String = "password",
    @SerialName("username")  val username: String,
    @SerialName("password")  val password: String
)

@Serializable
data class TokenResponse(
    // Ausentes cuando la cuenta tiene MFA activo: el servidor devuelve
    // mfaRequired/challengeToken/methods en su lugar (ver más abajo) y
    // estos tres campos vienen nulos hasta que se supera /oauth/mfa/verify.
    @SerialName("access_token")  val accessToken: String? = null,
    @SerialName("token_type")    val tokenType: String? = null,
    @SerialName("expires_in")    val expiresIn: Double? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("mfaRequired")   val mfaRequired: Boolean? = null,
    @SerialName("challengeToken") val challengeToken: String? = null,
    @SerialName("methods")       val methods: List<String>? = null
)

@Serializable
data class OAuthErrorResponse(
    @SerialName("error")             val error: String,
    @SerialName("error_description") val errorDescription: String? = null
)

@Serializable
data class RefreshTokenRequest(
    @SerialName("grantType")      val grantType: String = "refresh_token",
    @SerialName("refresh_token")  val refreshToken: String
)

// ── MFA: login en dos pasos (POST /oauth/mfa/verify) ──────────────────────

@Serializable
data class MfaVerifyRequest(
    @SerialName("challengeToken") val challengeToken: String,
    @SerialName("code")           val code: String? = null,
    @SerialName("recoveryCode")   val recoveryCode: String? = null
)

// ── MFA: enrolamiento (sección "Cuenta / Ajustes", GET/POST/DELETE /users/mfa/*) ──

@Serializable
data class MfaStatusResponse(
    @SerialName("enabled")     val enabled: Boolean,
    @SerialName("confirmedAt") val confirmedAt: String? = null
)

@Serializable
data class TotpSetupResponse(
    @SerialName("secret")          val secret: String,
    @SerialName("provisioningUri") val provisioningUri: String
)

@Serializable
data class TotpConfirmRequest(
    @SerialName("code") val code: String
)

@Serializable
data class TotpConfirmResponse(
    @SerialName("message")       val message: String,
    @SerialName("recoveryCodes") val recoveryCodes: List<String>
)

@Serializable
data class TotpDisableRequest(
    @SerialName("code")         val code: String? = null,
    @SerialName("recoveryCode") val recoveryCode: String? = null
)

@Serializable
data class MessageResponse(
    @SerialName("message") val message: String
)