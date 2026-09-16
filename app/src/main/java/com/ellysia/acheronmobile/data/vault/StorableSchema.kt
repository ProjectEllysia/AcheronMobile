package com.ellysia.acheronmobile.data.vault

/**
 * Catálogo de tipos de storable: el CONTRATO DE DATOS, sin nada que se vea en
 * pantalla.
 *
 * Qué tipos existen, qué claves tiene cada uno y cuáles son sensibles. Estas
 * claves viajan literalmente en el JSON de la bóveda, así que tienen que
 * coincidir con las de los otros tres clientes: la SPA
 * (`storableSchema.js`), la API (`storable_specs.py`) y el motor Java
 * (`AcheronCore`). Un nombre distinto en un solo sitio hace que ese cliente no
 * encuentre el campo, sin ningún error que lo delate.
 *
 * La fuente de verdad común es `schema/schema.json` en el repositorio
 * [AcheronCore](https://github.com/ProjectEllysia/AcheronCore), en el tag de la
 * versión del motor que usa esta app; `StorableSchemaContractTest` comprueba
 * que esta copia no diverge de él.
 *
 * Vive fuera de `ui/` a propósito. Antes el catálogo estaba dentro de
 * `ui/vault/StorableTypes.kt`, mezclado con iconos de Compose, colores de
 * marca y etiquetas en castellano, y eso hacía imposible compartirlo: ni la
 * API ni el motor Java pueden consumir un fichero que importa
 * `androidx.compose`. Aquí no hay una sola dependencia de UI, y la
 * presentación sigue en `StorableTypes`, que compone ambas mitades.
 */
object StorableSchema {

    /** Un campo de un storable. `secret` marca los sensibles. */
    data class Field(val key: String, val secret: Boolean = false)

    /**
     * Un tipo de storable.
     *
     * @param kind     singular; lo que espera la API en `POST /acheron/storables`
     * @param category plural; la clave de lista dentro del JSON de la bóveda
     */
    data class Type(val kind: String, val category: String, val fields: List<Field>)

    val types: List<Type> = listOf(
        Type("account", "accounts", listOf(
            Field("username"),
            Field("domain"),
            Field("password", secret = true)
        )),
        Type("creditcard", "creditcards", listOf(
            Field("cardHolderName"),
            Field("cardNumber", secret = true),
            Field("expirationDate"),
            Field("cvv", secret = true),
            Field("postalCode")
        )),
        Type("securenote", "securenotes", listOf(
            Field("content")
        )),
        Type("identity", "identities", listOf(
            Field("fullName"),
            Field("email"),
            Field("phone"),
            Field("address"),
            Field("city"),
            Field("country"),
            Field("documentId", secret = true)
        )),
        Type("bankaccount", "bankaccounts", listOf(
            Field("bankName"),
            Field("holder"),
            Field("iban", secret = true),
            Field("swiftBic", secret = true),
            Field("accountNumber", secret = true)
        )),
        Type("wifi", "wifinetworks", listOf(
            Field("ssid"),
            Field("password", secret = true),
            Field("securityType")
        )),
        Type("license", "licenses", listOf(
            Field("product"),
            Field("licenseKey", secret = true),
            Field("licensedTo"),
            Field("version")
        ))
    )

    private val byKind: Map<String, Type> = types.associateBy { it.kind }

    /** Tipo por kind, o `null` si el kind no está en el catálogo. */
    fun of(kind: String): Type? = byKind[kind]

    /** Claves de los campos de un kind, en orden; vacía si el kind es desconocido. */
    fun fieldKeys(kind: String): List<String> = of(kind)?.fields?.map { it.key } ?: emptyList()
}
