package com.seq.acheronmobile

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import com.seq.acheronmobile.data.network.NetworkModule
import com.seq.acheronmobile.data.repository.AuthRepository
import com.seq.acheronmobile.data.repository.TokenRepository
import com.seq.acheronmobile.data.repository.VaultRemoteDataSource
import com.seq.acheronmobile.data.security.BiometricMasterPasswordStore
import com.seq.acheronmobile.data.vault.VaultCryptoService
import com.seq.acheronmobile.di.VaultServiceLocator
import com.seq.acheronmobile.navigation.AcheronNavGraph
import com.seq.acheronmobile.ui.login.LoginViewModel
import com.seq.acheronmobile.ui.theme.AcheronMobileTheme
import com.seq.acheronmobile.ui.vault.VaultViewModel

class MainActivity : FragmentActivity() {

    // Instancias compartidas: LoginViewModel, MfaVerifyViewModel y
    // MfaSettingsViewModel operan todas sobre la misma sesión, así que
    // reutilizan el mismo TokenRepository/AuthRepository en vez de crear
    // repositorios independientes por pantalla.
    private val tokenRepository: TokenRepository by lazy { TokenRepository(applicationContext) }
    private val authRepository: AuthRepository by lazy { AuthRepository(tokenRepository) }

    private val loginViewModel: LoginViewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LoginViewModel(authRepository, tokenRepository) as T
        })[LoginViewModel::class.java]
    }

    private val vaultViewModel: VaultViewModel by lazy {
        ViewModelProvider(this)[VaultViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Instala la pantalla de inicio de marca (rio purpura sobre el abismo)
        // antes de super.onCreate; sustituye al splash automatico del sistema.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        NetworkModule.initialize(tokenRepository)

        // Init vault services (recreated on process death)
        VaultServiceLocator.cryptoService = VaultCryptoService()
        VaultServiceLocator.remoteDataSource = VaultRemoteDataSource()
        VaultServiceLocator.biometricStore = BiometricMasterPasswordStore(applicationContext)
        // Restaura el username de la sesion activa: necesario para validar el
        // checker del vault si se arranca directamente en MASTER_KEY (ver #3).
        VaultServiceLocator.username = tokenRepository.getUsername() ?: ""

        // La identidad de Acheron es siempre oscura, asi que fijamos iconos de
        // barra claros (estilo "dark") para que no queden invisibles aunque el
        // sistema este en modo claro.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            AcheronMobileTheme(dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AcheronNavGraph(
                        navController   = navController,
                        loginViewModel  = loginViewModel,
                        vaultViewModel  = vaultViewModel,
                        authRepository  = authRepository,
                        tokenRepository = tokenRepository
                    )
                }
            }
        }
    }
}
