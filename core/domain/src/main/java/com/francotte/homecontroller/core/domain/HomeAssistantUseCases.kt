package com.francotte.homecontroller.core.domain

import com.francotte.homecontroller.core.data.HomeAssistantRepository
import com.francotte.homecontroller.core.model.EntityDetail
import com.francotte.homecontroller.core.model.EntityRealtimeEvent
import com.francotte.homecontroller.core.model.HomeAssistantConfig
import com.francotte.homecontroller.core.model.HomeAssistantEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveConfigUseCase @Inject constructor(private val repo: HomeAssistantRepository) {
    operator fun invoke(): Flow<HomeAssistantConfig?> = repo.config
}

class SaveConfigUseCase @Inject constructor(private val repo: HomeAssistantRepository) {
    suspend operator fun invoke(config: HomeAssistantConfig) = repo.saveConfig(config)
}

class TestConnectionUseCase @Inject constructor(private val repo: HomeAssistantRepository) {
    suspend operator fun invoke(config: HomeAssistantConfig): Result<Unit> = repo.testConnection(config)
}

class GetControllableEntitiesUseCase @Inject constructor(private val repo: HomeAssistantRepository) {
    suspend operator fun invoke(): List<HomeAssistantEntity> = repo.getControllableEntities()
}

class SetEntityStateUseCase @Inject constructor(private val repo: HomeAssistantRepository) {
    suspend operator fun invoke(entityId: String, on: Boolean) = repo.setEntityState(entityId, on)
}

class ObserveEntityStatesUseCase @Inject constructor(private val repo: HomeAssistantRepository) {
    operator fun invoke(): Flow<EntityRealtimeEvent> = repo.observeEntityStates()
}

class GetEntityDetailUseCase @Inject constructor(private val repo: HomeAssistantRepository) {
    suspend operator fun invoke(entityId: String): EntityDetail = repo.getEntityDetail(entityId)
}

class SetBrightnessUseCase @Inject constructor(private val repo: HomeAssistantRepository) {
    suspend operator fun invoke(entityId: String, percent: Int) = repo.setBrightness(entityId, percent)
}
