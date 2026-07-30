package com.ellysia.acheronmobile.data.vault

import com.ellysia.acheronmobile.ui.vault.StorableTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cubre exactamente el punto donde D1 (docs/code-review.md) falla en
 * silencio: el conjunto de campos de cada [com.ellysia.acheronmobile.ui.vault.StorableTypeSpec]
 * está enumerado a mano en varios sitios de [VaultCryptoService]
 * (`createStorable`, `applyField`, `detailsOf`). Si a un tipo se le añade un
 * campo en `StorableTypes` y se olvida la rama correspondiente en alguno de
 * esos métodos, hoy no hay error de compilación ni excepción en runtime: el
 * campo simplemente no aparece, o el cambio del usuario se descarta. Este
 * test recorre los siete tipos y falla explícitamente si eso ocurre.
 */
class VaultCryptoServiceTest {

    private fun sampleFields(kind: String): Map<String, String> =
        StorableTypes.of(kind).fields.associate { it.key to "valor-${it.key}" }

    private fun detailsOf(service: VaultCryptoService, id: String): Map<String, String> {
        val state = service.state.value
        check(state is VaultState.Unlocked) { "El vault de prueba no está desbloqueado" }
        return state.storables.first { it.id == id }.details
    }

    @Test
    fun `cada tipo registrado puede crearse con todos sus campos`() {
        StorableTypes.all.forEach { spec ->
            val service = VaultCryptoService()
            service.createVault("user-test-${spec.kind}", "Contraseña-Test-1234")

            val created = service.addStorable(spec.kind, "Título de prueba", sampleFields(spec.kind))
            val id = requireNotNull(created.internalId) { "addStorable no devolvió internalId para '${spec.kind}'" }

            val details = detailsOf(service, id)
            spec.fields.forEach { f ->
                assertTrue(
                    "Falta el campo '${f.key}' en los detalles de un '${spec.kind}' recién creado " +
                        "(revisar VaultCryptoService.createStorable/detailsOf para este tipo, ver D1)",
                    details.containsKey(f.key)
                )
            }
        }
    }

    @Test
    fun `cada tipo registrado puede actualizar todos sus campos sin descartar ninguno`() {
        StorableTypes.all.forEach { spec ->
            if (spec.fields.isEmpty()) return@forEach

            val service = VaultCryptoService()
            service.createVault("user-test-${spec.kind}", "Contraseña-Test-1234")
            val created = service.addStorable(spec.kind, "Título de prueba", sampleFields(spec.kind))
            val id = requireNotNull(created.internalId)

            val changes = spec.fields.associate { it.key to "nuevo-${it.key}" }
            val encryptedChanges = service.updateStorable(id, title = null, fields = changes)

            assertEquals(
                "updateStorable('${spec.kind}') no devolvió cambios para todos los campos enviados: " +
                    "alguno se descartó en silencio en VaultCryptoService.applyField (ver D1 en docs/code-review.md)",
                spec.fields.map { it.key }.toSet(),
                encryptedChanges?.keys
            )
        }
    }
}
