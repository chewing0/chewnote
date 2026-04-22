package com.example.myapplication.agent.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureSettingsStore(context: Context) {
    private val appContext = context.applicationContext

    private val preferences: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getApiKey(): String {
        return preferences.getString(KEY_API_KEY, "") ?: ""
    }

    fun saveApiKey(value: String) {
        preferences.edit().putString(KEY_API_KEY, value).apply()
    }

    companion object {
        private const val FILE_NAME = "secure_settings"
        private const val KEY_API_KEY = "api_key"
    }
}
