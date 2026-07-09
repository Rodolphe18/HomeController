package com.francotte.homecontroller.feature.btclassic

import com.francotte.homecontroller.core.bluetooth.BtClassicScanner
import com.francotte.homecontroller.core.model.BtClassicDevice
import kotlinx.coroutines.flow.Flow

/** Scanner Classic de test : rejoue le Flow fourni. */
class FakeBtClassicScanner(private val flow: Flow<List<BtClassicDevice>>) : BtClassicScanner {
    override fun scan(): Flow<List<BtClassicDevice>> = flow
}
