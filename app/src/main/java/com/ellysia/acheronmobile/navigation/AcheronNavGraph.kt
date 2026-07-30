package com.ellysia.acheronmobile.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ellysia.acheronmobile.data.repository.AuthRepository
import com.ellysia.acheronmobile.data.repository.TokenRepository
import com.ellysia.acheronmobile.data.vault.StorableUi
import com.ellysia.acheronmobile.di.SessionEvents
import com.ellysia.acheronmobile.ui.account.AccountSettingsScreen
import com.ellysia.acheronmobile.ui.account.MfaSettingsViewModel
import com.ellysia.acheronmobile.ui.login.LoginScreen
import com.ellysia.acheronmobile.ui.login.LoginViewModel
import com.ellysia.acheronmobile.ui.mfa.MfaVerifyScreen
import com.ellysia.acheronmobile.ui.mfa.MfaVerifyViewModel
import com.ellysia.acheronmobile.ui.theme.BrandPrimaryButton
import com.ellysia.acheronmobile.ui.theme.BrandSpace
import com.ellysia.acheronmobile.ui.vault.MasterKeyScreen
import com.ellysia.acheronmobile.ui.vault.MasterKeyViewModel
import com.ellysia.acheronmobile.ui.vault.StorableDetailScreen
import com.ellysia.acheronmobile.ui.vault.StorableFormScreen
import com.ellysia.acheronmobile.ui.vault.VaultListScreen
import com.ellysia.acheronmobile.ui.vault.VaultViewModel

object Routes {
    const val LOGIN      = "login"
    const val MFA_VERIFY = "mfa_verify"
    const val MASTER_KEY = "master_key"
    const val VAULT_LIST = "vault_list"
    const val STORABLE_DETAIL = "storable_detail/{id}"
    const val STORABLE_ADD = "storable_add/{kind}"
    const val STORABLE_EDIT = "storable_edit/{id}"
    // "Cuenta / Ajustes Ellysia": configuración global de la cuenta (MFA, y a
    // futuro nombre/contraseña) — deliberadamente separada de las rutas del Vault.
    const val ACCOUNT_SETTINGS = "account_settings"
}

