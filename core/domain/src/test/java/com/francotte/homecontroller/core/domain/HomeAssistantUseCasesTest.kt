package com.francotte.homecontroller.core.domain

import com.francotte.homecontroller.core.data.HomeAssistantRepository
import com.francotte.homecontroller.core.model.HomeAssistantConfig
import com.francotte.homecontroller.core.model.HomeAssistantEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAssistantUseCasesTest {

    private class FakeRepo : HomeAssistantRepository {
        override val config: Flow<HomeAssistantConfig?> = flowOf(null)
        var savedConfig: HomeAssistantConfig? = null
        val toggles = mutableListOf<Pair<String, Boolean>>()
        override suspend fun saveConfig(config: HomeAssistantConfig) { savedConfig = config }
        override suspend fun testConnection(config: HomeAssistantConfig) = Result.success(Unit)
        override suspend fun getControllableEntities() =
            listOf(HomeAssistantEntity("light.a", "light", "A", true, "on"))
        override suspend fun setEntityState(entityId: String, on: Boolean) { toggles.add(entityId to on) }
    }

    @Test
    fun `GetControllableEntities delegue au repository`() = runTest {
        val entities = GetControllableEntitiesUseCase(FakeRepo())()
        assertEquals("light.a", entities.single().entityId)
    }

    @Test
    fun `SetEntityState delegue au repository`() = runTest {
        val repo = FakeRepo()
        SetEntityStateUseCase(repo)("switch.p", false)
        assertEquals("switch.p" to false, repo.toggles.single())
    }

    @Test
    fun `SaveConfig delegue au repository`() = runTest {
        val repo = FakeRepo()
        SaveConfigUseCase(repo)(HomeAssistantConfig("http://x:8123", "t"))
        assertEquals("http://x:8123", repo.savedConfig?.baseUrl)
    }
}
