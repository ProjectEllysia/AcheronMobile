package com.seq.acheronmobile.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seq.acheronmobile.ui.theme.BrandField
import com.seq.acheronmobile.ui.theme.BrandPanel
import com.seq.acheronmobile.ui.theme.BrandPrimaryButton
import com.seq.acheronmobile.ui.theme.BrandSecondaryButton
import com.seq.acheronmobile.ui.theme.BrandSpace
import com.seq.acheronmobile.ui.theme.SectionLabel

/**
 * "Cuenta / Ajustes Ellysia": configuración global de la cuenta (identidad,
 * seguridad de acceso — MFA, y a futuro contraseña/nombre). Deliberadamente
 * separada de las pantallas del Vault (VaultListScreen, MasterKeyScreen):
 * aquí se gestiona la cuenta en el servidor, no los secretos cifrados.
 */
@Composable
fun AccountSettingsScreen(
    mfaViewModel: MfaSettingsViewModel,
    onBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { AccountSettingsHeader(onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(BrandSpace.md),
            verticalArrangement = Arrangement.spacedBy(BrandSpace.md)
        ) {
            item {
                SectionLabel("Seguridad de acceso")
            }
            item {
                MfaSection(viewModel = mfaViewModel, snackbarHostState = snackbarHostState)
            }
            // Futuras secciones de cuenta (nombre, contraseña) van aquí, como
            // paneles hermanos de MfaSection — mismo contenedor, misma sección.
        }
    }
}

@Composable
private fun AccountSettingsHeader(onBack: () -> Unit) {
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
                "Cuenta y ajustes",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            Text(
                "Configuración de tu cuenta Ellysia",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Sección MFA ────────────────────────────────────────────────────────────

@Composable
private fun MfaSection(
    viewModel: MfaSettingsViewModel,
    snackbarHostState: SnackbarHostState
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    BrandPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(BrandSpace.lg),
            verticalArrangement = Arrangement.spacedBy(BrandSpace.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(22.dp))
                Spacer(Modifier.width(BrandSpace.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Verificación en dos pasos",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        if (uiState.isLoading) "Comprobando…"
                        else if (uiState.enabled) "Activada con una app de autenticación"
                        else "Añade una capa extra de seguridad a tu cuenta",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (uiState.isLoading) {
                    CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                } else {
                    Switch(
                        checked = uiState.enabled,
                        onCheckedChange = { checked ->
                            if (checked) viewModel.startSetup() else viewModel.showDisableDialog()
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                    )
                }
            }

            // Flujo de activación: setup (QR/secreto) → confirmar código.
            if (uiState.setupProvisioningUri != null) {
                TotpSetupBlock(
                    provisioningUri = uiState.setupProvisioningUri!!,
                    secret = uiState.setupSecret.orEmpty(),
                    code = uiState.confirmCode,
                    isBusy = uiState.isBusy,
                    onCodeChange = viewModel::onConfirmCodeChange,
                    onConfirm = viewModel::confirmSetup,
                    onCancel = viewModel::cancelSetup
                )
            }
        }
    }

    if (uiState.recoveryCodes != null) {
        RecoveryCodesDialog(
            codes = uiState.recoveryCodes!!,
            onAcknowledge = viewModel::acknowledgeRecoveryCodes
        )
    }

    if (uiState.showDisableDialog) {
        DisableMfaDialog(
            code = uiState.disableCode,
            isBusy = uiState.isBusy,
            onCodeChange = viewModel::onDisableCodeChange,
            onConfirm = viewModel::confirmDisable,
            onDismiss = viewModel::dismissDisableDialog
        )
    }
}

@Composable
private fun TotpSetupBlock(
    provisioningUri: String,
    secret: String,
    code: String,
    isBusy: Boolean,
    onCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    Column(verticalArrangement = Arrangement.spacedBy(BrandSpace.sm)) {
        Text(
            "Escanea este secreto con tu app de autenticación (Google Authenticator, Authy…) o cópialo manualmente:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = BrandSpace.md, vertical = BrandSpace.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                secret,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { clipboard.setText(AnnotatedString(secret)) }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar secreto", tint = MaterialTheme.colorScheme.primary)
            }
        }
        // Nota: sin librería de generación de QR en el proyecto todavía; se
        // ofrece el secreto/URI en texto. Añadir un renderizado de QR desde
        // provisioningUri es una mejora incremental (ver plan).
        Text(
            provisioningUri,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 2
        )
        BrandField(
            value = code,
            onValueChange = onCodeChange,
            label = "Código de 6 dígitos",
            enabled = !isBusy
        )
        Row(horizontalArrangement = Arrangement.spacedBy(BrandSpace.sm)) {
            BrandSecondaryButton(
                text = "Cancelar",
                onClick = onCancel,
                enabled = !isBusy,
                modifier = Modifier.weight(1f)
            )
            BrandPrimaryButton(
                text = "Confirmar",
                onClick = onConfirm,
                enabled = !isBusy,
                loading = isBusy,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RecoveryCodesDialog(codes: List<String>, onAcknowledge: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = { /* Debe reconocerse explícitamente: solo se muestran una vez. */ },
        icon = { Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("MFA activado") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BrandSpace.sm)) {
                Text(
                    "Guarda estos códigos de recuperación en un lugar seguro. Cada uno " +
                        "sirve una sola vez si pierdes acceso a tu app de autenticación. " +
                        "No volverán a mostrarse.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(BrandSpace.md),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    codes.forEach { code ->
                        Text(code, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
                TextButton(onClick = { clipboard.setText(AnnotatedString(codes.joinToString("\n"))) }) {
                    Text("Copiar todos")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAcknowledge) { Text("Ya los guardé") }
        }
    )
}

@Composable
private fun DisableMfaDialog(
    code: String,
    isBusy: Boolean,
    onCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        icon = { Icon(Icons.Filled.Shield, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Desactivar verificación en dos pasos") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BrandSpace.sm)) {
                Text(
                    "Tu cuenta quedará protegida solo con la contraseña. Confirma con un " +
                        "código vigente de tu app o un código de recuperación.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                BrandField(
                    value = code,
                    onValueChange = onCodeChange,
                    label = "Código",
                    enabled = !isBusy
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !isBusy, onClick = onConfirm) {
                Text("Desactivar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(enabled = !isBusy, onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
