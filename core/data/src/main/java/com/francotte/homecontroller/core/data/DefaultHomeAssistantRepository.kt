package com.francotte.homecontroller.core.data

import com.francotte.homecontroller.core.datastore.HomeAssistantConfigStore
import com.francotte.homecontroller.core.model.HomeAssistantConfig
import com.francotte.homecontroller.core.model.HomeAssistantEntity
import com.francotte.homecontroller.core.network.HomeAssistantNetworkDataSource
import com.francotte.homecontroller.core.network.NetworkEntityState
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

internal class DefaultHomeAssistantRepository @Inject constructor(
    private val dataSource: HomeAssistantNetworkDataSource,
    private val store: HomeAssistantConfigStore
) : HomeAssistantRepository {

    override val config: Flow<HomeAssistantConfig?> = store.config

    override suspend fun saveConfig(config: HomeAssistantConfig) = store.save(config)

    override suspend fun testConnection(config: HomeAssistantConfig): Result<Unit> =
        runCatching { dataSource.testConnection(config) }

    override suspend fun getControllableEntities(): List<HomeAssistantEntity> =
        dataSource.getStates()
            .filter { it.entityId.substringBefore(".") in CONTROLLABLE }
            .map { it.toDomain() }

    override suspend fun setEntityState(entityId: String, on: Boolean) =
        dataSource.callService(
            domain = entityId.substringBefore("."),
            service = if (on) "turn_on" else "turn_off",
            entityId = entityId
        )

    private fun NetworkEntityState.toDomain(): HomeAssistantEntity {
        val dom = entityId.substringBefore(".")
        return HomeAssistantEntity(
            entityId = entityId,
            domain = dom,
            friendlyName = attributes.friendlyName ?: entityId,
            isOn = state == "on",
            rawState = state
        )
    }

    private companion object {
        val CONTROLLABLE = setOf("light", "switch")
    }
}
