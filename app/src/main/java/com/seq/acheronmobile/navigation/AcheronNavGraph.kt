package com.seq.acheronmobile.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.seq.acheronmobile.data.repository.AuthRepository
import com.seq.acheronmobile.data.repository.TokenRepository
import com.seq.acheronmobile.data.vault.StorableUi
import com.seq.acheronmobile.di.SessionEvents
import com.seq.acheronmobile.ui.account.AccountSettingsScreen
import com.seq.acheronmobile.ui.account.MfaSettingsViewModel
import com.seq.acheronmobile.ui.login.LoginScreen
import com.seq.acheronmobile.ui.login.LoginViewModel
import com.seq.acheronmobile.ui.mfa.MfaVerifyScreen
import com.seq.acheronmobile.ui.mfa.MfaVerifyViewModel
import com.seq.acheronmobile.ui.vault.MasterKeyScreen
import com.seq.acheronmobile.ui.vault.MasterKeyViewModel
import com.seq.acheronmobile.ui.vault.StorableDetailScreen
import com.seq.acheronmobile.ui.vault.StorableFormScreen
import com.seq.acheronmobile.ui.vault.VaultListScreen
import com.seq.acheronmobile.ui.vault.VaultViewModel

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
                navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
            } else {
                val mfaVerifyViewModel: MfaVerifyViewModel = viewModel(
                    key = challengeToken,
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            MfaVerifyViewModel(challengeToken, pendingUsername, authRepository, tokenRepository) as T
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
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        MfaSettingsViewModel() as T
                }
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
                onLock = {
                    navController.navigate(Routes.MASTER_KEY) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onLogout = onLogout
            )
        }

        composable(
            route = Routes.STORABLE_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: return@composable
            val storable = vaultViewModel.uiState.value.storables.find { it.id == id }
            if (storable != null) {
                StorableDetailScreen(
                    storable = storable,
                    vaultViewModel = vaultViewModel,
                    onBack = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() }
                )
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
            val id = backStackEntry.arguments?.getString("id") ?: return@composable
            val storable = vaultViewModel.uiState.value.storables.find { it.id == id }
            if (storable != null) {
                StorableFormScreen(
                    storable = storable,
                    vaultViewModel = vaultViewModel,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
