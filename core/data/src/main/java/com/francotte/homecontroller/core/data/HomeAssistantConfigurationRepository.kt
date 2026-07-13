package com.francotte.homecontroller.core.data

import com.francotte.homecontroller.core.datastore.DataSourceHomeAssistantConfiguration
import com.francotte.homecontroller.core.model.HomeAssistantCredentials
import com.francotte.homecontroller.core.network.HomeAssistantNetworkDataSource
import kotlinx.coroutines.flow.Flow

class HomeAssistantConfigurationRepository(private val networkDataSource: HomeAssistantNetworkDataSource, private val dataSourceHomeAssistantConfiguration: DataSourceHomeAssistantConfiguration): HomeAssistantConfiguration {

    override val credentials: Flow<HomeAssistantCredentials?> = dataSourceHomeAssistantConfiguration.credentials

    override suspend fun saveConfiguration(credentials: HomeAssistantCredentials) = dataSourceHomeAssistantConfiguration.save(credentials)

    override suspend fun testConnection(credentials: HomeAssistantCredentials): Result<Unit> =
        runCatching { networkDataSource.testConnection(credentials) }


}