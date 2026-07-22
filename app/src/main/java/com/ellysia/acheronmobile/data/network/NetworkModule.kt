package com.ellysia.acheronmobile.data.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.ellysia.acheronmobile.BuildConfig
import com.ellysia.acheronmobile.data.repository.TokenRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Singleton que provee la instancia de Retrofit.
 * En un proyecto con Hilt esto sería un @Module; aquí usamos object
 * para mantener el ejemplo sin DI framework.
 */
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true   // la API puede añadir campos sin romper el cliente
        isLenient = true
        encodeDefaults = true
    }
    private lateinit var tokenAuthenticator: TokenAuthenticator
    lateinit var apiService: EllysiaApiService
        private set

    private var initialized = false

    /**
     * Idempotente a propósito: `MainActivity.onCreate` se invoca en cada
     * rotación de pantalla y llamar aquí de nuevo reconstruiría el
     * `OkHttpClient` (y su pool de conexiones) en cada una (ver B1 en
     * docs/code-review.md).
     */
    fun initialize(tokenRepository: TokenRepository) {
        if (initialized) return
        // 1. Crear el Authenticator vacío (aún sin apiService)
        tokenAuthenticator = TokenAuthenticator(tokenRepository)

        // 2. Construir OkHttpClient con ese Authenticator
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .authenticator(tokenAuthenticator)
            .addInterceptor { chain ->
                val token = tokenRepository.getAccessToken()
                val request = chain.request().newBuilder().apply {
                    if (token != null) header("Authorization", "Bearer $token")
                    header("Accept", "application/json")
                }.build()
                chain.proceed(request)
            }
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    })
                }
            }
            .build()

        // 3. Construir Retrofit y obtener el servicio
        apiService = Retrofit.Builder()
            .baseUrl(BuildConfig.ELLYSIA_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(EllysiaApiService::class.java)

        // 4. Ahora que el servicio existe, inyectarlo en el Authenticator
        tokenAuthenticator.apiService = apiService
        initialized = true
    }
}