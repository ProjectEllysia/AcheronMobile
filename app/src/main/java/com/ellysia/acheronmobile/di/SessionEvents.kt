package com.ellysia.acheronmobile.di

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Canal global de eventos de fin de sesión.
 *
 * Cualquier capa (red, refresh de token, desbloqueo del vault) puede señalar que
 * la sesión terminó por un motivo concreto; la UI (NavGraph) lo observa y enruta
 * a la pantalla de login mostrando el mensaje adecuado.
 *
 * Motivos:
 *  - "password_changed": la contraseña de acceso cambió en otro dispositivo.
 */
object SessionEvents {
    private val _endReason = MutableStateFlow<String?>(null)
    val endReason: StateFlow<String?> = _endReason.asStateFlow()

    fun signalEnd(reason: String) {
        _endReason.value = reason
    }

    fun consume() {
        _endReason.value = null
    }
}
