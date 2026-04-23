package com.example.myapplication.agent.net

import com.example.myapplication.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    // Emulator can call host machine backend with 10.0.2.2
    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/"

    private val client: OkHttpClient by lazy {
        val logger = HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        OkHttpClient.Builder()
            .addInterceptor(logger)
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun sanitizeBaseUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return DEFAULT_BASE_URL

        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }

        val normalized = withScheme.removeSuffix("/")
        val emulatorReachable = normalized
            .replace("://127.0.0.1", "://10.0.2.2")
            .replace("://localhost", "://10.0.2.2")
        return "$emulatorReachable/"
    }

    fun resolveBackendUrl(url: String): String = sanitizeBaseUrl(url)

    private fun createRetrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(sanitizeBaseUrl(baseUrl))
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
    }

    fun createAgentApi(baseUrl: String): AgentApi {
        return createRetrofit(baseUrl).create(AgentApi::class.java)
    }
}
