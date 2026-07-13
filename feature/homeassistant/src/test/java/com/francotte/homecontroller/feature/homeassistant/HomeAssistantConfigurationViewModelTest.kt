package com.francotte.homecontroller.feature.homeassistant

import com.francotte.homecontroller.core.model.HomeAssistantException
import com.francotte.homecontroller.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** L'écran de configuration : formulaire, test/sauvegarde, événement de sauvegarde. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeAssistantConfigurationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(config: FakeHomeAssistantConfiguration) = HomeAssistantConfigurationViewModel(config)

    @Test
    fun `la saisie met a jour le formulaire`() = runTest {
        val model = vm(FakeHomeAssistantConfiguration())
        model.onUrlChange("http://x:8123")
        model.onTokenChange("TOKEN")
        assertEquals("http://x:8123", model.form.value.url)
        assertEquals("TOKEN", model.form.value.token)
    }

    @Test
    fun `test and save reussi enregistre et emet savedEvents`() = runTest {
        val repo = FakeHomeAssistantConfiguration()
        val model = vm(repo)
        val saved = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.savedEvents.collect { saved.add(it) }
        }

        model.onUrlChange("http://192.168.1.20:8123")
        model.onTokenChange("TOKEN")
        model.onTestAndSave()
        advanceUntilIdle()

        assertNotNull(repo.credentialsFlow.value)   // config enregistrée
        assertEquals("http://192.168.1.20:8123", repo.credentialsFlow.value?.baseUrl)
        assertFalse(model.form.value.isTesting)
        assertNull(model.form.value.error)
        assertEquals(1, saved.size)
    }

    @Test
    fun `test and save echoue affiche l erreur sans emettre savedEvents`() = runTest {
        val repo = FakeHomeAssistantConfiguration().apply {
            testResult = Result.failure(HomeAssistantException.Unauthorized)
        }
        val model = vm(repo)
        val saved = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.savedEvents.collect { saved.add(it) }
        }

        model.onUrlChange("http://x:8123")
        model.onTokenChange("bad")
        model.onTestAndSave()
        advanceUntilIdle()

        assertNotNull(model.form.value.error)
        assertFalse(model.form.value.isTesting)
        assertTrue(saved.isEmpty())
        assertNull(repo.credentialsFlow.value)   // rien enregistré
    }
}
