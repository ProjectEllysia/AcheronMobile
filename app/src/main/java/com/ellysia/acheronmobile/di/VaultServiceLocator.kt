package com.ellysia.acheronmobile.di

import android.content.Context
import com.ellysia.acheronmobile.data.repository.VaultRemoteDataSource
import com.ellysia.acheronmobile.data.security.AutoLockController
import com.ellysia.acheronmobile.data.security.AutoLockPreferences
import com.ellysia.acheronmobile.data.security.BiometricMasterPasswordStore
import com.ellysia.acheronmobile.data.vault.VaultCryptoService

/**
 * Localizador de servicios del vault, compartido por los ViewModels vía
 * inyección de campo (ver D8 en docs/code-review.md para la alternativa
 * por constructor, aún pendiente).
 *
 * [initialize] es idempotente a propósito: `MainActivity.onCreate` se
 * invoca en cada rotación de pantalla (no hay `configChanges` en el
 * manifiesto), pero los ViewModels retenidos ya capturaron las instancias
 * antiguas en su constructor. Recrearlas aquí en cada rotación dejaba
 * conviviendo dos [VaultCryptoService]: `logout()` bloqueaba la instancia
 * nueva mientras la antigua —viva, referenciada por el ViewModel
 * retenido— conservaba la bóveda descifrada en memoria (ver B1 en el
 * informe de revisión).
 */
object VaultServiceLocator {
    lateinit var cryptoService: VaultCryptoService
    lateinit var remoteDataSource: VaultRemoteDataSource
    lateinit var biometricStore: BiometricMasterPasswordStore
    lateinit var autoLockPreferences: AutoLockPreferences
    lateinit var autoLockController: AutoLockController
    var username: String = ""

    private var initialized = false

    /** Crea los servicios una única vez por proceso; no-op en rotaciones posteriores. */
    fun initialize(applicationContext: Context) {
        if (initialized) return
        cryptoService = VaultCryptoService()
        remoteDataSource = VaultRemoteDataSource()
        biometricStore = BiometricMasterPasswordStore(applicationContext)
        autoLockPreferences = AutoLockPreferences(applicationContext)
        autoLockController = AutoLockController(autoLockPreferences)
        initialized = true
    }
}