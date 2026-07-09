package com.francotte.homecontroller.core.bluetooth

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BluetoothModule {

    @Binds
    abstract fun bindBleScanner(impl: AndroidBleScanner): BleScanner

    // non scopé → une nouvelle instance par injection (une session par écran de contrôle)
    @Binds
    abstract fun bindEspDeviceClient(impl: AndroidEspDeviceClient): EspDeviceClient

    @Binds
    abstract fun bindBtClassicScanner(impl: AndroidBtClassicScanner): BtClassicScanner
}
