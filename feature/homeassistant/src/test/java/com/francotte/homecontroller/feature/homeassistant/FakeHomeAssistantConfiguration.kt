package com.francotte.homecontroller.feature.homeassistant

import com.francotte.homecontroller.core.data.HomeAssistantConfiguration
import com.francotte.homecontroller.core.model.HomeAssistantCredentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Double de test de la façade [HomeAssistantConfiguration] (credentials, test/sauvegarde). */
class FakeHomeAssistantConfiguration : HomeAssistantConfiguration {

    val credentialsFlow = MutableStateFlow<HomeAssistantCredentials?>(null)
    override val credentials: Flow<HomeAssistantCredentials?> = credentialsFlow

    var testResult: Result<Unit> = Result.success(Unit)
    var saved: HomeAssistantCredentials? = null

    override suspend fun saveConfiguration(credentials: HomeAssistantCredentials) {
        saved = credentials
        credentialsFlow.value = credentials
    }

    override suspend fun testConnection(credentials: HomeAssistantCredentials): Result<Unit> = testResult
}
