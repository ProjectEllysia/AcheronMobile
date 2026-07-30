package com.ellysia.acheronmobile.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ellysia.acheronmobile.data.security.AutoLockPreferences
import com.ellysia.acheronmobile.data.security.BiometricMasterPasswordStore
import com.ellysia.acheronmobile.di.VaultServiceLocator
import com.ellysia.acheronmobile.ui.security.BiometricGate
import com.ellysia.acheronmobile.ui.theme.BrandField
import com.ellysia.acheronmobile.ui.theme.BrandPanel
import com.ellysia.acheronmobile.ui.theme.BrandPrimaryButton
import com.ellysia.acheronmobile.ui.theme.BrandSecondaryButton
import com.ellysia.acheronmobile.ui.theme.BrandSpace
import com.ellysia.acheronmobile.ui.theme.BrandTopBar
import com.ellysia.acheronmobile.ui.theme.SectionLabel
import com.ellysia.acheronmobile.util.copySensitiveText
import kotlinx.coroutines.launch

/**
 * "Cuenta / Ajustes Ellysia": configuración global de la cuenta (identidad,
 * seguridad de acceso — MFA, y a futuro contraseña/nombre). Deliberadamente
 * separada de las pantallas del Vault (VaultListScreen, MasterKeyScreen):
 * aquí se gestiona la cuenta en el servidor, no los secretos cifrados.
 */
@Composable
fun AccountSettingsScreen(
    mfaViewModel: MfaSettingsViewModel,
    onBack: () -> Unit,
    biometricViewModel: BiometricSettingsViewModel = viewModel(),
    autoLockViewModel: AutoLockSettingsViewModel = viewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            BrandTopBar(
                title = "Cuenta y ajustes",
                subtitle = "Configuración de tu cuenta Ellysia",
                onBack = onBack
            )
        }
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
            item {
                BiometricSection(viewModel = biometricViewModel, snackbarHostState = snackbarHostState)
            }
            item {
                AutoLockSection(viewModel = autoLockViewModel)
            }
            // Futuras secciones de cuenta (nombre, contraseña) van aquí, como
            // paneles hermanos de MfaSection — mismo contenedor, misma sección.
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
                    onCancel = viewModel::cancelSetup,
                    snackbarHostState = snackbarHostState
                )
            }
        }
    }

    if (uiState.recoveryCodes != null) {
        RecoveryCodesDialog(
            codes = uiState.recoveryCodes!!,
            onAcknowledge = viewModel::acknowledgeRecoveryCodes,
            snackbarHostState = snackbarHostState
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

// ── Sección huella ─────────────────────────────────────────────────────────

/**
 * Activar/desactivar el desbloqueo con huella desde Ajustes. Antes solo se
 * ofrecía una vez, justo tras un desbloqueo manual; si el usuario la
 * rechazaba, no había otra forma de activarla salvo cerrar sesión (ver F6 en
 * docs/code-review.md).
 */
@Composable
private fun BiometricSection(
    viewModel: BiometricSettingsViewModel,
    snackbarHostState: SnackbarHostState
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val store = VaultServiceLocator.biometricStore
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.setAvailable(activity != null && BiometricGate.isAvailable(context))
    }

    BrandPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(BrandSpace.lg),
            verticalArrangement = Arrangement.spacedBy(BrandSpace.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Fingerprint, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(22.dp))
                Spacer(Modifier.width(BrandSpace.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Desbloqueo con huella",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        when {
                            !uiState.available -> "No disponible en este dispositivo"
                            uiState.enabled -> "Activado para abrir la bóveda"
                            else -> "Abre la bóveda con tu huella en vez de la clave maestra"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.enabled,
                    enabled = uiState.available,
                    onCheckedChange = { checked ->
                        if (checked) viewModel.showEnableDialog() else viewModel.disable()
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                )
            }
        }
    }

    if (uiState.showEnableDialog) {
        BiometricEnrollDialog(
            password = uiState.password,
            isBusy = uiState.isBusy,
            errorMessage = uiState.errorMessage,
            onPasswordChange = viewModel::onPasswordChange,
            onDismiss = viewModel::dismissEnableDialog,
            onConfirm = {
                if (activity == null) {
                    viewModel.onEnrollFailed("No se pudo iniciar la huella")
                    return@BiometricEnrollDialog
                }
                viewModel.setBusy(true)
                if (viewModel.verifyPassword()) {
                    launchBiometricEnroll(activity, store, viewModel) {
                        scope.launch { snackbarHostState.showSnackbar("Huella activada") }
                    }
                } else {
                    viewModel.setBusy(false)
                }
            }
        )
    }
}

