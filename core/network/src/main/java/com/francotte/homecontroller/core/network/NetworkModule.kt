package com.francotte.homecontroller.core.network

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {

    @Provides
    @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun okHttpClient(interceptor: HomeAssistantAuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("http://localhost/")   // placeholder ; réécrit par l'interceptor
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun apiService(retrofit: Retrofit): HomeAssistantApiService =
        retrofit.create(HomeAssistantApiService::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NetworkBindsModule {
    @Binds
    abstract fun dataSource(impl: RetrofitHomeAssistantNetworkDataSource): HomeAssistantNetworkDataSource

    @Binds
    abstract fun webSocketDataSource(
        impl: OkHttpHomeAssistantWebSocketDataSource
    ): HomeAssistantWebSocketDataSource
}
