package com.seq.acheronmobile.ui.mfa

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seq.acheronmobile.ui.theme.AcheronAuthScaffold
import com.seq.acheronmobile.ui.theme.AcheronLockup
import com.seq.acheronmobile.ui.theme.BrandField
import com.seq.acheronmobile.ui.theme.BrandPanel
import com.seq.acheronmobile.ui.theme.BrandPrimaryButton
import com.seq.acheronmobile.ui.theme.BrandSpace
import com.seq.acheronmobile.ui.theme.SectionLabel

/**
 * Segundo paso del login: verificación en dos pasos (TOTP o código de
 * recuperación). Se navega aquí desde LoginScreen cuando la cuenta tiene
 * MFA activo; nunca se accede directamente.
 */
@Composable
fun MfaVerifyScreen(
    viewModel: MfaVerifyViewModel,
    onVerified: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState.verifySuccess) {
        if (uiState.verifySuccess) {
            onVerified()
            viewModel.onNavigatedToVault()
        }
    }

    AcheronAuthScaffold {
        AcheronLockup(subtitle = "Un paso más para entrar a tu bóveda.")

        BrandPanel(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(BrandSpace.lg),
                verticalArrangement = Arrangement.spacedBy(BrandSpace.md)
            ) {
                SectionLabel("Verificación en dos pasos")
                Text(
                    if (uiState.useRecovery) "Código de recuperación" else "Código de tu app de autenticación",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )

                BrandField(
                    value = uiState.code,
                    onValueChange = viewModel::onCodeChange,
                    label = if (uiState.useRecovery) "Código de recuperación" else "Código de 6 dígitos",
                    leadingIcon = Icons.Filled.Shield,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (uiState.useRecovery) KeyboardType.Text else KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus(); viewModel.onVerifyClick() }
                    ),
                    isError = uiState.errorMessage != null,
                    enabled = !uiState.isLoading
                )

                AnimatedVisibility(
                    visible = uiState.errorMessage != null,
                    enter = fadeIn(), exit = fadeOut()
                ) {
                    uiState.errorMessage?.let { msg ->
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                BrandPrimaryButton(
                    text = "Verificar",
                    onClick = { focusManager.clearFocus(); viewModel.onVerifyClick() },
                    enabled = !uiState.isLoading,
                    loading = uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                TextButton(
                    onClick = { focusManager.clearFocus(); viewModel.onToggleRecovery() },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (uiState.useRecovery) "Usar código de la app en su lugar" else "Usar un código de recuperación",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        Text(
            "SeQ · SecOps Platform",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = BrandSpace.sm)
        )
    }
}
