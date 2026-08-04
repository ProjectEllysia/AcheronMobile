package com.ellysia.acheronmobile

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.rememberNavController
import com.ellysia.acheronmobile.data.network.NetworkModule
import com.ellysia.acheronmobile.data.repository.AuthRepository
import com.ellysia.acheronmobile.data.repository.TokenRepository
import com.ellysia.acheronmobile.di.VaultServiceLocator
import com.ellysia.acheronmobile.navigation.AcheronNavGraph
import com.ellysia.acheronmobile.ui.login.LoginViewModel
import com.ellysia.acheronmobile.ui.theme.AcheronMobileTheme
import com.ellysia.acheronmobile.ui.vault.VaultViewModel

class MainActivity : FragmentActivity() {

    // Instancias compartidas: LoginViewModel, MfaVerifyViewModel y
    // MfaSettingsViewModel operan todas sobre la misma sesión, así que
    // reutilizan el mismo TokenRepository/AuthRepository en vez de crear
    // repositorios independientes por pantalla.
    private val tokenRepository: TokenRepository by lazy { TokenRepository(applicationContext) }
    private val authRepository: AuthRepository by lazy { AuthRepository(tokenRepository) }

    // DSL de lifecycle-viewmodel en vez de un ViewModelProvider.Factory anónimo
    // con @Suppress("UNCHECKED_CAST") (ver D10 en docs/code-review.md).
    private val loginViewModel: LoginViewModel by lazy {
        ViewModelProvider(
            this,
            viewModelFactory { initializer { LoginViewModel(authRepository, tokenRepository) } }
        )[LoginViewModel::class.java]
    }

    private val vaultViewModel: VaultViewModel by lazy {
        ViewModelProvider(this)[VaultViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Instala la pantalla de inicio de marca (rio purpura sobre el abismo)
        // antes de super.onCreate; sustituye al splash automatico del sistema.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // La bóveda descifrada no debe aparecer en capturas de pantalla ni en
        // la miniatura del selector de apps recientes (ver S1 en docs/code-review.md).
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        NetworkModule.initialize(tokenRepository)

        // Init vault services una única vez por proceso: onCreate se
        // reinvoca en cada rotación (no hay configChanges) pero los
        // ViewModels retenidos ya capturaron las instancias antiguas, así
        // que recrearlas aquí dejaba conviviendo dos VaultCryptoService
        // (ver B1 en docs/code-review.md).
        VaultServiceLocator.initialize(applicationContext)
        // Restaura el username de la sesion activa: necesario para validar el
        // checker del vault si se arranca directamente en MASTER_KEY (ver #3).
        // Solo la primera vez: en rotaciones posteriores no debe pisar un
        // logout o un cambio de usuario ya reflejado en el locator.
        if (VaultServiceLocator.username.isEmpty()) {
            VaultServiceLocator.username = tokenRepository.getUsername() ?: ""
        }

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

    // Autobloqueo (ver S2/F4 en docs/code-review.md): antes la bóveda seguía
    // descifrada en memoria indefinidamente al ir a segundo plano. `onStop`
    // marca el instante (solo se dispara si la Activity deja de ser visible
    // del todo, no en solapamientos transitorios); `onResume` decide si ha
    // pasado más tiempo del configurado y, si es así, bloquea — la UI ya
    // reacciona sola a `crypto.state` y navega a la pantalla de bloqueo.
    override fun onStop() {
        super.onStop()
        VaultServiceLocator.autoLockController.onBackground()
    }

    override fun onResume() {
        super.onResume()
        if (VaultServiceLocator.autoLockController.onForeground()) {
            VaultServiceLocator.cryptoService.lock()
        }
    }
}
