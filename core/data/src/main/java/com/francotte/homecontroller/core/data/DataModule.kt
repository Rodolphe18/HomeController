package com.francotte.homecontroller.core.data

import com.francotte.homecontroller.core.network.HomeAssistantConfigurationProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {

    @Binds
    abstract fun bindHomeAssistantEntities(impl: HomeAssistantEntitiesRepository): HomeAssistantEntities

    @Binds
    abstract fun bindHomeAssistantConfiguration(impl: HomeAssistantConfigurationRepository): HomeAssistantConfiguration

    @Binds
    abstract fun bindConfigurationProvider(impl: StoreBackedConfigurationProvider): HomeAssistantConfigurationProvider
}
