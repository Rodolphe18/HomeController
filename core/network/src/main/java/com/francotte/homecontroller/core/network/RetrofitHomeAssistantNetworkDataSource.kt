package com.francotte.homecontroller.core.network

import com.francotte.homecontroller.core.model.HomeAssistantConfig
import com.francotte.homecontroller.core.model.HomeAssistantException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

internal class RetrofitHomeAssistantNetworkDataSource @Inject constructor(
    private val api: HomeAssistantApiService
) : HomeAssistantNetworkDataSource {

    // Client sans interceptor : les tests de connexion utilisent une config candidate.
    private val candidateClient = OkHttpClient()

    override suspend fun getStates(): List<NetworkEntityState> =
        call { api.getStates() }

    override suspend fun callService(domain: String, service: String, entityId: String) =
        call { api.callService(domain, service, NetworkServiceCall(entityId)) }

    override suspend fun testConnection(config: HomeAssistantConfig) = withContext(Dispatchers.IO) {
        val url = config.baseUrl.trimEnd('/') + "/api/"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.token}")
            .build()
        try {
            candidateClient.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> Unit
                    response.code == 401 -> throw HomeAssistantException.Unauthorized
                    else -> throw HomeAssistantException.Unknown
                }
            }
        } catch (e: HomeAssistantException) {
            throw e
        } catch (e: IOException) {
            throw HomeAssistantException.Unreachable
        }
    }

    private suspend fun <T> call(block: suspend () -> T): T =
        try {
            block()
        } catch (e: HomeAssistantException) {
            throw e
        } catch (e: HttpException) {
            throw if (e.code() == 401) HomeAssistantException.Unauthorized else HomeAssistantException.Unknown
        } catch (e: IOException) {
            throw HomeAssistantException.Unreachable
        }
}
