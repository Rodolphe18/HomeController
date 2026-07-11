package com.francotte.homecontroller.core.data

import com.francotte.homecontroller.core.datastore.HomeAssistantConfiguration
import com.francotte.homecontroller.core.model.EntityDetail
import com.francotte.homecontroller.core.model.EntityRealtimeEvent
import com.francotte.homecontroller.core.model.EntityStateChange
import com.francotte.homecontroller.core.model.HomeAssistantConfig
import com.francotte.homecontroller.core.model.HomeAssistantException
import com.francotte.homecontroller.core.network.HomeAssistantNetworkDataSource
import com.francotte.homecontroller.core.network.HomeAssistantWebSocketDataSource
import com.francotte.homecontroller.core.network.NetworkAttributes
import com.francotte.homecontroller.core.network.NetworkEntityState
import com.francotte.homecontroller.core.network.WsStateEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultHomeAssistantRepositoryTest {

    private class FakeStore : HomeAssistantConfiguration {
        val flow = MutableStateFlow<HomeAssistantConfig?>(null)
        override val configuration: StateFlow<HomeAssistantConfig?> = flow
        var saved: HomeAssistantConfig? = null
        override suspend fun save(config: HomeAssistantConfig) { saved = config; flow.value = config }
        override suspend fun clear() { flow.value = null }
    }

    private class FakeDataSource : HomeAssistantNetworkDataSource {
        var states: List<NetworkEntityState> = emptyList()
        var stateResult: NetworkEntityState = NetworkEntityState("light.a", "on")
        var testError: Throwable? = null
        val serviceCalls = mutableListOf<Triple<String, String, String>>()
        var lastBrightnessPct: Int? = null
        override suspend fun testConnection(config: HomeAssistantConfig) { testError?.let { throw it } }
        override suspend fun getStates(): List<NetworkEntityState> = states
        override suspend fun getState(entityId: String): NetworkEntityState = stateResult
        override suspend fun callService(domain: String, service: String, entityId: String, brightnessPct: Int?) {
            serviceCalls.add(Triple(domain, service, entityId))
            lastBrightnessPct = brightnessPct
        }
    }

    private class FakeWebSocketDataSource : HomeAssistantWebSocketDataSource {
        var events: List<WsStateEvent> = emptyList()
        override fun observeStateChanges(): Flow<WsStateEvent> = flowOf(*events.toTypedArray())
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
    fun `getControllableEntities masque les entites auxiliaires (suffixe d un autre id)`() = runTest {
        val ds = FakeDataSource().apply {
            states = listOf(
                NetworkEntityState("switch.tapo_p110", "off", NetworkAttributes("Tapo P110")),
                NetworkEntityState("switch.tapo_p110_led", "off", NetworkAttributes("Tapo P110 LED")),
                NetworkEntityState("switch.tapo_p110_arret_auto", "off", NetworkAttributes("Tapo P110 Arrêt auto")),
                NetworkEntityState("light.tapo_l535", "on", NetworkAttributes("Tapo L535")),
                NetworkEntityState("switch.tapo_l535_maj", "off", NetworkAttributes("Tapo L535 MAJ"))
            )
        }
        val result = repo(ds).getControllableEntities()

        // Seules les entités principales sont gardées ; les auxiliaires (suffixe d'un autre id) tombent.
        assertEquals(listOf("switch.tapo_p110", "light.tapo_l535"), result.map { it.entityId })
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

    @Test
    fun `observeEntityStates mappe Subscribed en Resync et Changed en EntityStateChange`() = runTest {
        val ws = FakeWebSocketDataSource().apply {
            events = listOf(
                WsStateEvent.Subscribed,
                WsStateEvent.Changed("light.salon", "on"),
                WsStateEvent.Changed("switch.prise", "off")
            )
        }
        val result = repo(ws = ws).observeEntityStates().toList()

        assertEquals(
            listOf(
                EntityRealtimeEvent.Resync,
                EntityRealtimeEvent.Changed(EntityStateChange("light.salon", true, "on")),
                EntityRealtimeEvent.Changed(EntityStateChange("switch.prise", false, "off"))
            ),
            result
        )
    }

    @Test
    fun `getEntityDetail mappe luminosite et supportsBrightness pour une lumiere`() = runTest {
        val ds = FakeDataSource().apply {
            stateResult = NetworkEntityState("light.salon", "on", NetworkAttributes("Salon", 128))
        }
        val detail = repo(ds).getEntityDetail("light.salon")
        assertEquals("Salon", detail.friendlyName)
        assertTrue(detail.isOn)
        assertTrue(detail.supportsBrightness)
        assertEquals(50, detail.brightnessPercent)
    }

    @Test
    fun `getEntityDetail sur une prise n a pas de luminosite`() = runTest {
        val ds = FakeDataSource().apply {
            stateResult = NetworkEntityState("switch.prise", "off", NetworkAttributes("Prise"))
        }
        val detail = repo(ds).getEntityDetail("switch.prise")
        assertFalse(detail.supportsBrightness)
        assertEquals(0, detail.brightnessPercent)
    }

    @Test
    fun `setBrightness a zero eteint`() = runTest {
        val ds = FakeDataSource()
        repo(ds).setBrightness("light.salon", 0)
        assertEquals(Triple("light", "turn_off", "light.salon"), ds.serviceCalls.single())
    }

    @Test
    fun `setBrightness positif allume avec le pourcentage`() = runTest {
        val ds = FakeDataSource()
        repo(ds).setBrightness("light.salon", 60)
        assertEquals(Triple("light", "turn_on", "light.salon"), ds.serviceCalls.single())
        assertEquals(60, ds.lastBrightnessPct)
    }

    @Test
    fun `observeEntityStates propage la luminosite`() = runTest {
        val ws = FakeWebSocketDataSource().apply {
            events = listOf(WsStateEvent.Changed("light.a", "on", 75))
        }
        val result = repo(ws = ws).observeEntityStates().toList()
        val change = (result.single() as EntityRealtimeEvent.Changed).change
        assertEquals(75, change.brightnessPercent)
    }
}
