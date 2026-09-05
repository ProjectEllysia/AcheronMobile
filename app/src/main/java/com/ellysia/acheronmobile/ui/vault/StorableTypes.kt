package com.ellysia.acheronmobile.ui.vault

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.ellysia.acheronmobile.data.vault.StorableSchema
import com.ellysia.acheronmobile.ui.theme.AcheronGold

/**
 * Descriptor de un campo editable de un storable.
 *
 * @param key      clave del campo (coincide con el JSON del core y con la API)
 * @param label    etiqueta legible mostrada en formulario y detalle
 * @param secret   si es secreto: se enmascara con revelar en el detalle y se
 *                 escribe con transformación de contraseña en el formulario
 * @param prefill  si en edición se pre-rellena con el valor actual; los secretos
 *                 cuyo valor no se expone (p. ej. el PAN) deben dejarse en false
 * @param numeric  teclado numérico
 * @param multiline campo de varias líneas (notas)
 * @param minLength longitud mínima exigida cuando el campo lleva valor
 */
data class FieldSpec(
    val key: String,
    val label: String,
    val secret: Boolean = false,
    val prefill: Boolean = true,
    val numeric: Boolean = false,
    val multiline: Boolean = false,
    val minLength: Int = 0
)

/**
 * Especificación de un tipo de storable. Es la única fuente de verdad para la
 * UI: el formulario, la lista y el detalle se construyen a partir de aquí, de
 * modo que añadir un tipo nuevo no exige tocar ramas `if` por toda la interfaz.
 *
 * @param accent acento de marca; `null` ⇒ se usa el `primary` del tema (que se
 *               adapta a claro/oscuro). El resto definen un color propio.
 */
data class StorableTypeSpec(
    val kind: String,
    val label: String,
    val plural: String,
    val newLabel: String,
    val icon: ImageVector,
    val accent: Color?,
    val fields: List<FieldSpec>,
    val subtitleKey: String?
) {
    fun field(key: String): FieldSpec? = fields.firstOrNull { it.key == key }
}

/** Lo que el esquema no dice de un campo porque no le compete: su aspecto. */
private data class FieldUi(
    val label: String,
    val prefill: Boolean = true,
    val numeric: Boolean = false,
    val multiline: Boolean = false,
    val minLength: Int = 0
)

/**
 * Compone los [FieldSpec] de un kind: las claves y `secret` salen del contrato
 * compartido ([StorableSchema]) y el resto de aquí.
 *
 * Así el catálogo de campos no está escrito dos veces dentro de esta misma app:
 * si el contrato añade un campo, esta función falla al arrancar por falta de
 * presentación en vez de dejar un campo invisible en el formulario.
 */
private fun fieldsOf(kind: String, vararg ui: Pair<String, FieldUi>): List<FieldSpec> {
    val presentation = ui.toMap()
    val type = requireNotNull(StorableSchema.of(kind)) { "kind '$kind' no está en StorableSchema" }
    return type.fields.map { field ->
        val look = requireNotNull(presentation[field.key]) {
            "falta la presentación del campo '${field.key}' de '$kind'"
        }
        FieldSpec(
            key = field.key,
            label = look.label,
            secret = field.secret,
            prefill = look.prefill,
            numeric = look.numeric,
            multiline = look.multiline,
            minLength = look.minLength
        )
    }
}

object StorableTypes {

    // Acentos propios de los tipos nuevos, legibles sobre el fondo casi negro.
    private val NoteBlue = Color(0xFF8AA6F0)
    private val IdentityGreen = Color(0xFF6FCF97)
    private val BankSky = Color(0xFF56CCF2)
    private val WifiTeal = Color(0xFF4FD1C5)
    private val LicenseAmber = Color(0xFFE0A458)

    val account = StorableTypeSpec(
        kind = "account",
        label = "Cuenta",
        plural = "Cuentas",
        newLabel = "Nueva cuenta",
        icon = Icons.Filled.Person,
        accent = null,
        fields = fieldsOf("account",
            "username" to FieldUi("Usuario / Email"),
            "domain" to FieldUi("Dominio / Servicio"),
            "password" to FieldUi("Contraseña", prefill = false)
        ),
        subtitleKey = "username"
    )

