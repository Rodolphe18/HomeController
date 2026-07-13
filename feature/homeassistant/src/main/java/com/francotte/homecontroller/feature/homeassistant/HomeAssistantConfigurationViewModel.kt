package com.francotte.homecontroller.feature.homeassistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.francotte.homecontroller.core.data.HomeAssistantConfiguration
import com.francotte.homecontroller.core.model.HomeAssistantCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel de l'écran de configuration : gère le formulaire (URL + jeton), teste la connexion
 * puis enregistre. À la sauvegarde réussie il émet [savedEvents] ; l'aiguilleur
 * ([HomeAssistantViewModel]) écoute cet événement pour quitter le formulaire et revenir à la liste.
 */
@HiltViewModel
class HomeAssistantConfigurationViewModel @Inject constructor(
    private val homeAssistantConfiguration: HomeAssistantConfiguration
) : ViewModel() {

    private val _form = MutableStateFlow(ConfigurationFormState())
    val form: StateFlow<ConfigurationFormState> = _form.asStateFlow()

    // Événement one-shot : la config a été enregistrée → l'aiguilleur peut fermer le formulaire.
    private val _savedEvents = Channel<Unit>(Channel.BUFFERED)
    val savedEvents: Flow<Unit> = _savedEvents.receiveAsFlow()

    fun onUrlChange(value: String) = _form.update { it.copy(url = value) }
    fun onTokenChange(value: String) = _form.update { it.copy(token = value) }

    private suspend fun saveConfiguration(credentials: HomeAssistantCredentials) {
        homeAssistantConfiguration.saveConfiguration(credentials)
    }

    fun onTestAndSave() {
        val form = _form.value
        val credentials = HomeAssistantCredentials(form.url.trim(), form.token.trim())
        _form.update { it.copy(isTesting = true, error = null) }
        viewModelScope.launch {
            homeAssistantConfiguration.testConnection(credentials).fold(
                onSuccess = {
                    saveConfiguration(credentials)
                    _form.update { it.copy(isTesting = false) }
                    _savedEvents.send(Unit)
                },
                onFailure = { error ->
                    _form.update { it.copy(isTesting = false, error = error.toMessage()) }
                }
            )
        }
    }
}
