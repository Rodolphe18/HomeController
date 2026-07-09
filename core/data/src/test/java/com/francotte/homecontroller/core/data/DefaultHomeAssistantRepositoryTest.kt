package com.francotte.homecontroller.core.data

import com.francotte.homecontroller.core.datastore.HomeAssistantConfigStore
import com.francotte.homecontroller.core.model.HomeAssistantConfig
import com.francotte.homecontroller.core.model.HomeAssistantException
import com.francotte.homecontroller.core.network.EntityRegistryEntry
import com.francotte.homecontroller.core.network.HomeAssistantNetworkDataSource
import com.francotte.homecontroller.core.network.HomeAssistantWebSocketDataSource
import com.francotte.homecontroller.core.network.NetworkAttributes
import com.francotte.homecontroller.core.network.NetworkEntityState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultHomeAssistantRepositoryTest {

    private class FakeStore : HomeAssistantConfigStore {
        val flow = MutableStateFlow<HomeAssistantConfig?>(null)
        override val config: StateFlow<HomeAssistantConfig?> = flow
        var saved: HomeAssistantConfig? = null
        override suspend fun save(config: HomeAssistantConfig) { saved = config; flow.value = config }
        override suspend fun clear() { flow.value = null }
    }

    private class FakeDataSource : HomeAssistantNetworkDataSource {
        var states: List<NetworkEntityState> = emptyList()
        var testError: Throwable? = null
        val serviceCalls = mutableListOf<Triple<String, String, String>>()
        override suspend fun testConnection(config: HomeAssistantConfig) { testError?.let { throw it } }
        override suspend fun getStates(): List<NetworkEntityState> = states
        override suspend fun callService(domain: String, service: String, entityId: String) {
            serviceCalls.add(Triple(domain, service, entityId))
        }
    }

    private class FakeWebSocketDataSource : HomeAssistantWebSocketDataSource {
        var registry: List<EntityRegistryEntry> = emptyList()
        var error: Throwable? = null
        override suspend fun getEntityRegistry(): List<EntityRegistryEntry> =
            error?.let { throw it } ?: registry
    }

    private fun repo(
        ds: FakeDataSource = FakeDataSource(),
        ws: FakeWebSocketDataSource = FakeWebSocketDataSource(),
        store: FakeStore = FakeStore()
    ) = DefaultHomeAssistantRepository(ds, ws, store)

    @Test
    fun `getControllableEntities filtre light et switch et mappe`() = runTest {
        val ds = FakeDataSource().apply {
            states = listOf(
                NetworkEntityState("light.salon", "on", NetworkAttributes("Salon")),
                NetworkEntityState("switch.prise", "off"),
                NetworkEntityState("sensor.temp", "21.5")   // ignoré
            )
        }
        val result = repo(ds).getControllableEntities()

        assertEquals(listOf("light.salon", "switch.prise"), result.map { it.entityId })
        val salon = result.first()
        assertEquals("Salon", salon.friendlyName)   // friendly_name
        assertTrue(salon.isOn)
        val prise = result[1]
        assertEquals("switch.prise", prise.friendlyName)  // repli sur entityId
        assertFalse(prise.isOn)
        assertEquals("switch", prise.domain)
    }

    @Test
    fun `getControllableEntities masque les entites auxiliaires (entity_category non nul)`() = runTest {
        val ds = FakeDataSource().apply {
            states = listOf(
                NetworkEntityState("switch.tapo_p110", "off", NetworkAttributes("Tapo P110")),
                NetworkEntityState("switch.tapo_p110_led", "off", NetworkAttributes("Tapo P110 LED")),
                NetworkEntityState("light.tapo_l535", "on", NetworkAttributes("Tapo L535"))
            )
        }
        val ws = FakeWebSocketDataSource().apply {
            registry = listOf(
                EntityRegistryEntry("switch.tapo_p110", entityCategory = null),
                EntityRegistryEntry("switch.tapo_p110_led", entityCategory = "config"),
                EntityRegistryEntry("light.tapo_l535", entityCategory = null)
            )
        }
        val result = repo(ds, ws).getControllableEntities()

        assertEquals(listOf("switch.tapo_p110", "light.tapo_l535"), result.map { it.entityId })
    }

    @Test
    fun `si le registre WebSocket echoue on ne filtre pas (degradation gracieuse)`() = runTest {
        val ds = FakeDataSource().apply {
            states = listOf(
                NetworkEntityState("switch.tapo_p110", "off", NetworkAttributes("Tapo P110")),
                NetworkEntityState("switch.tapo_p110_led", "off", NetworkAttributes("Tapo P110 LED"))
            )
        }
        val ws = FakeWebSocketDataSource().apply { error = HomeAssistantException.Unreachable }
        val result = repo(ds, ws).getControllableEntities()

        assertEquals(listOf("switch.tapo_p110", "switch.tapo_p110_led"), result.map { it.entityId })
    }

    @Test
    fun `setEntityState allume via turn_on sur le bon domaine`() = runTest {
        val ds = FakeDataSource()
        repo(ds).setEntityState("light.salon", true)
        assertEquals(Triple("light", "turn_on", "light.salon"), ds.serviceCalls.single())
    }

    @Test
    fun `setEntityState eteint via turn_off`() = runTest {
        val ds = FakeDataSource()
        repo(ds).setEntityState("switch.prise", false)
        assertEquals(Triple("switch", "turn_off", "switch.prise"), ds.serviceCalls.single())
    }

    @Test
    fun `testConnection succes donne Result success`() = runTest {
        val result = repo().testConnection(HomeAssistantConfig("http://x:8123", "t"))
        assertTrue(result.isSuccess)
    }

    @Test
    fun `testConnection echec porte l exception`() = runTest {
        val ds = FakeDataSource().apply { testError = HomeAssistantException.Unauthorized }
        val result = repo(ds).testConnection(HomeAssistantConfig("http://x:8123", "bad"))
        assertTrue(result.exceptionOrNull() is HomeAssistantException.Unauthorized)
    }

    @Test
    fun `saveConfig delegue au store`() = runTest {
        val store = FakeStore()
        repo(store = store).saveConfig(HomeAssistantConfig("http://x:8123", "t"))
        assertEquals("http://x:8123", store.saved?.baseUrl)
    }
}