    val creditCard = StorableTypeSpec(
        kind = "creditcard",
        label = "Tarjeta",
        plural = "Tarjetas",
        newLabel = "Nueva tarjeta",
        icon = Icons.Filled.CreditCard,
        accent = AcheronGold,
        fields = fieldsOf("creditcard",
            "cardHolderName" to FieldUi("Titular"),
            "cardNumber" to FieldUi("Número de tarjeta", prefill = false, numeric = true, minLength = 4),
            "expirationDate" to FieldUi("Caducidad (MM/YY)"),
            "cvv" to FieldUi("CVV", prefill = false, numeric = true),
            "postalCode" to FieldUi("Código postal")
        ),
        subtitleKey = "cardNumber"
    )

    val secureNote = StorableTypeSpec(
        kind = "securenote",
        label = "Nota segura",
        plural = "Notas",
        newLabel = "Nueva nota",
        icon = Icons.Filled.Description,
        accent = NoteBlue,
        fields = fieldsOf("securenote",
            "content" to FieldUi("Contenido", multiline = true)
        ),
        subtitleKey = "content"
    )

    val identity = StorableTypeSpec(
        kind = "identity",
        label = "Identidad",
        plural = "Identidades",
        newLabel = "Nueva identidad",
        icon = Icons.Filled.Badge,
        accent = IdentityGreen,
        fields = fieldsOf("identity",
            "fullName" to FieldUi("Nombre completo"),
            "email" to FieldUi("Email"),
            "phone" to FieldUi("Teléfono"),
            "address" to FieldUi("Dirección"),
            "city" to FieldUi("Ciudad"),
            "country" to FieldUi("País"),
            "documentId" to FieldUi("Documento (DNI/Pasaporte)")
        ),
        subtitleKey = "fullName"
    )

    val bankAccount = StorableTypeSpec(
        kind = "bankaccount",
        label = "Cuenta bancaria",
        plural = "Bancos",
        newLabel = "Nueva cuenta bancaria",
        icon = Icons.Filled.AccountBalance,
        accent = BankSky,
        fields = fieldsOf("bankaccount",
            "bankName" to FieldUi("Banco"),
            "holder" to FieldUi("Titular"),
            "iban" to FieldUi("IBAN"),
            "swiftBic" to FieldUi("SWIFT / BIC"),
            "accountNumber" to FieldUi("Número de cuenta")
        ),
        subtitleKey = "bankName"
    )

    val wifi = StorableTypeSpec(
        kind = "wifi",
        label = "Wi-Fi",
        plural = "Wi-Fi",
        newLabel = "Nueva red Wi-Fi",
        icon = Icons.Filled.Wifi,
        accent = WifiTeal,
        fields = fieldsOf("wifi",
            "ssid" to FieldUi("Nombre de red (SSID)"),
            "password" to FieldUi("Contraseña", prefill = false),
            "securityType" to FieldUi("Seguridad (WPA2/WPA3)")
        ),
        subtitleKey = "ssid"
    )

    val license = StorableTypeSpec(
        kind = "license",
        label = "Licencia",
        plural = "Licencias",
        newLabel = "Nueva licencia",
        icon = Icons.Filled.Key,
        accent = LicenseAmber,
        fields = fieldsOf("license",
            "product" to FieldUi("Producto"),
            "licenseKey" to FieldUi("Clave de licencia"),
            "licensedTo" to FieldUi("Licenciado a"),
            "version" to FieldUi("Versión")
        ),
        subtitleKey = "product"
    )

    /** Orden de presentación en selectores, menús y filtros. */
    val all: List<StorableTypeSpec> = listOf(
        account, creditCard, secureNote, identity, bankAccount, wifi, license
    )

    private val byKind: Map<String, StorableTypeSpec> = all.associateBy { it.kind }

    /** Spec por kind, con un descriptor de reserva tolerante para kinds desconocidos. */
    fun of(kind: String): StorableTypeSpec = byKind[kind] ?: StorableTypeSpec(
        kind = kind,
        label = kind.replaceFirstChar { it.uppercase() },
        plural = kind,
        newLabel = "Nuevo elemento",
        icon = Icons.Filled.Description,
        accent = null,
        fields = emptyList(),
        subtitleKey = null
    )
}

/** Resuelve el acento del tipo dentro de una composición (respeta el tema). */
@Composable
fun StorableTypeSpec.accentColor(): Color = accent ?: MaterialTheme.colorScheme.primary
