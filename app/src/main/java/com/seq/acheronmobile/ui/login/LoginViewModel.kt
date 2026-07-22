package com.seq.acheronmobile.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seq.acheronmobile.data.repository.AuthRepository
import com.seq.acheronmobile.data.repository.TokenRepository
import com.seq.acheronmobile.di.VaultServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String      = "",
    val password: String      = "",
    val isLoading: Boolean    = false,
    val errorMessage: String? = null,
    val loginSuccess: Boolean = false,
    // Presente cuando la cuenta tiene MFA activo: la pantalla debe navegar al
    // segundo paso (verificación TOTP) llevando este challengeToken consigo.
    val mfaChallengeToken: String? = null
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val tokenRepository: TokenRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    val hasActiveSession: Boolean
        get() = tokenRepository.hasValidSession()


    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(username = value, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun onLoginClick() {
        val state = _uiState.value
        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Introduce usuario y contraseña") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            when (val result = authRepository.login(state.username, state.password)) {
                is AuthRepository.AuthResult.Success -> {
                    VaultServiceLocator.username = state.username
                    tokenRepository.saveUsername(state.username)
                    _uiState.update { it.copy(isLoading = false, loginSuccess = true) }
                }
                is AuthRepository.AuthResult.MfaRequired -> {
                    _uiState.update {
                        it.copy(isLoading = false, mfaChallengeToken = result.challengeToken)
                    }
                }
                is AuthRepository.AuthResult.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                }
                AuthRepository.AuthResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Sin conexión. Comprueba tu red."
                        )
                    }
                }
                AuthRepository.AuthResult.SessionExpired -> {   // ← AÑADIR ESTO
                    _uiState.update {
                        it.copy(
                            isLoading    = false,
                            errorMessage = "Tu sesión ha expirado. Inicia sesión de nuevo."
                        )
                    }
                }
            }
        }
    }

    fun onNavigatedToVault() {
        _uiState.update { it.copy(loginSuccess = false) }
    }

    /** Se llama tras navegar a la pantalla de verificación MFA, para no re-disparar la navegación. */
    fun onNavigatedToMfaVerify() {
        _uiState.update { it.copy(mfaChallengeToken = null) }
    }

    /**
     * Cierra la sesión de SeQ: revoca los tokens locales, bloquea la bóveda en
     * memoria y descarta el secreto biométrico. A diferencia de "bloquear", esto
     * obliga a iniciar sesión de nuevo (no solo a reintroducir la clave maestra).
     */
    fun logout() {
        tokenRepository.clearTokens()
        VaultServiceLocator.cryptoService.lock()
        VaultServiceLocator.biometricStore.clear()
        VaultServiceLocator.username = ""
        _uiState.value = LoginUiState()
    }

    /**
     * Muestra un mensaje en el login cuando la sesión terminó por un motivo
     * concreto (p.ej. la contraseña de acceso cambió en otro dispositivo).
     * Debe llamarse DESPUÉS de [logout] para que el mensaje no se borre.
     */
    fun notifySessionEnded(reason: String) {
        val msg = when (reason) {
            "password_changed" -> "Tu contraseña ha cambiado. Inicia sesión de nuevo."
            else -> "Tu sesión ha expirado. Inicia sesión de nuevo."
        }
        _uiState.update { it.copy(errorMessage = msg) }
    }
}