@Composable
private fun BiometricEnrollDialog(
    password: String,
    isBusy: Boolean,
    errorMessage: String?,
    onPasswordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        icon = { Icon(Icons.Filled.Fingerprint, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Activar huella") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BrandSpace.sm)) {
                Text(
                    "Confirma tu clave maestra para guardarla de forma segura tras tu huella. " +
                        "Si cambias la contraseña más adelante, tendrás que activarla de nuevo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                BrandField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = "Clave maestra",
                    enabled = !isBusy,
                    isError = errorMessage != null,
                    supportingText = errorMessage,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !isBusy && password.isNotBlank(), onClick = onConfirm) {
                Text("Activar")
            }
        },
        dismissButton = {
            TextButton(enabled = !isBusy, onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/** Lanza el BiometricPrompt para cifrar y guardar la contraseña maestra ya verificada. */
private fun launchBiometricEnroll(
    activity: FragmentActivity,
    store: BiometricMasterPasswordStore,
    viewModel: BiometricSettingsViewModel,
    onSuccess: () -> Unit
) {
    val cipher = try {
        store.encryptCipher()
    } catch (_: Exception) {
        viewModel.onEnrollFailed("No se pudo preparar la huella")
        return
    }
    BiometricGate.authenticate(
        activity = activity,
        cipher = cipher,
        title = "Activar huella",
        subtitle = "Confirma tu huella para guardar la clave maestra de forma segura",
        negativeText = "Cancelar",
        onSuccess = { authenticated ->
            try {
                store.finishEnroll(authenticated, viewModel.masterPasswordForEnroll())
                viewModel.onEnrolled()
                onSuccess()
            } catch (_: Exception) {
                viewModel.onEnrollFailed("No se pudo guardar la huella")
            }
        },
        onError = { _, _ -> viewModel.onEnrollFailed("Autenticación cancelada") }
    )
}

// ── Sección autobloqueo ────────────────────────────────────────────────────

/**
 * Minutos de inactividad en segundo plano antes de bloquear la bóveda (ver
 * F4 en docs/code-review.md, cara de producto de S2).
 */
@Composable
private fun AutoLockSection(viewModel: AutoLockSettingsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BrandPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(BrandSpace.lg),
            verticalArrangement = Arrangement.spacedBy(BrandSpace.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Timer, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(22.dp))
                Spacer(Modifier.width(BrandSpace.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Autobloqueo",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Bloquea la bóveda tras este tiempo en segundo plano",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(BrandSpace.sm)) {
                AutoLockPreferences.OPTIONS.forEach { minutes ->
                    AutoLockOption(
                        label = autoLockLabel(minutes),
                        selected = uiState.timeoutMinutes == minutes,
                        onClick = { viewModel.setTimeoutMinutes(minutes) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun autoLockLabel(minutes: Int): String = if (minutes == 0) "Inmediato" else "$minutes min"

@Composable
private fun AutoLockOption(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
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
    onCancel: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
            IconButton(onClick = {
                copySensitiveText(context, scope, "Secreto TOTP", secret)
                scope.launch { snackbarHostState.showSnackbar("Secreto copiado — se borrará del portapapeles en 45 s") }
            }) {
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
private fun RecoveryCodesDialog(
    codes: List<String>,
    onAcknowledge: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
                TextButton(onClick = {
                    copySensitiveText(context, scope, "Códigos de recuperación", codes.joinToString("\n"))
                    scope.launch { snackbarHostState.showSnackbar("Códigos copiados — se borrarán del portapapeles en 45 s") }
                }) {
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
