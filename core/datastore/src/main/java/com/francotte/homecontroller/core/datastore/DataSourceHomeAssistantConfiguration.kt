package com.francotte.homecontroller.core.datastore

import com.francotte.homecontroller.core.model.HomeAssistantCredentials
import kotlinx.coroutines.flow.StateFlow

/** Stockage sécurisé de la configuration Home Assistant. */
interface DataSourceHomeAssistantConfiguration {
    /** Config courante (null = pas encore configuré). StateFlow → lecture synchrone via `.value`. */
    val credentials: StateFlow<HomeAssistantCredentials?>
    suspend fun save(config: HomeAssistantCredentials)
    suspend fun clear()
}
