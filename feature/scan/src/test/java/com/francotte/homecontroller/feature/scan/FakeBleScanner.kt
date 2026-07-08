package com.francotte.homecontroller.feature.scan

import com.francotte.homecontroller.core.bluetooth.BleScanner
import com.francotte.homecontroller.core.model.BleDevice
import kotlinx.coroutines.flow.Flow

/** Scanner de test : rejoue le Flow qu'on lui fournit. */
class FakeBleScanner(private val flow: Flow<List<BleDevice>>) : BleScanner {
    override fun scan(): Flow<List<BleDevice>> = flow
}
