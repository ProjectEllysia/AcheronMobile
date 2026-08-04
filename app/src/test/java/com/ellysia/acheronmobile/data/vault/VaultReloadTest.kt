package com.ellysia.acheronmobile.data.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cubre la recarga en caliente: traerse el vault del servidor y volver a
 * abrirlo **sin pedir otra vez la contraseña maestra**.
 *
 * Es la pieza que sustituye al viejo botón "Sincronizar" (que empujaba el
 * snapshot local y borraba lo escrito desde otro cliente) y la que se ejecuta
 * tras un 409 por revisión obsoleta antes de reintentar. Si [VaultCryptoService]
 * dejara de conservar la contraseña de la sesión, la recarga devolvería
 * `Locked` y la app pediría desbloquear en mitad de cada operación.
 */
class VaultReloadTest {

    private val password = "Contraseña-Test-1234"
    private val userId = "user-reload"

    private fun storableIds(state: VaultState): Set<String> {
        check(state is VaultState.Unlocked) { "Se esperaba un vault desbloqueado, fue $state" }
        return state.storables.map { it.id }.toSet()
    }

    @Test
    fun `recargar trae lo que escribio otro cliente sin la contraseña maestra`() {
        // "Otro dispositivo" (la web): crea la bóveda y le añade un elemento.
        val other = VaultCryptoService()
        val initialJson = other.createVault(userId, password)
        other.addStorable("account", "Cuenta web", mapOf(
            "username" to "web@example.com", "domain" to "example.com", "password" to "s3cr3t",
        ))
        val remoteJson = other.exportEncryptedJson()

        // Este dispositivo se desbloqueó ANTES de ese cambio: no lo ve.
        val mobile = VaultCryptoService()
        val unlocked = mobile.unlockFromJson(userId, initialJson, password)
        assertTrue("El desbloqueo de partida debería funcionar", unlocked is VaultState.Unlocked)
        assertEquals(emptySet<String>(), storableIds(unlocked))

        // Recarga: sin contraseña, y el elemento ajeno aparece descifrado.
        val reloaded = mobile.reloadFromJson(userId, remoteJson)
        assertEquals(
            "La recarga debería traer el elemento escrito desde el otro cliente",
            1, storableIds(reloaded).size,
        )
        val item = (reloaded as VaultState.Unlocked).storables.first()
        assertEquals("Cuenta web", item.title)
        assertEquals("web@example.com", item.details["username"])
    }

    @Test
    fun `recargar tras crear la boveda tampoco pide la contraseña`() {
        val service = VaultCryptoService()
        val json = service.createVault(userId, password)

        val reloaded = service.reloadFromJson(userId, json)
        assertTrue(
            "Crear la bóveda deja la sesión abierta: la recarga no debería bloquear",
            reloaded is VaultState.Unlocked,
        )
    }

    @Test
    fun `recargar con la boveda bloqueada no la abre`() {
        val source = VaultCryptoService()
        val json = source.createVault(userId, password)

        val locked = VaultCryptoService()
        assertEquals(
            "Sin sesión previa no hay contraseña que reutilizar",
            VaultState.Locked, locked.reloadFromJson(userId, json),
        )
    }

    @Test
    fun `bloquear descarta la contraseña de la sesion`() {
        val service = VaultCryptoService()
        val json = service.createVault(userId, password)
        service.lock()

        assertEquals(
            "lock() debe olvidar la contraseña: si no, seguiría siendo recargable estando bloqueada",
            VaultState.Locked, service.reloadFromJson(userId, json),
        )
    }
}
