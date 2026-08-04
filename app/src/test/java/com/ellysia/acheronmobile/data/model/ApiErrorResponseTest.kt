package com.ellysia.acheronmobile.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorResponseTest {

    @Test
    fun `detects password changed by code or by error string`() {
        assertTrue(ApiErrorResponse(code = ApiErrorResponse.PASSWORD_CHANGED_CODE).isPasswordChanged())
        assertTrue(ApiErrorResponse(error = "password_changed").isPasswordChanged())
        assertFalse(ApiErrorResponse(code = 401).isPasswordChanged())
    }

    @Test
    fun `forbidden with missing permissions lists them in the message`() {
        val response = ApiErrorResponse(
            error = "forbidden",
            missingPermissions = MissingPermissions(atLeastOne = listOf("vault:read"))
        )
        assertEquals("No tienes permisos para realizar esta accion (falta: vault:read)", response.displayMessage())
    }

    @Test
    fun `forbidden without a permissions list still gets a generic message`() {
        val response = ApiErrorResponse(error = "forbidden")
        assertEquals("No tienes permisos para realizar esta accion", response.displayMessage())
    }

    @Test
    fun `falls back to error description when there is no permission issue`() {
        val response = ApiErrorResponse(errorDescription = "Credenciales invalidas")
        assertEquals("Credenciales invalidas", response.displayMessage())
    }
}
