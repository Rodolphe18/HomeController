package com.francotte.homecontroller.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

internal interface HomeAssistantApiService {
    @GET("api/")
    suspend fun ping(): NetworkApiInfo

    @GET("api/states")
    suspend fun getStates(): List<NetworkEntityState>

    @POST("api/services/{domain}/{service}")
    suspend fun callService(
        @Path("domain") domain: String,
        @Path("service") service: String,
        @Body body: NetworkServiceCall
    )
}
