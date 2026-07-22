package com.seq.acheronmobile.ui.mfa

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

data class MfaVerifyUiState(
    val code: String          = "",
    val useRecovery: Boolean  = false,
    val isLoading: Boolean    = false,
    val errorMessage: String? = null,
    val verifySuccess: Boolean = false
)

/**
 * Segundo paso del login cuando la cuenta tiene MFA (TOTP) activo.
 * Recibe el challengeToken emitido por el paso 1 (POST /oauth/token) y lo
 * canjea, junto con un código TOTP o de recuperación, en POST /oauth/mfa/verify.
 */
class MfaVerifyViewModel(
    private val challengeToken: String,
    private val username: String,
    private val authRepository: AuthRepository,
    private val tokenRepository: TokenRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MfaVerifyUiState())
    val uiState: StateFlow<MfaVerifyUiState> = _uiState.asStateFlow()

    fun onCodeChange(value: String) {
        _uiState.update { it.copy(code = value, errorMessage = null) }
    }

    fun onToggleRecovery() {
        _uiState.update { it.copy(useRecovery = !it.useRecovery, code = "", errorMessage = null) }
    }

    fun onVerifyClick() {
        val state = _uiState.value
        if (state.code.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = if (state.useRecovery) "Introduce tu código de recuperación" else "Introduce el código de 6 dígitos")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = if (state.useRecovery) {
                authRepository.verifyMfa(challengeToken, recoveryCode = state.code.trim())
            } else {
                authRepository.verifyMfa(challengeToken, code = state.code.trim())
            }

            when (result) {
                is AuthRepository.AuthResult.Success -> {
                    VaultServiceLocator.username = username
                    tokenRepository.saveUsername(username)
                    _uiState.update { it.copy(isLoading = false, verifySuccess = true) }
                }
                is AuthRepository.AuthResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                AuthRepository.AuthResult.NetworkError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Sin conexión. Comprueba tu red.")
                    }
                }
                // El reto ya fue consumido o expiró: no hay forma de recuperarlo,
                // hay que volver a iniciar sesión desde cero.
                is AuthRepository.AuthResult.MfaRequired, AuthRepository.AuthResult.SessionExpired -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "La verificación caducó. Inicia sesión de nuevo."
                        )
                    }
                }
            }
        }
    }

    fun onNavigatedToVault() {
        _uiState.update { it.copy(verifySuccess = false) }
    }
}
