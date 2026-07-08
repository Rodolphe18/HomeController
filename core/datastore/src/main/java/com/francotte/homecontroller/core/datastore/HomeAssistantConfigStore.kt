package com.francotte.homecontroller.core.datastore

import com.francotte.homecontroller.core.model.HomeAssistantConfig
import kotlinx.coroutines.flow.StateFlow

/** Stockage sécurisé de la configuration Home Assistant. */
interface HomeAssistantConfigStore {
    /** Config courante (null = pas encore configuré). StateFlow → lecture synchrone via `.value`. */
    val config: StateFlow<HomeAssistantConfig?>
    suspend fun save(config: HomeAssistantConfig)
    suspend fun clear()
}
