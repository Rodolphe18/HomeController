package com.francotte.homecontroller.core.network

import com.francotte.homecontroller.core.model.HomeAssistantException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class RetrofitHomeAssistantNetworkDataSourceTest {

    private fun api(block: FakeApi.() -> Unit) = FakeApi().apply(block)

    private class FakeApi : HomeAssistantApiService {
        var statesResult: () -> List<NetworkEntityState> = { emptyList() }
        var stateResult: () -> NetworkEntityState = { NetworkEntityState("light.a", "on") }
        var callServiceResult: () -> Unit = {}
        var lastServiceCall: NetworkServiceCall? = null
        override suspend fun ping(): NetworkApiInfo = NetworkApiInfo("API running.")
        override suspend fun getStates(): List<NetworkEntityState> = statesResult()
        override suspend fun getState(entityId: String): NetworkEntityState = stateResult()
        override suspend fun callService(domain: String, service: String, body: NetworkServiceCall) {
            lastServiceCall = body
            callServiceResult()
        }
    }

    @Test
    fun `getStates renvoie la liste en succes`() = runTest {
        val fake = api { statesResult = { listOf(NetworkEntityState("light.a", "on")) } }
        val ds = RetrofitHomeAssistantNetworkDataSource(fake)
        assertEquals("light.a", ds.getStates().single().entityId)
    }

    @Test
    fun `un 401 devient Unauthorized`() = runTest {
        val http401 = HttpException(Response.error<Any>(401, "".toResponseBody(null)))
        val fake = api { statesResult = { throw http401 } }
        val ds = RetrofitHomeAssistantNetworkDataSource(fake)
        val error = runCatching { ds.getStates() }.exceptionOrNull()
        assertTrue(error is HomeAssistantException.Unauthorized)
    }

    @Test
    fun `une IOException devient Unreachable`() = runTest {
        val fake = api { statesResult = { throw IOException("no route") } }
        val ds = RetrofitHomeAssistantNetworkDataSource(fake)
        val error = runCatching { ds.getStates() }.exceptionOrNull()
        assertTrue(error is HomeAssistantException.Unreachable)
    }

    @Test
    fun `getState renvoie l entite avec ses attributs`() = runTest {
        val fake = api { stateResult = { NetworkEntityState("light.a", "on", NetworkAttributes("Salon", 128)) } }
        val ds = RetrofitHomeAssistantNetworkDataSource(fake)
        assertEquals(128, ds.getState("light.a").attributes.brightness)
    }

    @Test
    fun `callService transmet brightness_pct dans le corps`() = runTest {
        val fake = api {}
        val ds = RetrofitHomeAssistantNetworkDataSource(fake)
        ds.callService("light", "turn_on", "light.a", brightnessPct = 40)
        assertEquals(40, fake.lastServiceCall?.brightnessPct)
    }
}
