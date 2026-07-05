package com.francotte.homecontroller.presentation.scan

import com.francotte.homecontroller.MainDispatcherRule
import com.francotte.homecontroller.domain.model.BleDevice
import com.francotte.homecontroller.domain.scan.BleScanException
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
class ScanViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun device(address: String, rssi: Int) =
        BleDevice(address = address, name = "dev-$address", rssi = rssi)

    @Test
    fun `etat initial est PermissionRequired`() {
        val vm = ScanViewModel(FakeBleScanner(flowOf(emptyList())))
        assertEquals(ScanUiState.PermissionRequired, vm.uiState.value)
    }

    @Test
    fun `sans permission reste PermissionRequired`() {
        val vm = ScanViewModel(FakeBleScanner(flowOf(emptyList())))
        vm.updateAvailability(hasPermissions = false, isBluetoothEnabled = true)
        assertEquals(ScanUiState.PermissionRequired, vm.uiState.value)
    }

    @Test
    fun `permission OK mais bluetooth eteint donne BluetoothOff`() {
        val vm = ScanViewModel(FakeBleScanner(flowOf(emptyList())))
        vm.updateAvailability(hasPermissions = true, isBluetoothEnabled = false)
        assertEquals(ScanUiState.BluetoothOff, vm.uiState.value)
    }

    @Test
    fun `permission OK et bluetooth ON donne Idle`() {
        val vm = ScanViewModel(FakeBleScanner(flowOf(emptyList())))
        vm.updateAvailability(hasPermissions = true, isBluetoothEnabled = true)
        assertEquals(ScanUiState.Idle, vm.uiState.value)
    }

    @Test
    fun `startScan publie les appareils tries par RSSI decroissant a la cadence de rafraichissement`() = runTest {
        val devices = listOf(device("AA", -80), device("BB", -40), device("CC", -60))
        // Flux qui émet puis reste ouvert, pour laisser un tick de sample se produire.
        val vm = ScanViewModel(
            FakeBleScanner(flow { emit(devices); awaitCancellation() }),
            refreshIntervalMs = 1_000L
        )
        vm.updateAvailability(hasPermissions = true, isBluetoothEnabled = true)

        vm.startScan()
        advanceTimeBy(1_100)
        runCurrent()

        val state = vm.uiState.value
        assertTrue(state is ScanUiState.Scanning)
        assertEquals(
            listOf("BB", "CC", "AA"),
            (state as ScanUiState.Scanning).devices.map { it.address }
        )

        vm.stopScan()
    }

    @Test
    fun `startScan sans disponibilite ne fait rien`() = runTest {
        val vm = ScanViewModel(FakeBleScanner(flowOf(listOf(device("AA", -50)))))
        // updateAvailability jamais appelé avec succès -> PermissionRequired
        vm.startScan()
        advanceUntilIdle()
        assertEquals(ScanUiState.PermissionRequired, vm.uiState.value)
    }

    @Test
    fun `une erreur du scan donne l etat Error`() = runTest {
        val failing = flow<List<BleDevice>> { throw BleScanException(2) }
        val vm = ScanViewModel(FakeBleScanner(failing))
        vm.updateAvailability(hasPermissions = true, isBluetoothEnabled = true)

        vm.startScan()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is ScanUiState.Error)
    }

    @Test
    fun `stopScan revient a Idle`() = runTest {
        val vm = ScanViewModel(FakeBleScanner(flowOf(listOf(device("AA", -50)))))
        vm.updateAvailability(hasPermissions = true, isBluetoothEnabled = true)
        vm.startScan()
        advanceUntilIdle()

        vm.stopScan()

        assertEquals(ScanUiState.Idle, vm.uiState.value)
    }
}
