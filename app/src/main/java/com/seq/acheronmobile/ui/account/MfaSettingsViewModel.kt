package com.seq.acheronmobile.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seq.acheronmobile.data.repository.MfaSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MfaSettingsUiState(
    val isLoading: Boolean       = true,
    val enabled: Boolean         = false,
    val confirmedAt: String?     = null,
    val errorMessage: String?    = null,
    val isBusy: Boolean          = false,
    // Enrolamiento en curso (setup → confirm → códigos de recuperación).
    val setupSecret: String?         = null,
    val setupProvisioningUri: String? = null,
    val confirmCode: String          = "",
    // Se muestran una única vez tras confirmar; el usuario debe reconocerlos.
    val recoveryCodes: List<String>?  = null,
    // Desactivación (exige reautenticación con un código vigente).
    val showDisableDialog: Boolean = false,
    val disableCode: String        = ""
)

/**
 * Configuración de MFA (TOTP) de la cuenta Ellysia. Vive en la sección
 * "Cuenta / Ajustes", no en el Vault: aquí se activa/desactiva el segundo
 * factor de acceso a la plataforma, no se gestionan secretos del vault.
 */
class MfaSettingsViewModel(
    private val repository: MfaSettingsRepository = MfaSettingsRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MfaSettingsUiState())
    val uiState: StateFlow<MfaSettingsUiState> = _uiState.asStateFlow()

    init {
        loadStatus()
    }

    fun loadStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.getStatus()) {
                is MfaSettingsRepository.MfaResult.Success -> {
                    _uiState.update {
                        it.copy(isLoading = false, enabled = result.data.enabled, confirmedAt = result.data.confirmedAt)
                    }
                }
                is MfaSettingsRepository.MfaResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                MfaSettingsRepository.MfaResult.NetworkError -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Sin conexión. Comprueba tu red.") }
                }
            }
        }
    }

    fun startSetup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            when (val result = repository.setupTotp()) {
                is MfaSettingsRepository.MfaResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            setupSecret = result.data.secret,
                            setupProvisioningUri = result.data.provisioningUri
                        )
                    }
                }
                is MfaSettingsRepository.MfaResult.Error -> {
                    _uiState.update { it.copy(isBusy = false, errorMessage = result.message) }
                }
                MfaSettingsRepository.MfaResult.NetworkError -> {
                    _uiState.update { it.copy(isBusy = false, errorMessage = "Sin conexión. Comprueba tu red.") }
                }
            }
        }
    }

    fun cancelSetup() {
        _uiState.update { it.copy(setupSecret = null, setupProvisioningUri = null, confirmCode = "") }
    }

    fun onConfirmCodeChange(value: String) {
        _uiState.update { it.copy(confirmCode = value, errorMessage = null) }
    }

    fun confirmSetup() {
        val code = _uiState.value.confirmCode
        if (code.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Introduce el código de 6 dígitos") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            when (val result = repository.confirmTotp(code.trim())) {
                is MfaSettingsRepository.MfaResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            enabled = true,
                            confirmCode = "",
                            setupSecret = null,
                            setupProvisioningUri = null,
                            recoveryCodes = result.data.recoveryCodes
                        )
                    }
                }
                is MfaSettingsRepository.MfaResult.Error -> {
                    _uiState.update { it.copy(isBusy = false, errorMessage = result.message) }
                }
                MfaSettingsRepository.MfaResult.NetworkError -> {
                    _uiState.update { it.copy(isBusy = false, errorMessage = "Sin conexión. Comprueba tu red.") }
                }
            }
        }
    }

    /** El usuario confirma que guardó los códigos de recuperación; solo se muestran una vez. */
    fun acknowledgeRecoveryCodes() {
        _uiState.update { it.copy(recoveryCodes = null) }
    }

    fun showDisableDialog() {
        _uiState.update { it.copy(showDisableDialog = true, disableCode = "", errorMessage = null) }
    }

    fun dismissDisableDialog() {
        _uiState.update { it.copy(showDisableDialog = false, disableCode = "") }
    }

    fun onDisableCodeChange(value: String) {
        _uiState.update { it.copy(disableCode = value, errorMessage = null) }
    }

    fun confirmDisable() {
        val code = _uiState.value.disableCode
        if (code.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Introduce un código vigente o de recuperación") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            when (val result = repository.disableTotp(code = code.trim())) {
                is MfaSettingsRepository.MfaResult.Success -> {
                    _uiState.update {
                        it.copy(isBusy = false, enabled = false, confirmedAt = null, showDisableDialog = false, disableCode = "")
                    }
                }
                is MfaSettingsRepository.MfaResult.Error -> {
                    _uiState.update { it.copy(isBusy = false, errorMessage = result.message) }
                }
                MfaSettingsRepository.MfaResult.NetworkError -> {
                    _uiState.update { it.copy(isBusy = false, errorMessage = "Sin conexión. Comprueba tu red.") }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
