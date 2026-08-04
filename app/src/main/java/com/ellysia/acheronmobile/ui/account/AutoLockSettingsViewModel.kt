package com.ellysia.acheronmobile.ui.account

import androidx.lifecycle.ViewModel
import com.ellysia.acheronmobile.data.security.AutoLockPreferences
import com.ellysia.acheronmobile.di.VaultServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AutoLockUiState(val timeoutMinutes: Int = AutoLockPreferences.DEFAULT_TIMEOUT_MINUTES)

/**
 * Configura cuánto tiempo en segundo plano tolera la bóveda antes de
 * autobloquearse (ver F4 en docs/code-review.md, cara de producto de S2).
 */
class AutoLockSettingsViewModel : ViewModel() {

    private val preferences = VaultServiceLocator.autoLockPreferences

    private val _uiState = MutableStateFlow(AutoLockUiState(timeoutMinutes = preferences.timeoutMinutes))
    val uiState: StateFlow<AutoLockUiState> = _uiState.asStateFlow()

    fun setTimeoutMinutes(minutes: Int) {
        preferences.timeoutMinutes = minutes
        _uiState.update { it.copy(timeoutMinutes = minutes) }
    }
}
