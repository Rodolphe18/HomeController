package com.francotte.homecontroller.feature.homeassistant

import com.francotte.homecontroller.core.model.HomeAssistantCredentials
import com.francotte.homecontroller.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** L'aiguilleur ne fait QUE choisir l'écran (configuration vs entités). */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeAssistantViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // uiState est en WhileSubscribed : on maintient un abonné pour la durée du test.
    private fun TestScope.activeVm(config: FakeHomeAssistantConfiguration): HomeAssistantViewModel {
        val model = HomeAssistantViewModel(config)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} }
        return model
    }

    @Test
    fun `sans config affiche Unconfigured non annulable`() = runTest {
        val model = activeVm(FakeHomeAssistantConfiguration())
        advanceUntilIdle()
        val state = model.uiState.value
        assertTrue(state is HomeAssistantUiState.Unconfigured)
        assertFalse((state as HomeAssistantUiState.Unconfigured).canCancel)
    }

    @Test
    fun `avec config affiche Entities`() = runTest {
        val config = FakeHomeAssistantConfiguration().apply {
            credentialsFlow.value = HomeAssistantCredentials("http://x:8123", "t")
        }
        val model = activeVm(config)
        advanceUntilIdle()
        assertEquals(HomeAssistantUiState.Entities, model.uiState.value)
    }

    @Test
    fun `onEditConfig avec config affiche Unconfigured annulable`() = runTest {
        val config = FakeHomeAssistantConfiguration().apply {
            credentialsFlow.value = HomeAssistantCredentials("http://x:8123", "t")
        }
        val model = activeVm(config)
        advanceUntilIdle()
        model.navigateToConfigurationScreen()
        advanceUntilIdle()
        val state = model.uiState.value
        assertTrue(state is HomeAssistantUiState.Unconfigured)
        assertTrue((state as HomeAssistantUiState.Unconfigured).canCancel)
    }

    @Test
    fun `onCancelEdit revient sur Entities`() = runTest {
        val config = FakeHomeAssistantConfiguration().apply {
            credentialsFlow.value = HomeAssistantCredentials("http://x:8123", "t")
        }
        val model = activeVm(config)
        advanceUntilIdle()
        model.navigateToConfigurationScreen()
        advanceUntilIdle()
        model.popBackConfigurationScreen()
        advanceUntilIdle()
        assertEquals(HomeAssistantUiState.Entities, model.uiState.value)
    }

    @Test
    fun `onConfigurationSaved revient sur Entities`() = runTest {
        val config = FakeHomeAssistantConfiguration().apply {
            credentialsFlow.value = HomeAssistantCredentials("http://x:8123", "t")
        }
        val model = activeVm(config)
        advanceUntilIdle()
        model.navigateToConfigurationScreen()
        advanceUntilIdle()
        model.onConfigurationSaved()
        advanceUntilIdle()
        assertEquals(HomeAssistantUiState.Entities, model.uiState.value)
    }
}
