package com.francotte.homecontroller.core.data

import com.francotte.homecontroller.core.model.EntityDetail
import com.francotte.homecontroller.core.model.EntityRealtimeEvent
import com.francotte.homecontroller.core.model.HomeAssistantConfig
import com.francotte.homecontroller.core.model.HomeAssistantEntity
import kotlinx.coroutines.flow.Flow

interface HomeAssistantRepository {
    val config: Flow<HomeAssistantConfig?>
    suspend fun saveConfig(config: HomeAssistantConfig)
    /** Teste une config candidate. Succès = Result.success ; échec porte la HomeAssistantException. */
    suspend fun testConnection(config: HomeAssistantConfig): Result<Unit>
    /** Entités light/switch, mappées et filtrées. */
    suspend fun getControllableEntities(): List<HomeAssistantEntity>
    /** Allume/éteint une entité (turn_on / turn_off sur son domaine). */
    suspend fun setEntityState(entityId: String, on: Boolean)
    /** Flux temps réel des états d'entités (WebSocket, reconnexion gérée en interne). */
    fun observeEntityStates(): Flow<EntityRealtimeEvent>
    /** Détail complet d'une entité (nom, on/off, luminosité). */
    suspend fun getEntityDetail(entityId: String): EntityDetail
    /** Règle la luminosité (0 = éteindre ; >0 = allumer à ce pourcentage). */
    suspend fun setBrightness(entityId: String, percent: Int)
}
