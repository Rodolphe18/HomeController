package com.francotte.homecontroller.feature.homeassistant

import com.francotte.homecontroller.core.data.HomeAssistantRepository
import com.francotte.homecontroller.core.model.HomeAssistantConfig
import com.francotte.homecontroller.core.model.HomeAssistantEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeHomeAssistantRepository : HomeAssistantRepository {
    val configFlow = MutableStateFlow<HomeAssistantConfig?>(null)
    override val config: Flow<HomeAssistantConfig?> = configFlow

    var entities: List<HomeAssistantEntity> = emptyList()
    var entitiesError: Throwable? = null
    var testResult: Result<Unit> = Result.success(Unit)
    var setError: Throwable? = null
    val toggles = mutableListOf<Pair<String, Boolean>>()

    override suspend fun saveConfig(config: HomeAssistantConfig) { configFlow.value = config }
    override suspend fun testConnection(config: HomeAssistantConfig): Result<Unit> = testResult
    override suspend fun getControllableEntities(): List<HomeAssistantEntity> {
        entitiesError?.let { throw it }
        return entities
    }
    override suspend fun setEntityState(entityId: String, on: Boolean) {
        toggles.add(entityId to on)
        setError?.let { throw it }   // échec : l'état HA ne change pas
        // succès : HA reflète le nouvel état pour la réconciliation (rechargement).
        entities = entities.map { if (it.entityId == entityId) it.copy(isOn = on) else it }
    }
}
