package com.francotte.homecontroller.feature.homeassistant

import com.francotte.homecontroller.core.model.EntityRealtimeEvent
import com.francotte.homecontroller.core.model.EntityStateChange
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

/** L'écran des entités : chargement, toggle optimiste, temps réel. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeAssistantEntitiesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // uiState est en WhileSubscribed : le pipeline (et le WS) ne vit qu'avec un collecteur actif.
    private fun TestScope.activeVm(repo: FakeHomeAssistantEntities): HomeAssistantEntitiesViewModel {
        val model = HomeAssistantEntitiesViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} }
        return model
    }

    private fun content(model: HomeAssistantEntitiesViewModel) =
        model.uiState.value as EntitiesUiState.Content

    @Test
    fun `charge la liste au demarrage`() = runTest {
        val repo = FakeHomeAssistantEntities().apply {
            entities = listOf(HomeAssistantEntity("light.a", "light", "A", false, "off"))
        }
        val model = activeVm(repo)
        advanceUntilIdle()
        assertEquals("light.a", content(model).items.single().entityId)
    }

    @Test
    fun `un echec de chargement affiche listError et liste vide`() = runTest {
        val repo = FakeHomeAssistantEntities().apply {
            entitiesError = HomeAssistantException.Unreachable
        }
        val model = activeVm(repo)
        advanceUntilIdle()
        val c = content(model)
        assertTrue(c.items.isEmpty())
        assertNotNull(c.listError)
    }

    @Test
    fun `toggle optimiste bascule immediatement et appelle le repo`() = runTest {
        val repo = FakeHomeAssistantEntities().apply {
            entities = listOf(HomeAssistantEntity("light.a", "light", "A", false, "off"))
        }
        val model = activeVm(repo)
        advanceUntilIdle()
        model.onToggle("light.a", true)
        advanceUntilIdle()
        assertEquals("light.a" to true, repo.toggles.first())
        assertTrue(content(model).items.single { it.entityId == "light.a" }.isOn)
    }

    @Test
    fun `un echec de toggle revient en arriere et remonte une erreur transitoire`() = runTest {
        val repo = FakeHomeAssistantEntities().apply {
            entities = listOf(HomeAssistantEntity("light.a", "light", "A", false, "off"))
            setError = HomeAssistantException.Unreachable
        }
        val model = activeVm(repo)
        advanceUntilIdle()
        model.onToggle("light.a", true)
        advanceUntilIdle()
        val c = content(model)
        assertFalse(c.items.single { it.entityId == "light.a" }.isOn)  // rollback
        assertNotNull(c.transientError)
    }

    @Test
    fun `un changement temps reel met a jour l entite`() = runTest {
        val repo = FakeHomeAssistantEntities().apply {
            entities = listOf(HomeAssistantEntity("light.a", "light", "A", false, "off"))
        }
        val model = activeVm(repo)
        advanceUntilIdle()
        repo.emitRealtime(EntityRealtimeEvent.Changed(EntityStateChange("light.a", true, "on")))
        advanceUntilIdle()
        assertTrue(content(model).items.single { it.entityId == "light.a" }.isOn)
    }

    @Test
    fun `un Resync recharge la liste depuis le repo`() = runTest {
        val repo = FakeHomeAssistantEntities().apply {
            entities = listOf(HomeAssistantEntity("light.a", "light", "A", false, "off"))
        }
        val model = activeVm(repo)
        advanceUntilIdle()
        // Changement que seul un rechargement REST verrait :
        repo.entities = listOf(HomeAssistantEntity("light.a", "light", "A", true, "on"))
        repo.emitRealtime(EntityRealtimeEvent.Resync)
        advanceUntilIdle()
        assertTrue(content(model).items.single { it.entityId == "light.a" }.isOn)
    }

    @Test
    fun `un changement sur un id inconnu laisse la liste inchangee`() = runTest {
        val repo = FakeHomeAssistantEntities().apply {
            entities = listOf(HomeAssistantEntity("light.a", "light", "A", false, "off"))
        }
        val model = activeVm(repo)
        advanceUntilIdle()
        repo.emitRealtime(EntityRealtimeEvent.Changed(EntityStateChange("light.inconnu", true, "on")))
        advanceUntilIdle()
        val c = content(model)
        assertEquals(1, c.items.size)
        assertFalse(c.items.single().isOn)
    }
}
