package com.ellysia.acheronmobile.ui.account

import androidx.lifecycle.ViewModel
import com.ellysia.acheronmobile.di.VaultServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class BiometricSettingsUiState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val showEnableDialog: Boolean = false,
    val password: String = "",
)

/**
 * Gestión del desbloqueo por huella desde "Cuenta y ajustes". Antes solo se
 * podía activar en el diálogo posterior a un desbloqueo manual; si el usuario
 * lo rechazaba una vez, no había forma de volver a activarlo salvo cerrar
 * sesión (ver F6 en docs/code-review.md).
 *
 * Activar exige reintroducir la clave maestra: [com.ellysia.acheronmobile.data.security.BiometricMasterPasswordStore]
 * necesita el texto plano para cifrarlo, y aquí (a diferencia del desbloqueo
 * inicial) no queda ninguna copia en memoria por diseño.
 */
class BiometricSettingsViewModel : ViewModel() {

    private val crypto = VaultServiceLocator.cryptoService
    private val biometricStore = VaultServiceLocator.biometricStore

    private val _uiState = MutableStateFlow(BiometricSettingsUiState(enabled = biometricStore.isEnabled()))
    val uiState: StateFlow<BiometricSettingsUiState> = _uiState.asStateFlow()

    fun setAvailable(available: Boolean) {
        _uiState.update { it.copy(available = available) }
    }

    fun showEnableDialog() {
        _uiState.update { it.copy(showEnableDialog = true, password = "", errorMessage = null) }
    }

    fun dismissEnableDialog() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(showEnableDialog = false, password = "") }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    /** Verifica la contraseña introducida contra el vault abierto (sin mutar nada). */
    fun verifyPassword(): Boolean {
        val ok = crypto.verifyMasterPassword(_uiState.value.password)
        if (!ok) {
            _uiState.update { it.copy(errorMessage = "Contraseña maestra incorrecta") }
        }
        return ok
    }

    fun setBusy(busy: Boolean) {
        _uiState.update { it.copy(isBusy = busy) }
    }

    fun onEnrolled() {
        _uiState.update {
            it.copy(showEnableDialog = false, password = "", isBusy = false, enabled = true, errorMessage = null)
        }
    }

    fun onEnrollFailed(message: String) {
        _uiState.update { it.copy(isBusy = false, errorMessage = message) }
    }

    /** La contraseña ya verificada, para cifrarla tras la autenticación biométrica. */
    fun masterPasswordForEnroll(): String = _uiState.value.password

    fun disable() {
        biometricStore.clear()
        _uiState.update { it.copy(enabled = false, errorMessage = null) }
    }
}
