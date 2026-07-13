package com.francotte.homecontroller.feature.homeassistant

import com.francotte.homecontroller.core.model.EntityDetail
import com.francotte.homecontroller.core.model.EntityRealtimeEvent
import com.francotte.homecontroller.core.model.EntityStateChange
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
class EntityDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(repo: FakeHomeAssistantEntities, entityId: String = "light.a") =
        EntityDetailViewModel(entityId = entityId, homeAssistantEntities = repo)

    private fun TestScope.activeVm(repo: FakeHomeAssistantEntities, entityId: String = "light.a"): EntityDetailViewModel {
        val model = vm(repo, entityId)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} }
        return model
    }

    private fun lightDetail(isOn: Boolean = true, pct: Int = 30) =
        EntityDetail("light.a", "Lampe", "light", isOn, true, pct)

    @Test
    fun `chargement expose Content`() = runTest {
        val repo = FakeHomeAssistantEntities().apply { detail = lightDetail(isOn = true, pct = 30) }
        val model = activeVm(repo)
        advanceUntilIdle()
        val state = model.uiState.value as EntityDetailUiState.Content
        assertEquals("Lampe", state.friendlyName)
        assertTrue(state.isOn)
        assertEquals(30, state.brightnessPercent)
    }

    @Test
    fun `chargement en echec expose Error`() = runTest {
        val repo = FakeHomeAssistantEntities().apply { detailError = RuntimeException("boom") }
        val model = activeVm(repo)
        advanceUntilIdle()
        assertTrue(model.uiState.value is EntityDetailUiState.Error)
    }

    @Test
    fun `drag met a jour l affichage sans envoi`() = runTest {
        val repo = FakeHomeAssistantEntities().apply { detail = lightDetail(pct = 30) }
        val model = activeVm(repo)
        advanceUntilIdle()
        model.onBrightnessDrag(70)
        advanceUntilIdle()
        val state = model.uiState.value as EntityDetailUiState.Content
        assertEquals(70, state.brightnessPercent)
        assertTrue(repo.brightnessCalls.isEmpty())
    }

    @Test
    fun `commit positif envoie turn_on et optimiste allume`() = runTest {
        val repo = FakeHomeAssistantEntities().apply { detail = lightDetail(isOn = false, pct = 0) }
        val model = activeVm(repo)
        advanceUntilIdle()
        model.onBrightnessCommit(60)
        advanceUntilIdle()
        assertEquals("light.a" to 60, repo.brightnessCalls.single())
        val state = model.uiState.value as EntityDetailUiState.Content
        assertTrue(state.isOn)
        assertEquals(60, state.brightnessPercent)
    }

    @Test
    fun `commit a zero envoie et optimiste eteint`() = runTest {
        val repo = FakeHomeAssistantEntities().apply { detail = lightDetail(isOn = true, pct = 40) }
        val model = activeVm(repo)
        advanceUntilIdle()
        model.onBrightnessCommit(0)
        advanceUntilIdle()
        assertEquals("light.a" to 0, repo.brightnessCalls.single())
        val state = model.uiState.value as EntityDetailUiState.Content
        assertFalse(state.isOn)
    }

    @Test
    fun `echec de commit restaure l etat precedent`() = runTest {
        val repo = FakeHomeAssistantEntities().apply {
            detail = lightDetail(isOn = true, pct = 30)
            setBrightnessError = RuntimeException("réseau")
        }
        val model = activeVm(repo)
        advanceUntilIdle()
        model.onBrightnessCommit(80)
        advanceUntilIdle()
        val state = model.uiState.value as EntityDetailUiState.Content
        assertEquals(30, state.brightnessPercent)   // rollback
        assertTrue(state.isOn)
        assertNotNull(state.transientError)
    }

    @Test
    fun `changement temps reel applique hors drag`() = runTest {
        val repo = FakeHomeAssistantEntities().apply { detail = lightDetail(isOn = true, pct = 30) }
        val model = activeVm(repo)
        advanceUntilIdle()
        repo.emitRealtime(EntityRealtimeEvent.Changed(EntityStateChange("light.a", true, "on", 80)))
        advanceUntilIdle()
        val state = model.uiState.value as EntityDetailUiState.Content
        assertEquals(80, state.brightnessPercent)
    }

    @Test
    fun `changement temps reel ignore pendant le drag`() = runTest {
        val repo = FakeHomeAssistantEntities().apply { detail = lightDetail(isOn = true, pct = 30) }
        val model = activeVm(repo)
        advanceUntilIdle()
        model.onBrightnessDrag(50)
        repo.emitRealtime(EntityRealtimeEvent.Changed(EntityStateChange("light.a", true, "on", 80)))
        advanceUntilIdle()
        val state = model.uiState.value as EntityDetailUiState.Content
        assertEquals(50, state.brightnessPercent)   // le doigt gagne
    }

    @Test
    fun `resync recharge le detail`() = runTest {
        val repo = FakeHomeAssistantEntities().apply { detail = lightDetail(isOn = false, pct = 0) }
        val model = activeVm(repo)
        advanceUntilIdle()
        repo.detail = lightDetail(isOn = true, pct = 90)
        repo.emitRealtime(EntityRealtimeEvent.Resync)
        advanceUntilIdle()
        val state = model.uiState.value as EntityDetailUiState.Content
        assertEquals(90, state.brightnessPercent)
    }
}
