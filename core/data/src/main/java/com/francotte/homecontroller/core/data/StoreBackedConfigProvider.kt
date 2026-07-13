package com.francotte.homecontroller.core.data

import com.francotte.homecontroller.core.datastore.DataSourceHomeAssistantConfiguration
import com.francotte.homecontroller.core.model.HomeAssistantCredentials
import com.francotte.homecontroller.core.network.HomeAssistantConfigProvider
import javax.inject.Inject

/** Alimente l'interceptor réseau avec la config enregistrée (lecture synchrone du StateFlow). */
internal class StoreBackedConfigurationProvider @Inject constructor(
    private val store: DataSourceHomeAssistantConfiguration
) : HomeAssistantConfigProvider {
    override fun current(): HomeAssistantCredentials? = store.credentials.value
}
