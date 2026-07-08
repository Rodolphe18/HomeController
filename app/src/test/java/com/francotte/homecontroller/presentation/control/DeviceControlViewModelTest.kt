package com.francotte.homecontroller.presentation.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.francotte.homecontroller.MainDispatcherRule
import com.francotte.homecontroller.domain.connection.EspConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceControlViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `connecte a l adresse au demarrage`() {
        val fake = FakeEspDeviceClient()
        DeviceControlViewModel("AA:BB:CC", fake)
        assertEquals("AA:BB:CC", fake.connectedAddress)
    }

    @Test
    fun `reflete l etat de connexion`() = runTest {
        val fake = FakeEspDeviceClient()
        val vm = DeviceControlViewModel("AA:BB:CC", fake)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }

        fake.stateFlow.value = EspConnectionState.Connected
        runCurrent()

        assertEquals(EspConnectionState.Connected, vm.uiState.value.connection)
    }

    @Test
    fun `reflete la valeur du compteur`() = runTest {
        val fake = FakeEspDeviceClient()
        val vm = DeviceControlViewModel("AA:BB:CC", fake)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        runCurrent() // laisse stateIn démarrer la collecte amont (s'abonner au compteur)

        fake.counterFlow.emit(42)
        runCurrent()

        assertEquals(42, vm.uiState.value.counter)
    }

    @Test
    fun `onLedToggle ecrit la LED et met a jour l etat`() = runTest {
        val fake = FakeEspDeviceClient()
        val vm = DeviceControlViewModel("AA:BB:CC", fake)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }

        vm.onLedToggle(true)
        advanceUntilIdle()

        assertEquals(listOf(true), fake.ledWrites)
        assertTrue(vm.uiState.value.ledOn)
    }

    @Test
    fun `un echec d ecriture LED annule la bascule et remonte une erreur`() = runTest {
        val fake = FakeEspDeviceClient().apply { failLed = true }
        val vm = DeviceControlViewModel("AA:BB:CC", fake)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }

        vm.onLedToggle(true)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.ledOn)
        assertNotNull(vm.uiState.value.transientError)
    }

    @Test
    fun `deconnecte quand le ViewModel est detruit`() {
        val fake = FakeEspDeviceClient()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DeviceControlViewModel("AA:BB:CC", fake) as T
        }
        val store = ViewModelStore()
        ViewModelProvider(store, factory)[DeviceControlViewModel::class.java]

        store.clear() // déclenche onCleared()

        assertTrue(fake.disconnectCalled)
    }
}
