package com.francotte.homecontroller.core.data

import com.francotte.homecontroller.core.datastore.DataSourceHomeAssistantConfiguration
import com.francotte.homecontroller.core.model.HomeAssistantCredentials
import com.francotte.homecontroller.core.model.HomeAssistantException
import com.francotte.homecontroller.core.network.HomeAssistantNetworkDataSource
import com.francotte.homecontroller.core.network.NetworkEntityState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAssistantConfigurationRepositoryTest {

    private class FakeStore : DataSourceHomeAssistantConfiguration {
        val flow = MutableStateFlow<HomeAssistantCredentials?>(null)
        override val credentials: StateFlow<HomeAssistantCredentials?> = flow
        var saved: HomeAssistantCredentials? = null
        override suspend fun save(config: HomeAssistantCredentials) { saved = config; flow.value = config }
        override suspend fun clear() { flow.value = null }
    }

    private class FakeNetwork : HomeAssistantNetworkDataSource {
        var testError: Throwable? = null
        override suspend fun testConnection(config: HomeAssistantCredentials) { testError?.let { throw it } }
        override suspend fun getStates(): List<NetworkEntityState> = emptyList()
        override suspend fun getState(entityId: String): NetworkEntityState = NetworkEntityState("light.a", "on")
        override suspend fun callService(domain: String, service: String, entityId: String, brightnessPct: Int?) {}
    }

    private fun repo(net: FakeNetwork = FakeNetwork(), store: FakeStore = FakeStore()) =
        HomeAssistantConfigurationRepository(net, store)

    @Test
    fun `testConnection succes donne Result success`() = runTest {
        val result = repo().testConnection(HomeAssistantCredentials("http://x:8123", "t"))
        assertTrue(result.isSuccess)
    }

    @Test
    fun `testConnection echec porte l exception`() = runTest {
        val net = FakeNetwork().apply { testError = HomeAssistantException.Unauthorized }
        val result = repo(net).testConnection(HomeAssistantCredentials("http://x:8123", "bad"))
        assertTrue(result.exceptionOrNull() is HomeAssistantException.Unauthorized)
    }

    @Test
    fun `saveConfiguration delegue au store`() = runTest {
        val store = FakeStore()
        repo(store = store).saveConfiguration(HomeAssistantCredentials("http://x:8123", "t"))
        assertEquals("http://x:8123", store.saved?.baseUrl)
    }
}
