package com.francotte.homecontroller.core.data

import com.francotte.homecontroller.core.model.HomeAssistantCredentials
import kotlinx.coroutines.flow.Flow


interface HomeAssistantConfiguration {
    val credentials: Flow<HomeAssistantCredentials?>
    suspend fun saveConfiguration(credentials: HomeAssistantCredentials)
    /** Teste une config candidate. Succès = Result.success ; échec porte la HomeAssistantException. */
    suspend fun testConnection(credentials: HomeAssistantCredentials): Result<Unit>
}