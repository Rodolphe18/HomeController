package com.francotte.homecontroller.feature.homeassistant

import com.francotte.homecontroller.core.data.HomeAssistantRepository
import com.francotte.homecontroller.core.domain.GetControllableEntitiesUseCase
import com.francotte.homecontroller.core.domain.ObserveConfigUseCase
import com.francotte.homecontroller.core.domain.ObserveEntityStatesUseCase
import com.francotte.homecontroller.core.domain.SaveConfigUseCase
import com.francotte.homecontroller.core.domain.SetEntityStateUseCase
import com.francotte.homecontroller.core.domain.TestConnectionUseCase
import com.francotte.homecontroller.core.model.EntityRealtimeEvent
import com.francotte.homecontroller.core.model.EntityStateChange
import com.francotte.homecontroller.core.model.HomeAssistantConfig
import com.francotte.homecontroller.core.model.HomeAssistantEntity
import com.francotte.homecontroller.core.model.HomeAssistantException
import com.francotte.homecontroller.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeAssistantViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(repo: HomeAssistantRepository) = HomeAssistantViewModel(
        observeConfig = ObserveConfigUseCase(repo),
        saveConfig = SaveConfigUseCase(repo),
        testConnection = TestConnectionUseCase(repo),
        getEntities = GetControllableEntitiesUseCase(repo),
        setEntityState = SetEntityStateUseCase(repo),
        observeEntityStates = ObserveEntityStatesUseCase(repo)
    )

    /**
     * `uiState` est en `WhileSubscribed` : sans collecteur, le pipeline (et le WS) ne démarre pas
     * et `uiState.value` resterait sur l'initial. On maintient donc un abonné pour la durée du test.
     */
    private fun TestScope.activeVm(repo: HomeAssistantRepository): HomeAssistantViewModel {
        val model = vm(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} }
        return model
    }

    @Test
    fun `sans config demarre sur Unconfigured`() = runTest {
        val model = activeVm(FakeHomeAssistantRepository())
        advanceUntilIdle()
        assertTrue(model.uiState.value is HomeAssistantUiState.Unconfigured)
    }

    @Test
    fun `avec config demarre sur Entities`() = runTest {
        val repo = FakeHomeAssistantRepository().apply {
            configFlow.value = HomeAssistantConfig("http://x:8123", "t")
            entities = listOf(HomeAssistantEntity("light.a", "light", "A", false, "off"))
        }
        val model = activeVm(repo)
        advanceUntilIdle()
        val state = model.uiState.value
        assertTrue(state is HomeAssistantUiState.Entities)
        assertEquals("light.a", (state as HomeAssistantUiState.Entities).items.single().entityId)
    }

    @Test
    fun `test and save reussi passe en Entities`() = runTest {
        val repo = FakeHomeAssistantRepository().apply {
            entities = listOf(HomeAssistantEntity("switch.p", "switch", "P", false, "off"))
        }
        val model = activeVm(repo)
        advanceUntilIdle()
        model.onUrlChange("http://192.168.1.20:8123")
        model.onTokenChange("TOKEN")
        model.onTestAndSave()
        advanceUntilIdle()
        assertTrue(model.uiState.value is HomeAssistantUiState.Entities)
    }

    @Test
    fun `test and save echoue affiche l erreur dans le formulaire`() = runTest {
        val repo = FakeHomeAssistantRepository().apply {
            testResult = Result.failure(HomeAssistantException.Unauthorized)
        }
        val model = activeVm(repo)
        advanceUntilIdle()
        model.onUrlChange("http://x:8123")
        model.onTokenChange("bad")
        model.onTestAndSave()
        advanceUntilIdle()
        val state = model.uiState.value
        assertTrue(state is HomeAssistantUiState.Unconfigured)
        assertNotNull((state as HomeAssistantUiState.Unconfigured).form.error)
        assertFalse(state.form.isTesting)
    }

    @Test
    fun `toggle optimiste bascule immediatement et appelle le repo`() = runTest {
        val repo = FakeHomeAssistantRepository().apply {
            configFlow.value = HomeAssistantConfig("http://x:8123", "t")
            entities = listOf(HomeAssistantEntity("light.a", "light", "A", false, "off"))
        }
        val model = activeVm(repo)
        advanceUntilIdle()
        model.onToggle("light.a", true)
        advanceUntilIdle()
        assertEquals("light.a" to true, repo.toggles.first())
        val state = model.uiState.value as HomeAssistantUiState.Entities
        assertTrue(state.items.single { it.entityId == "light.a" }.isOn)
    }

    @Test
    fun `un echec de toggle revient en arriere et remonte une erreur transitoire`() = runTest {
        val repo = FakeHomeAssistantRepository().apply {
            configFlow.value = HomeAssistantConfig("http://x:8123", "t")
            entities = listOf(HomeAssistantEntity("light.a", "light", "A", false, "off"))
            setError = HomeAssistantException.Unreachable
        }
        val model = activeVm(repo)
        advanceUntilIdle()
        model.onToggle("light.a", true)
        advanceUntilIdle()
        val state = model.uiState.value as HomeAssistantUiState.Entities
        assertFalse(state.items.single { it.entityId == "light.a" }.isOn)  // rollback
        assertNotNull(state.transientError)
    }

    @Test
    fun `un changement temps reel met a jour l entite`() = runTest {
        val repo = FakeHomeAssistantRepository().apply {
            configFlow.value = HomeAssistantConfig("http://x:8123", "t")
            entities = listOf(HomeAssistantEntity("light.a", "light", "A", false, "off"))
        }
        val model = activeVm(repo)
        advanceUntilIdle()
        repo.emitRealtime(EntityRealtimeEvent.Changed(EntityStateChange("light.a", true, "on")))
        advanceUntilIdle()

        val state = model.uiState.value as HomeAssistantUiState.Entities
        assertTrue(state.items.single { it.entityId == "light.a" }.isOn)
    }

    @Test
    fun `un Resync recharge la liste depuis le repo`() = runTest {
        val repo = FakeHomeAssistantRepository().apply {
            configFlow.value = HomeAssistantConfig("http://x:8123", "t")
            entities = listOf(HomeAssistantEntity("light.a", "light", "A", false, "off"))
        }
        val model = activeVm(repo)
        advanceUntilIdle()
        // Changement que seul un rechargement REST verrait :
        repo.entities = listOf(HomeAssistantEntity("light.a", "light", "A", true, "on"))
        repo.emitRealtime(EntityRealtimeEvent.Resync)
        advanceUntilIdle()

        val state = model.uiState.value as HomeAssistantUiState.Entities
        assertTrue(state.items.single { it.entityId == "light.a" }.isOn)
    }

    @Test
    fun `un changement sur un id inconnu laisse la liste inchangee`() = runTest {
        val repo = FakeHomeAssistantRepository().apply {
            configFlow.value = HomeAssistantConfig("http://x:8123", "t")
            entities = listOf(HomeAssistantEntity("light.a", "light", "A", false, "off"))
        }
        val model = activeVm(repo)
        advanceUntilIdle()
        repo.emitRealtime(EntityRealtimeEvent.Changed(EntityStateChange("light.inconnu", true, "on")))
        advanceUntilIdle()

        val state = model.uiState.value as HomeAssistantUiState.Entities
        assertEquals(1, state.items.size)
        assertFalse(state.items.single().isOn)
    }
}
