package com.ellysia.acheronmobile.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Diálogo de confirmación de "Cerrar sesión de Ellysia", antes duplicado
 * íntegro en `VaultListScreen.kt` y `MasterKeyScreen.kt` (ver D5 en
 * docs/code-review.md).
 */
@Composable
fun LogoutConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Cerrar sesión de Ellysia") },
        text = {
            Text(
                "Se cerrará tu sesión por completo. La próxima vez deberás " +
                    "iniciar sesión de nuevo con tu usuario y contraseña de Ellysia."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Cerrar sesión", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
