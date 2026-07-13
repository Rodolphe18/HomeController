package com.francotte.homecontroller.core.datastore

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataStoreModule {
    @Binds
    abstract fun bindConfigStore(impl: DataStoreDataSourceHomeAssistantConfiguration): DataSourceHomeAssistantConfiguration
}
