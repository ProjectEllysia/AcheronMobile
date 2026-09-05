package com.ellysia.acheronmobile.data.vault

import com.ellysia.acheronmobile.ui.vault.StorableTypes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El catálogo de esta app tiene que seguir a `AcheronSchema`.
 *
 * Los tipos de storable están escritos en cuatro sitios y en cuatro lenguajes:
 * aquí, en la SPA (`storableSchema.js`), en la API (`storable_specs.py`) y en
 * el motor Java (`AcheronCore`). Los cuatro deben coincidir en los nombres
 * EXACTOS de los campos, porque son las claves del JSON de la bóveda que los
 * clientes cifran y se intercambian.
 *
 * El modo de fallo es silencioso: si esta app escribe `cardholderName` y los
 * demás esperan `cardHolderName`, el campo no viaja. No hay excepción ni log;
 * el usuario ve que su tarjeta perdió el titular al abrirla desde la web.
 *
 * La copia versionada del contrato está en `src/test/resources`. Se compara
 * contra ella y no contra el repositorio remoto a propósito: un test que
 * necesite red no es un test, es una fuente de fallos intermitentes.
 *
 * Los campos se comparan como CONJUNTOS, no como listas: el orden no forma
 * parte del contrato, porque el JSON de la bóveda es un objeto con los campos
 * por nombre y no una tupla. Hoy hay una divergencia real y viva —esta app y
 * la SPA ordenan `creditcard` con `cvv` antes que `postalCode`, y la API y
 * AcheronCore al revés— que no rompe nada. Si algún día el orden importa, el
 * sitio donde decidirlo es AcheronSchema, no este test.
 */
class StorableSchemaContractTest {

    private data class SharedField(val key: String, val secret: Boolean)
    private data class SharedType(
        val kind: String,
        val category: String,
        val fields: List<SharedField>
    )

    private fun sharedSchema(): List<SharedType> {
        val stream = javaClass.classLoader!!.getResourceAsStream("acheron-schema.json")
        assertNotNull(
            "No está la copia de AcheronSchema en src/test/resources/acheron-schema.json. " +
                "Se copia del repositorio AcheronSchema a un tag concreto.",
            stream
        )
        val root = Json.parseToJsonElement(stream!!.bufferedReader().readText()).jsonObject
        return root["types"]!!.jsonArray.map { type ->
            val obj = type.jsonObject
            SharedType(
                kind = obj["kind"]!!.jsonPrimitive.content,
                category = obj["category"]!!.jsonPrimitive.content,
                fields = obj["fields"]!!.jsonArray.map { field ->
                    val f = field.jsonObject
                    SharedField(
                        key = f["key"]!!.jsonPrimitive.content,
                        secret = f["secret"]?.jsonPrimitive?.content == "true"
                    )
                }
            )
        }
    }

    @Test
    fun `the local schema declares exactly the kinds of the shared contract`() {
        assertEquals(
            sharedSchema().map { it.kind },
            StorableSchema.types.map { it.kind }
        )
    }

    @Test
    fun `every kind maps to the vault JSON list key the contract declares`() {
        sharedSchema().forEach { shared ->
            val local = StorableSchema.of(shared.kind)
            assertNotNull("falta el kind ${shared.kind}", local)
            assertEquals(
                "${shared.kind}: la clave de lista del JSON del vault no coincide",
                shared.category,
                local!!.category
            )
        }
    }

    @Test
    fun `every kind declares exactly the field keys of the shared contract`() {
        sharedSchema().forEach { shared ->
            assertEquals(
                "${shared.kind}: las claves de campo no coinciden con el contrato",
                shared.fields.map { it.key }.toSet(),
                StorableSchema.fieldKeys(shared.kind).toSet()
            )
        }
    }

    @Test
    fun `the same fields are marked secret here and in the contract`() {
        // Un campo que deja de estar marcado como secreto se muestra en claro
        // en el detalle y se escribe sin transformación de contraseña. Es el
        // tipo de divergencia que no rompe nada y expone datos.
        sharedSchema().forEach { shared ->
            val local = StorableSchema.of(shared.kind)!!
            assertEquals(
                "${shared.kind}: los campos secretos no coinciden con el contrato",
                shared.fields.filter { it.secret }.map { it.key }.toSet(),
                local.fields.filter { it.secret }.map { it.key }.toSet()
            )
        }
    }

    @Test
    fun `the UI registry exposes exactly what the schema declares`() {
        // StorableTypes compone sus campos desde StorableSchema, así que esto
        // no puede fallar por construcción — salvo que alguien deshaga la
        // composición y vuelva a escribir los campos a mano, que es
        // precisamente el retroceso que conviene detectar.
        StorableTypes.all.forEach { spec ->
            assertEquals(
                "${spec.kind}: la UI no expone las claves del esquema",
                StorableSchema.fieldKeys(spec.kind).toSet(),
                spec.fields.map { it.key }.toSet()
            )
            assertEquals(
                "${spec.kind}: la UI no respeta los campos secretos del esquema",
                StorableSchema.of(spec.kind)!!.fields.filter { it.secret }.map { it.key }.toSet(),
                spec.fields.filter { it.secret }.map { it.key }.toSet()
            )
        }
    }

    @Test
    fun `every field the UI shows has a non blank label`() {
        StorableTypes.all.forEach { spec ->
            assertTrue("${spec.kind}: sin etiqueta de tipo", spec.label.isNotBlank())
            assertTrue("${spec.kind}: sin plural", spec.plural.isNotBlank())
            assertTrue("${spec.kind}: sin etiqueta de alta", spec.newLabel.isNotBlank())
            spec.fields.forEach { field ->
                assertTrue(
                    "${spec.kind}.${field.key}: etiqueta vacía",
                    field.label.isNotBlank()
                )
            }
        }
    }
}
