package com.francotte.homecontroller.feature.homeassistant

import com.francotte.homecontroller.core.data.HomeAssistantEntities
import com.francotte.homecontroller.core.model.EntityDetail
import com.francotte.homecontroller.core.model.EntityRealtimeEvent
import com.francotte.homecontroller.core.model.HomeAssistantEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Double de test de la façade [HomeAssistantEntities] (liste, toggle, détail, luminosité, WS). */
class FakeHomeAssistantEntities : HomeAssistantEntities {

    var entities: List<HomeAssistantEntity> = emptyList()
    var entitiesError: Throwable? = null
    var setError: Throwable? = null
    val toggles = mutableListOf<Pair<String, Boolean>>()

    override suspend fun getControllableEntities(): Result<List<HomeAssistantEntity>> =
        entitiesError?.let { Result.failure(it) } ?: Result.success(entities)

    override suspend fun setEntityState(entityId: String, on: Boolean) {
        toggles.add(entityId to on)
        setError?.let { throw it }   // échec : l'état HA ne change pas
        // succès : HA reflète le nouvel état pour la réconciliation (rechargement).
        entities = entities.map { if (it.entityId == entityId) it.copy(isOn = on) else it }
    }

    private val realtime = MutableSharedFlow<EntityRealtimeEvent>(extraBufferCapacity = 16)
    override fun observeEntityStates(): Flow<EntityRealtimeEvent> = realtime
    suspend fun emitRealtime(event: EntityRealtimeEvent) { realtime.emit(event) }

    var detail: EntityDetail? = null
    var detailError: Throwable? = null
    val brightnessCalls = mutableListOf<Pair<String, Int>>()
    var setBrightnessError: Throwable? = null

    override suspend fun getEntityDetail(entityId: String): EntityDetail {
        detailError?.let { throw it }
        return detail ?: error("FakeHomeAssistantEntities.detail non défini")
    }

    override suspend fun setBrightness(entityId: String, percent: Int) {
        brightnessCalls.add(entityId to percent)
        setBrightnessError?.let { throw it }
    }
}
