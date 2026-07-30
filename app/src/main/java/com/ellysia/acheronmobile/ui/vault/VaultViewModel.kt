package com.ellysia.acheronmobile.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ellysia.acheronmobile.data.ApiResult
import com.ellysia.acheronmobile.data.model.BulkUpdateRequest
import com.ellysia.acheronmobile.data.model.StorableResponse
import com.ellysia.acheronmobile.data.vault.StorableUi
import com.ellysia.acheronmobile.data.vault.VaultCryptoService
import com.ellysia.acheronmobile.data.vault.VaultState
import com.ellysia.acheronmobile.di.VaultServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class VaultUiState(
    val storables: List<StorableUi> = emptyList(),
    val isLoading: Boolean = false,
    val syncing: Boolean = false,
    val errorMessage: String? = null,
    val locked: Boolean = false
)

class VaultViewModel : ViewModel() {

    private val crypto = VaultServiceLocator.cryptoService
    private val remote = VaultServiceLocator.remoteDataSource

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            crypto.state.collectLatest { state ->
                when (state) {
                    is VaultState.Unlocked -> {
                        _uiState.update {
                            it.copy(storables = state.storables, locked = false)
                        }
                    }
                    is VaultState.Locked -> {
                        _uiState.update { it.copy(locked = true, storables = emptyList()) }
                    }
                    is VaultState.Error -> {
                        _uiState.update { it.copy(errorMessage = state.message) }
                    }
                    VaultState.NoVault -> {}
                }
            }
        }
    }

    /**
     * Recarga la bóveda desde el servidor y reemplaza el estado local.
     *
     * Sustituye al antiguo `syncToRemote()`, que empujaba el vault local entero
     * contra `POST /acheron/vault` —un DELETE+reinsert de todos los storables en
     * el backend— y así borraba en silencio lo que se hubiera editado desde otro
     * cliente. Ese push no tenía caso de uso legítimo: cada alta/edición/baja ya
     * se envía de forma granular en el momento en que ocurre, así que nunca hay
     * estado local pendiente de subir. Lo que falta en el flujo es lo contrario:
     * traerse lo que escribieron los demás.
     *
     * No pide la contraseña maestra: la sesión abierta ya puede descifrar.
     */
    fun refreshFromRemote() {
        viewModelScope.launch { reloadFromRemote() }
    }

    /**
     * Trae el vault del servidor y lo vuelve a abrir en caliente.
     *
     * @return `true` si el estado local quedó sincronizado con el servidor.
     */
    private suspend fun reloadFromRemote(): Boolean {
        _uiState.update { it.copy(syncing = true, errorMessage = null) }
        return when (val result = remote.fetchVault()) {
            is ApiResult.Success -> {
                val state = crypto.reloadFromJson(
                    VaultServiceLocator.username, result.data.toString()
                )
                if (state is VaultState.Unlocked) {
                    _uiState.update { it.copy(syncing = false) }
                    true
                } else {
                    // La contraseña maestra ya no abre este vault: se rotó desde
                    // otro dispositivo. El collector de crypto.state marcará
                    // `locked` y la UI llevará al desbloqueo.
                    _uiState.update {
                        it.copy(
                            syncing = false,
                            errorMessage = "Tu contraseña maestra cambió en otro dispositivo. " +
                                "Vuelve a desbloquear la bóveda.",
                        )
                    }
                    false
                }
            }
            is ApiResult.Error -> {
                _uiState.update { it.copy(syncing = false, errorMessage = result.message) }
                false
            }
            is ApiResult.NetworkError -> {
                _uiState.update { it.copy(syncing = false, errorMessage = "Sin conexión") }
                false
            }
        }
    }

    /**
     * Ejecuta una escritura y, si el servidor la rechaza con 409, recarga el
     * vault y la reintenta UNA vez sobre el estado fresco.
     *
     * El reintento reenvía el MISMO cuerpo cifrado: la `vaultKey` no cambia con
     * lo que escriban otros clientes, así que el ciphertext sigue siendo válido.
     * Al terminar se vuelve a recargar, porque la recarga intermedia deshizo el
     * cambio en el vault en memoria (que sí se aplica localmente antes de
     * enviarlo) y esta segunda lectura deja local y servidor diciendo lo mismo,
     * incluido lo que escribió el otro dispositivo.
     *
     * Se reintenta ante cualquier 409 sin mirar el motivo: en `DELETE` y `PATCH`
     * el único 409 posible es la revisión obsoleta, y en `POST` el otro caso
     * —internalId duplicado— exigiría una colisión de SHA-256 sobre ciphertext
     * con IV aleatorio. Si de todos modos ocurriera, el reintento vuelve a
     * fallar y el error llega al usuario igual.
     */
    private suspend fun <T> withRevisionRetry(block: suspend () -> ApiResult<T>): ApiResult<T> {
        val first = block()
        if (first !is ApiResult.Error || first.code != 409) return first
        if (!reloadFromRemote()) return first

        val retry = block()
        if (retry is ApiResult.Success) reloadFromRemote()
        return retry
    }

    /**
     * Mapea el resultado de un alta/baja granular (`POST`/`DELETE /storables`)
     * a un booleano, dejando el motivo en `errorMessage` si falla.
     */
    private fun pushStorableResult(result: ApiResult<StorableResponse>): Boolean {
        return when (result) {
            is ApiResult.Success -> {
                _uiState.update { it.copy(isLoading = false) }
                true
            }
            is ApiResult.Error -> {
                _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                false
            }
            is ApiResult.NetworkError -> {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Sin conexión") }
                false
            }
        }
    }

    /**
     * Alta genérica de un storable de cualquier [kind] a partir de un mapa
     * `campo -> valor` (los nombres de campo provienen del [StorableTypeSpec]).
     */
    suspend fun addStorable(kind: String, title: String, fields: Map<String, String>): Boolean {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        return try {
            val request = crypto.addStorable(kind, title, fields)
            pushStorableResult(withRevisionRetry { remote.addStorable(request) })
        } catch (e: Exception) {
            _uiState.update {
                it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Error")
            }
            false
        }
    }

    suspend fun deleteStorable(internalId: String): Boolean {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        return try {
            if (!crypto.removeStorable(internalId)) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Elemento no encontrado")
                }
                return false
            }
            pushStorableResult(withRevisionRetry { remote.deleteStorable(internalId) })
        } catch (e: Exception) {
            _uiState.update {
                it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Error")
            }
            false
        }
    }

    /**
     * Actualización genérica de un storable: [fields] contiene `campo -> valor`
     * para los campos a cambiar (valor `null` = sin cambios).
     */
    suspend fun updateStorable(id: String, title: String?, fields: Map<String, String?>): Boolean {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        return try {
            pushStorableUpdate(id, crypto.updateStorable(id, title, fields))
        } catch (e: Exception) {
            _uiState.update {
                it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Error")
            }
            false
        }
    }

    /**
     * Envia un cambio puntual de un storable via `PATCH /storables` (solo los
     * campos modificados, ya cifrados) en lugar de reescribir todo el vault.
     *
     * @param changes mapa de campos cifrados devuelto por el crypto service;
     *                `null` si el storable no existe, vacio si no hubo cambios.
     */
    private suspend fun pushStorableUpdate(id: String, changes: Map<String, String>?): Boolean {
        if (changes == null) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Elemento no encontrado") }
            return false
        }
        if (changes.isEmpty()) {
            _uiState.update { it.copy(isLoading = false) }
            return true
        }
        val result = withRevisionRetry {
            remote.bulkUpdate(listOf(BulkUpdateRequest(id, changes)))
        }
        return when (result) {
            is ApiResult.Success -> {
                val status = result.data.results.firstOrNull()
                    ?.get("status")?.jsonPrimitive?.contentOrNull
                if (status == "updated") {
                    _uiState.update { it.copy(isLoading = false) }
                    true
                } else {
                    _uiState.update {
                        it.copy(isLoading = false,
                            errorMessage = "No se pudo actualizar (${status ?: "desconocido"})")
                    }
                    false
                }
            }
            is ApiResult.Error -> {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
                false
            }
            is ApiResult.NetworkError -> {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Sin conexión")
                }
                false
            }
        }
    }

    /**
     * Rota la contraseña maestra: re-cifra la clave de la bóveda con la nueva
     * contraseña (los storables no se tocan) y refresca los metadatos en el
     * servidor vía `PATCH /vault`. Tras intentarlo, bloquea la bóveda para que
     * el usuario la vuelva a abrir con la contraseña correspondiente (la nueva
     * si tuvo éxito; la antigua si falló la persistencia).
     *
     * @return `true` si el cambio se aplicó y persistió correctamente.
     */
    suspend fun changeMasterPassword(oldPassword: String, newPassword: String): Boolean {
        _uiState.update { it.copy(syncing = true, errorMessage = null) }

        val metadata = try {
            crypto.changeMasterPassword(oldPassword, newPassword)
        } catch (_: com.ellysia.acheron.exceptions.WrongPasswordException) {
            // Se lanza antes de mutar el vault: el usuario puede reintentar.
            _uiState.update { it.copy(syncing = false, errorMessage = "Contraseña actual incorrecta") }
            return false
        } catch (e: Exception) {
            _uiState.update {
                it.copy(syncing = false, errorMessage = e.localizedMessage ?: "Error al cambiar la contraseña")
            }
            return false
        }

        return when (val result = remote.changeVaultPassword(metadata)) {
            is ApiResult.Success -> {
                _uiState.update { it.copy(syncing = false) }
                // El secreto biométrico guardaba la contraseña antigua: descartarlo
                // para que se vuelva a enrolar al desbloquear con la nueva.
                VaultServiceLocator.biometricStore.clear()
                crypto.lock() // re-desbloqueo con la nueva contraseña
                true
            }
            is ApiResult.Error -> {
                // El vault en memoria ya está rekeyado pero el server no: bloquea
                // para descartar el estado divergente.
                _uiState.update { it.copy(syncing = false, errorMessage = result.message) }
                crypto.lock()
                false
            }
            is ApiResult.NetworkError -> {
                _uiState.update { it.copy(syncing = false, errorMessage = "Sin conexión") }
                crypto.lock()
                false
            }
        }
    }

    fun lockVault() {
        crypto.lock()
        remote.forgetRevision()
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
