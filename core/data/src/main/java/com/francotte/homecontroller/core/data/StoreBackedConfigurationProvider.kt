package com.francotte.homecontroller.core.data

import com.francotte.homecontroller.core.datastore.DataSourceHomeAssistantConfiguration
import com.francotte.homecontroller.core.model.HomeAssistantCredentials
import com.francotte.homecontroller.core.network.HomeAssistantConfigurationProvider
import javax.inject.Inject

/** Alimente l'interceptor réseau avec la config enregistrée (lecture synchrone du StateFlow). */
internal class StoreBackedConfigurationProvider @Inject constructor(
    private val dataSourceHomeAssistantConfiguration: DataSourceHomeAssistantConfiguration
) : HomeAssistantConfigurationProvider {
    override fun current(): HomeAssistantCredentials? = dataSourceHomeAssistantConfiguration.credentials.value
}
