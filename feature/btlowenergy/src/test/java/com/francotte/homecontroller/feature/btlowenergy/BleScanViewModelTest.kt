package com.francotte.homecontroller.feature.btlowenergy

import com.francotte.homecontroller.core.bluetooth.BleScanException
import com.francotte.homecontroller.core.model.BleDevice
import com.francotte.homecontroller.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BleScanViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun device(address: String, rssi: Int) =
        BleDevice(address = address, name = "dev-$address", rssi = rssi)

    @Test
    fun `etat initial est PermissionRequired`() {
        val vm = BleScanViewModel(FakeBleScanner(flowOf(emptyList())))
        assertEquals(BleScanUiState.PermissionRequired, vm.uiState.value)
    }

    @Test
    fun `sans permission reste PermissionRequired`() {
        val vm = BleScanViewModel(FakeBleScanner(flowOf(emptyList())))
        vm.updateAvailability(hasPermissions = false, isBluetoothEnabled = true)
        assertEquals(BleScanUiState.PermissionRequired, vm.uiState.value)
    }

    @Test
    fun `permission OK mais bluetooth eteint donne BluetoothOff`() {
        val vm = BleScanViewModel(FakeBleScanner(flowOf(emptyList())))
        vm.updateAvailability(hasPermissions = true, isBluetoothEnabled = false)
        assertEquals(BleScanUiState.BluetoothOff, vm.uiState.value)
    }

    @Test
    fun `permission OK et bluetooth ON donne Idle`() {
        val vm = BleScanViewModel(FakeBleScanner(flowOf(emptyList())))
        vm.updateAvailability(hasPermissions = true, isBluetoothEnabled = true)
        assertEquals(BleScanUiState.Idle, vm.uiState.value)
    }

    @Test
    fun `startScan publie les appareils tries par RSSI decroissant a la cadence de rafraichissement`() = runTest {
        val devices = listOf(device("AA", -80), device("BB", -40), device("CC", -60))
        // Flux qui émet puis reste ouvert, pour laisser un tick de sample se produire.
        val vm = BleScanViewModel(
            FakeBleScanner(flow { emit(devices); awaitCancellation() })
        )
        vm.updateAvailability(hasPermissions = true, isBluetoothEnabled = true)

        vm.startScan()
        advanceTimeBy(1_100)
        runCurrent()

        val state = vm.uiState.value
        assertTrue(state is BleScanUiState.Scanning)
        assertEquals(
            listOf("BB", "CC", "AA"),
            (state as BleScanUiState.Scanning).devices.map { it.address }
        )

        vm.stopScan()
    }

    @Test
    fun `startScan sans disponibilite ne fait rien`() = runTest {
        val vm = BleScanViewModel(FakeBleScanner(flowOf(listOf(device("AA", -50)))))
        // updateAvailability jamais appelé avec succès -> PermissionRequired
        vm.startScan()
        advanceUntilIdle()
        assertEquals(BleScanUiState.PermissionRequired, vm.uiState.value)
    }

    @Test
    fun `une erreur du scan donne l etat Error`() = runTest {
        val failing = flow<List<BleDevice>> { throw BleScanException(2) }
        val vm = BleScanViewModel(FakeBleScanner(failing))
        vm.updateAvailability(hasPermissions = true, isBluetoothEnabled = true)

        vm.startScan()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is BleScanUiState.Error)
    }

    @Test
    fun `stopScan revient a Idle`() = runTest {
        val vm = BleScanViewModel(FakeBleScanner(flowOf(listOf(device("AA", -50)))))
        vm.updateAvailability(hasPermissions = true, isBluetoothEnabled = true)
        vm.startScan()
        advanceUntilIdle()

        vm.stopScan()

        assertEquals(BleScanUiState.Idle, vm.uiState.value)
    }
}
