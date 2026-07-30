package com.ellysia.acheronmobile.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ellysia.acheronmobile.data.ApiResult
import com.ellysia.acheronmobile.data.vault.VaultState
import com.ellysia.acheronmobile.di.VaultServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class MasterKeyUiState(
    val masterPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val errorMessage: String? = null,
    val unlocked: Boolean = false,
    // null = aun comprobando si existe boveda; true = existe (desbloquear);
    // false = no existe (crear). probeError != null = la comprobacion fallo.
    val vaultExists: Boolean? = null,
    val probeError: String? = null,
    // ── Biometría ──
    val biometricAvailable: Boolean = false,
    val biometricEnrolled: Boolean = false,
    // Tras un desbloqueo MANUAL correcto, ofrecer activar la huella antes de entrar.
    val offerBiometricEnroll: Boolean = false,
)

class MasterKeyViewModel : ViewModel() {

    private val crypto = VaultServiceLocator.cryptoService
    private val remote = VaultServiceLocator.remoteDataSource
    private val biometric = VaultServiceLocator.biometricStore

    // Blob cifrado de la boveda obtenido durante la comprobacion; se reutiliza
    // al desbloquear para no volver a pedirlo al servidor.
    private var cachedVaultJson: JsonObject? = null

    private val _uiState = MutableStateFlow(
        MasterKeyUiState(biometricEnrolled = biometric.isEnabled())
    )
    val uiState: StateFlow<MasterKeyUiState> = _uiState.asStateFlow()

    init {
        checkVault()
    }

    /** La UI informa si el dispositivo tiene biometría utilizable. */
    fun setBiometricAvailable(available: Boolean) {
        _uiState.update { it.copy(biometricAvailable = available) }
    }

