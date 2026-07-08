package com.francotte.homecontroller.core.data

import com.francotte.homecontroller.core.network.HomeAssistantConfigProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    abstract fun bindRepository(impl: DefaultHomeAssistantRepository): HomeAssistantRepository

    @Binds
    abstract fun bindConfigProvider(impl: StoreBackedConfigProvider): HomeAssistantConfigProvider
}
