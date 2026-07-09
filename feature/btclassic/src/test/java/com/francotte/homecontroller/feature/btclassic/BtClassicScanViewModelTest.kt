package com.francotte.homecontroller.feature.btclassic

import com.francotte.homecontroller.core.bluetooth.BtClassicScanException
import com.francotte.homecontroller.core.model.BtClassicDevice
import com.francotte.homecontroller.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BtClassicScanViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun dev(address: String, rssi: Int?, bonded: Boolean = false) =
        BtClassicDevice(address = address, name = "dev-$address", rssi = rssi, bonded = bonded)

    @Test
    fun `etat initial est PermissionRequired`() {
        val vm = BtClassicScanViewModel(FakeBtClassicScanner(flowOf(emptyList())))
        assertEquals(BtClassicUiState.PermissionRequired, vm.uiState.value)
    }

    @Test
    fun `permission OK mais bluetooth eteint donne BluetoothOff`() {
        val vm = BtClassicScanViewModel(FakeBtClassicScanner(flowOf(emptyList())))
        vm.updateAvailability(hasPermissions = true, isBluetoothEnabled = false)
        assertEquals(BtClassicUiState.BluetoothOff, vm.uiState.value)
    }

    @Test
    fun `permission OK et bluetooth ON donne Idle`() {
        val vm = BtClassicScanViewModel(FakeBtClassicScanner(flowOf(emptyList())))
        vm.updateAvailability(hasPermissions = true, isBluetoothEnabled = true)
        assertEquals(BtClassicUiState.Idle, vm.uiState.value)
    }

    @Test
    fun `scan termine passe en Finished avec liste triee appaires puis rssi`() = runTest {
        val devices = listOf(
            dev("AA", -80),
            dev("BB", -40),
            dev("CC", -60, bonded = true)   // appairé → en tête
        )
        val vm = BtClassicScanViewModel(FakeBtClassicScanner(flowOf(devices)))
        vm.updateAvailability(hasPermissions = true, isBluetoothEnabled = true)

        vm.startScan()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is BtClassicUiState.Finished)
        assertEquals(
            listOf("CC", "BB", "AA"),
            (state as BtClassicUiState.Finished).devices.map { it.address }
        )
    }

    @Test
    fun `stopScan pendant le scan passe en Finished avec la liste courante`() = runTest {
        val vm = BtClassicScanViewModel(
            FakeBtClassicScanner(flow { emit(listOf(dev("AA", -50))); awaitCancellation() })
        )
        vm.updateAvailability(hasPermissions = true, isBluetoothEnabled = true)

        vm.startScan()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is BtClassicUiState.Scanning)

        vm.stopScan()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is BtClassicUiState.Finished)
        assertEquals(listOf("AA"), (state as BtClassicUiState.Finished).devices.map { it.address })
    }

    @Test
    fun `une erreur du scan donne Error`() = runTest {
        val failing = flow<List<BtClassicDevice>> { throw BtClassicScanException("boom") }
        val vm = BtClassicScanViewModel(FakeBtClassicScanner(failing))
        vm.updateAvailability(hasPermissions = true, isBluetoothEnabled = true)

        vm.startScan()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is BtClassicUiState.Error)
    }

    @Test
    fun `startScan sans disponibilite ne fait rien`() = runTest {
        val vm = BtClassicScanViewModel(FakeBtClassicScanner(flowOf(listOf(dev("AA", -50)))))
        vm.startScan()
        advanceUntilIdle()
        assertEquals(BtClassicUiState.PermissionRequired, vm.uiState.value)
    }
}