@Composable
fun AcheronNavGraph(
    navController: NavHostController,
    loginViewModel: LoginViewModel,
    vaultViewModel: VaultViewModel,
    authRepository: AuthRepository,
    tokenRepository: TokenRepository
) {
    val startDestination = if (loginViewModel.hasActiveSession)
        Routes.MASTER_KEY
    else
        Routes.LOGIN

    val onLogout: () -> Unit = {
        loginViewModel.logout()
        navController.navigate(Routes.LOGIN) {
            popUpTo(0) { inclusive = true }
        }
    }

    // Fin de sesión global (p.ej. la contraseña de acceso cambió en otro
    // dispositivo): cerrar sesión y llevar a login con el mensaje adecuado.
    val sessionEndReason by SessionEvents.endReason.collectAsStateWithLifecycle()
    LaunchedEffect(sessionEndReason) {
        val reason = sessionEndReason ?: return@LaunchedEffect
        loginViewModel.logout()
        loginViewModel.notifySessionEnded(reason)
        SessionEvents.consume()
        navController.navigate(Routes.LOGIN) {
            popUpTo(0) { inclusive = true }
        }
    }

    // Bloqueo del vault (manual, o autobloqueo tras volver de segundo plano,
    // ver S2/F4 en docs/code-review.md) reaccionando a nivel global en vez de
    // solo dentro de VaultListScreen: si no, bloquear mientras el usuario está
    // en el detalle o el formulario de un storable no llevaría a ningún sitio,
    // porque esas pantallas no observaban `locked`.
    //
    // `crypto.state` empieza en `Locked` por defecto (antes de cualquier
    // login), así que este efecto solo debe actuar tras haber visto un
    // `Unlocked` real — si no, redirigiría a MASTER_KEY incluso desde la
    // pantalla de LOGIN en el arranque en frío. Tampoco debe disparar cuando
    // el bloqueo viene de un logout (que ya navega a LOGIN por su cuenta):
    // se comprueba que la sesión de acceso siga viva.
    val vaultUiState by vaultViewModel.uiState.collectAsStateWithLifecycle()
    var vaultWasUnlocked by remember { mutableStateOf(false) }
    LaunchedEffect(vaultUiState.locked) {
        if (!vaultUiState.locked) {
            vaultWasUnlocked = true
        } else if (vaultWasUnlocked) {
            vaultWasUnlocked = false
            if (loginViewModel.hasActiveSession) {
                navController.navigate(Routes.MASTER_KEY) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    // Puente en memoria entre el login (paso 1) y la verificación MFA (paso 2):
    // el challengeToken es efímero (~5 min) y no debe persistirse en disco.
    var pendingChallengeToken by remember { mutableStateOf<String?>(null) }
    var pendingUsername by remember { mutableStateOf("") }

    NavHost(
        navController    = navController,
        startDestination = startDestination
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel    = loginViewModel,
                onLoginSuccess = {
                    navController.navigate(Routes.MASTER_KEY) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onMfaRequired = { challengeToken ->
                    pendingChallengeToken = challengeToken
                    pendingUsername = loginViewModel.uiState.value.username
                    navController.navigate(Routes.MFA_VERIFY)
                }
            )
        }

        composable(Routes.MFA_VERIFY) {
            val challengeToken = pendingChallengeToken
            if (challengeToken == null) {
                // Ruta alcanzada sin un reto pendiente (p. ej. restauración de
                // estado tras proceso muerto): no hay nada que verificar.
                // `navigate` es un efecto secundario y debe ir en un
                // LaunchedEffect, no ejecutarse directamente durante la
                // composición (ver B4 en docs/code-review.md).
                LaunchedEffect(Unit) {
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                }
            } else {
                val mfaVerifyViewModel: MfaVerifyViewModel = viewModel(
                    key = challengeToken,
                    factory = viewModelFactory {
                        initializer { MfaVerifyViewModel(challengeToken, pendingUsername, authRepository, tokenRepository) }
                    }
                )
                MfaVerifyScreen(
                    viewModel = mfaVerifyViewModel,
                    onVerified = {
                        pendingChallengeToken = null
                        navController.navigate(Routes.MASTER_KEY) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable(Routes.ACCOUNT_SETTINGS) {
            val mfaSettingsViewModel: MfaSettingsViewModel = viewModel(
                factory = viewModelFactory { initializer { MfaSettingsViewModel() } }
            )
            AccountSettingsScreen(
                mfaViewModel = mfaSettingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.MASTER_KEY) {
            val masterKeyVM: MasterKeyViewModel = viewModel()
            MasterKeyScreen(
                viewModel = masterKeyVM,
                onVaultUnlocked = {
                    navController.navigate(Routes.VAULT_LIST) {
                        popUpTo(Routes.MASTER_KEY) { inclusive = true }
                    }
                },
                onLogout = onLogout
            )
        }

        composable(Routes.VAULT_LIST) {
            VaultListScreen(
                viewModel = vaultViewModel,
                onAdd = { kind ->
                    navController.navigate("storable_add/$kind")
                },
                onStorableClick = { storable ->
                    navController.navigate("storable_detail/${storable.id}")
                },
                onOpenAccountSettings = {
                    navController.navigate(Routes.ACCOUNT_SETTINGS)
                },
                onLogout = onLogout
            )
        }

        composable(
            route = Routes.STORABLE_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")
            // Lectura reactiva (antes `.value`, una foto fija que no se
            // recomponía si el estado cambiaba tras entrar) y con una salida
            // explícita cuando el id no existe, en vez de una pantalla en
            // blanco sin barra ni botón de volver (ver B5 en docs/code-review.md).
            val uiState by vaultViewModel.uiState.collectAsStateWithLifecycle()
            val storable = id?.let { theId -> uiState.storables.find { it.id == theId } }
            if (storable != null) {
                StorableDetailScreen(
                    storable = storable,
                    vaultViewModel = vaultViewModel,
                    onBack = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() },
                    // Navega a la ruta STORABLE_EDIT en vez de alternar un
                    // estado local: esa ruta ya existía pero nunca se usaba,
                    // dejando dos caminos para lo mismo y el gesto de
                    // retroceso del sistema saliendo del detalle en vez de
                    // volver a él durante la edición (ver D6 en docs/code-review.md).
                    onEdit = { navController.navigate("storable_edit/${storable.id}") }
                )
            } else {
                MissingStorableContent(onBack = { navController.popBackStack() })
            }
        }

        composable(
            route = Routes.STORABLE_ADD,
            arguments = listOf(navArgument("kind") { type = NavType.StringType })
        ) { backStackEntry ->
            val kind = backStackEntry.arguments?.getString("kind") ?: "account"
            StorableFormScreen(
                vaultViewModel = vaultViewModel,
                defaultKind = kind,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.STORABLE_EDIT,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")
            val uiState by vaultViewModel.uiState.collectAsStateWithLifecycle()
            val storable = id?.let { theId -> uiState.storables.find { it.id == theId } }
            if (storable != null) {
                StorableFormScreen(
                    storable = storable,
                    vaultViewModel = vaultViewModel,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            } else {
                MissingStorableContent(onBack = { navController.popBackStack() })
            }
        }
    }
}

/** Se muestra cuando la ruta de detalle/edición no encuentra el id (eliminado, o la bóveda aún cargando). */
@Composable
private fun MissingStorableContent(onBack: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(BrandSpace.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Elemento no disponible",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(BrandSpace.xs))
            Text(
                "Puede que se haya eliminado o que la bóveda aún se esté cargando.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(BrandSpace.lg))
            BrandPrimaryButton(text = "Volver", onClick = onBack, modifier = Modifier.fillMaxWidth())
        }
    }
}
