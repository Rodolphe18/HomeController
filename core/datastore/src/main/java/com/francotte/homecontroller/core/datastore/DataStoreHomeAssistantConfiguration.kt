package com.francotte.homecontroller.core.datastore

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.francotte.homecontroller.core.model.HomeAssistantConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.configurationDataStore: DataStore<Preferences> by preferencesDataStore(name = "home_assistant_config")

@Singleton
internal class DataStoreHomeAssistantConfiguration @Inject constructor(
    @ApplicationContext context: Context
) : HomeAssistantConfiguration {

    private val dataStore = context.configurationDataStore

    // Seed synchrone (lecture unique, petite) pour offrir un `.value` à l'interceptor réseau.
    private val _configuration = MutableStateFlow(runBlocking { read() })
    override val configuration: StateFlow<HomeAssistantConfig?> = _configuration.asStateFlow()

    override suspend fun save(config: HomeAssistantConfig) {
        val sealed = TokenCrypto.encrypt(config.token)
        dataStore.edit { prefs ->
            prefs[KEY_URL] = config.baseUrl
            prefs[KEY_TOKEN_CIPHER] = sealed.ciphertext.toBase64()
            prefs[KEY_TOKEN_IV] = sealed.iv.toBase64()
        }
        _configuration.value = config
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
        _configuration.value = null
    }

    private suspend fun read(): HomeAssistantConfig? {
        val prefs = dataStore.data.first()
        val url = prefs[KEY_URL] ?: return null
        val cipher = prefs[KEY_TOKEN_CIPHER] ?: return null
        val iv = prefs[KEY_TOKEN_IV] ?: return null
        // Si la clé Keystore a disparu (restauration sur un autre appareil…), on repart non configuré.
        return runCatching {
            HomeAssistantConfig(url, TokenCrypto.decrypt(iv.fromBase64(), cipher.fromBase64()))
        }.getOrNull()
    }

    private fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        val KEY_URL = stringPreferencesKey("url")
        val KEY_TOKEN_CIPHER = stringPreferencesKey("token_cipher")
        val KEY_TOKEN_IV = stringPreferencesKey("token_iv")
    }
}
