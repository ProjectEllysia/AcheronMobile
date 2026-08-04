package com.ellysia.acheronmobile.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Barra superior de marca: flecha de volver, título (con subtítulo opcional
 * de una línea) y un slot de acciones al final.
 *
 * Antes existían tres versiones de esto casi idénticas —`VaultHeader` en
 * `VaultListScreen`, un `BrandTopBar` privado en `StorableScreens` y
 * `AccountSettingsHeader` en `AccountSettingsScreen`— con la misma estructura
 * (`Row` + fondo + `statusBarsPadding` + título + acciones). `VaultHeader` se
 * dejó aparte a propósito: no tiene flecha de volver (usa la marca del río) y
 * encadena varios iconos de acción con un divisor, así que forzarlo a este
 * mismo componente habría complicado la firma sin ganar nada (ver D5 en
 * docs/code-review.md).
 */
@Composable
fun BrandTopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = BrandSpace.sm, vertical = BrandSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = MaterialTheme.colorScheme.onBackground)
        }
        Column(Modifier.weight(1f).padding(start = BrandSpace.xs)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        actions()
    }
}
