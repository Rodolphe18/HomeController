package com.francotte.homecontroller.presentation.scan

import com.francotte.homecontroller.domain.model.BleDevice
import com.francotte.homecontroller.domain.scan.BleScanner
import kotlinx.coroutines.flow.Flow

/** Scanner de test : rejoue le Flow qu'on lui fournit. */
class FakeBleScanner(private val flow: Flow<List<BleDevice>>) : BleScanner {
    override fun scan(): Flow<List<BleDevice>> = flow
}