    /**
     * Determina por adelantado si el usuario tiene una boveda, ANTES de pedir la
     * clave maestra. Asi la UI puede llevar directamente a desbloquear (si
     * existe) o a la bienvenida/creacion (si no), en lugar de descubrirlo solo
     * tras enviar la contraseña.
     */
    fun checkVault() {
        _uiState.update { it.copy(vaultExists = null, probeError = null, errorMessage = null) }
        viewModelScope.launch {
            when (val result = remote.fetchVault()) {
                is ApiResult.Success -> {
                    cachedVaultJson = result.data
                    _uiState.update { it.copy(vaultExists = true) }
                }
                is ApiResult.Error -> {
                    if (result.code == 404) {
                        cachedVaultJson = null
                        _uiState.update { it.copy(vaultExists = false) }
                    } else {
                        _uiState.update { it.copy(probeError = result.message) }
                    }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update { it.copy(probeError = "Sin conexión. Comprueba tu red.") }
                }
            }
        }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(masterPassword = value, errorMessage = null) }
    }

    fun onConfirmChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }
    }

    fun onUnlockClick() {
        val password = _uiState.value.masterPassword
        if (password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Introduce la clave maestra") }
            return
        }
        doUnlock(password, fromBiometric = false)
    }

    /** Desbloqueo con la contraseña maestra recuperada tras autenticación biométrica. */
    fun onBiometricUnlock(password: String) {
        doUnlock(password, fromBiometric = true)
    }

    private fun doUnlock(password: String, fromBiometric: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isCreating = false, errorMessage = null) }

            // Reutiliza el blob cacheado; si falta (raro), lo vuelve a pedir.
            val vault = cachedVaultJson ?: when (val r = remote.fetchVault()) {
                is ApiResult.Success -> r.data.also { cachedVaultJson = it }
                is ApiResult.Error -> {
                    if (r.code == 404) {
                        _uiState.update { it.copy(isLoading = false, vaultExists = false) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, errorMessage = r.message) }
                    }
                    return@launch
                }
                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Sin conexión. Comprueba tu red.")
                    }
                    return@launch
                }
            }

            // El resultado ya lo devuelve unlockFromJson: no hace falta releer
            // crypto.state.value como una segunda fuente de la misma verdad
            // (ver B9 en docs/code-review.md).
            when (val state = crypto.unlockFromJson(VaultServiceLocator.username, vault.toString(), password)) {
                is VaultState.Unlocked -> {
                    rememberMetadataVersion(vault)
                    // Tras un desbloqueo manual, ofrecer activar la huella (si procede)
                    // ANTES de navegar; si no, entrar directamente.
                    val offer = _uiState.value.biometricAvailable &&
                        !fromBiometric && !biometric.isEnabled()
                    _uiState.update {
                        it.copy(isLoading = false, offerBiometricEnroll = offer, unlocked = !offer)
                    }
                }
                is VaultState.Locked -> {
                    if (fromBiometric) {
                        // La contraseña maestra guardada ya no valida el checker:
                        // cambió en otro dispositivo. Descartar el secreto biométrico.
                        biometric.clear()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                biometricEnrolled = false,
                                masterPassword = "",
                                errorMessage = "Tu contraseña maestra ha cambiado. Introdúcela de nuevo.",
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = "Clave maestra incorrecta")
                        }
                    }
                }
                is VaultState.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Error: ${state.message}")
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Error al abrir la bóveda")
                    }
                }
            }
        }
    }

    /**
     * La UI ha cifrado y guardado la contraseña maestra tras autenticar la huella.
     * Marca la huella como activa y entra a la bóveda.
     */
    fun onBiometricEnrolled() {
        _uiState.update {
            it.copy(biometricEnrolled = true, offerBiometricEnroll = false, unlocked = true)
        }
    }

    /** El usuario rechazó (o falló) activar la huella: entrar igualmente. */
    fun skipBiometricEnroll() {
        _uiState.update { it.copy(offerBiometricEnroll = false, unlocked = true) }
    }

    /**
     * La clave biométrica quedó invalidada (p.ej. se registró una nueva huella).
     * El secreto ya se borró; pedir la clave maestra manualmente.
     */
    fun onBiometricInvalidated() {
        _uiState.update {
            it.copy(
                biometricEnrolled = false,
                errorMessage = "La biometría cambió. Introduce tu clave maestra y vuelve a activarla.",
            )
        }
    }

    /** La contraseña maestra en claro que la UI necesita para enrolar la huella. */
    fun masterPasswordForEnroll(): String = _uiState.value.masterPassword

    private fun rememberMetadataVersion(vault: JsonObject) {
        val mv = vault["metadataVersion"]?.jsonPrimitive?.intOrNull
        if (mv != null) biometric.setKnownMetadataVersion(mv)
    }

    fun onCreateVaultClick() {
        val state = _uiState.value
        val password = state.masterPassword
        if (password.length < 8) {
            _uiState.update { it.copy(errorMessage = "La clave debe tener al menos 8 caracteres") }
            return
        }
        if (password != state.confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Las claves no coinciden") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isCreating = true, errorMessage = null) }
            try {
                val vaultJson = crypto.createVault(VaultServiceLocator.username, password)
                val json = Json.parseToJsonElement(vaultJson).jsonObject

                when (val result = remote.pushVault(json)) {
                    is ApiResult.Success -> {
                        cachedVaultJson = json
                        rememberMetadataVersion(json)
                        val offer = _uiState.value.biometricAvailable && !biometric.isEnabled()
                        _uiState.update {
                            it.copy(isLoading = false, offerBiometricEnroll = offer, unlocked = !offer)
                        }
                    }
                    is ApiResult.Error -> {
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = "Error al guardar: ${result.message}")
                        }
                    }
                    is ApiResult.NetworkError -> {
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = "Sin conexión. Comprueba tu red.")
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Error al crear la bóveda: ${e.localizedMessage}")
                }
            }
        }
    }

    fun onNavigatedToVault() {
        _uiState.update { it.copy(unlocked = false) }
    }
}
