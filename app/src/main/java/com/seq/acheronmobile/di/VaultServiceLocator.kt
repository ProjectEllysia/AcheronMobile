package com.seq.acheronmobile.di

import com.seq.acheronmobile.data.repository.VaultRemoteDataSource
import com.seq.acheronmobile.data.security.BiometricMasterPasswordStore
import com.seq.acheronmobile.data.vault.VaultCryptoService

object VaultServiceLocator {
    lateinit var cryptoService: VaultCryptoService
    lateinit var remoteDataSource: VaultRemoteDataSource
    lateinit var biometricStore: BiometricMasterPasswordStore
    var username: String = ""
}