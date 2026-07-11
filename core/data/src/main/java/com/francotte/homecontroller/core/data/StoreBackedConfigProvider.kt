package com.francotte.homecontroller.core.data

import com.francotte.homecontroller.core.datastore.HomeAssistantConfiguration
import com.francotte.homecontroller.core.model.HomeAssistantConfig
import com.francotte.homecontroller.core.network.HomeAssistantConfigProvider
import javax.inject.Inject

/** Alimente l'interceptor réseau avec la config enregistrée (lecture synchrone du StateFlow). */
internal class StoreBackedConfigProvider @Inject constructor(
    private val store: HomeAssistantConfiguration
) : HomeAssistantConfigProvider {
    override fun current(): HomeAssistantConfig? = store.configuration.value
}
