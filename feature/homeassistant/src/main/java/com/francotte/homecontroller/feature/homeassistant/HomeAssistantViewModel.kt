package com.francotte.homecontroller.feature.homeassistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.francotte.homecontroller.core.domain.GetControllableEntitiesUseCase
import com.francotte.homecontroller.core.domain.ObserveConfigUseCase
import com.francotte.homecontroller.core.domain.SaveConfigUseCase
import com.francotte.homecontroller.core.domain.SetEntityStateUseCase
import com.francotte.homecontroller.core.domain.TestConnectionUseCase
import com.francotte.homecontroller.core.model.HomeAssistantConfig
import com.francotte.homecontroller.core.model.HomeAssistantException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeAssistantViewModel @Inject constructor(
    private val observeConfig: ObserveConfigUseCase,
    private val saveConfig: SaveConfigUseCase,
    private val testConnection: TestConnectionUseCase,
    private val getEntities: GetControllableEntitiesUseCase,
    private val setEntityState: SetEntityStateUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeAssistantUiState>(HomeAssistantUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private var form = ConfigFormState()

    init {
        viewModelScope.launch {
            if (observeConfig().first() != null) loadEntities() else showForm()
        }
    }

    fun onUrlChange(value: String) { form = form.copy(url = value); syncForm() }
    fun onTokenChange(value: String) { form = form.copy(token = value); syncForm() }

    fun onTestAndSave() {
        val candidate = HomeAssistantConfig(form.url.trim(), form.token.trim())
        form = form.copy(isTesting = true, error = null); syncForm()
        viewModelScope.launch {
            testConnection(candidate).fold(
                onSuccess = {
                    saveConfig(candidate)
                    loadEntities()
                },
                onFailure = {
                    form = form.copy(isTesting = false, error = it.toMessage())
                    syncForm()
                }
            )
        }
    }

    fun onEditConfig() { showForm() }

    fun onRefresh() {
        val current = _uiState.value
        if (current is HomeAssistantUiState.Entities) {
            _uiState.value = current.copy(isRefreshing = true, listError = null)
            viewModelScope.launch { loadEntities(isRefresh = true) }
        }
    }

    fun onToggle(entityId: String, on: Boolean) {
        val current = _uiState.value as? HomeAssistantUiState.Entities ?: return
        // Optimiste : bascule immédiate.
        _uiState.value = current.copy(
            items = current.items.map { if (it.entityId == entityId) it.copy(isOn = on) else it },
            transientError = null
        )
        viewModelScope.launch {
            try {
                setEntityState(entityId, on)
                // Pas de rechargement immédiat : les appareils Tapo passent par le cloud et
                // mettent un délai à refléter le nouvel état. Un GET /states immédiat renverrait
                // l'ancien état et ferait "sauter" le toggle. L'état optimiste est conservé ;
                // le pull-to-refresh réconcilie à la demande.
            } catch (t: Throwable) {
                val reverted = _uiState.value as? HomeAssistantUiState.Entities ?: return@launch
                _uiState.value = reverted.copy(
                    items = reverted.items.map { if (it.entityId == entityId) it.copy(isOn = !on) else it },
                    transientError = t.toMessage()
                )
            }
        }
    }

    private fun showForm() { _uiState.value = HomeAssistantUiState.Unconfigured(form) }
    private fun syncForm() {
        if (_uiState.value is HomeAssistantUiState.Unconfigured) {
            _uiState.value = HomeAssistantUiState.Unconfigured(form)
        }
    }

    private suspend fun loadEntities(isRefresh: Boolean = false) {
        try {
            val items = getEntities()
            _uiState.value = HomeAssistantUiState.Entities(items = items, isRefreshing = false)
        } catch (t: Throwable) {
            _uiState.value = HomeAssistantUiState.Entities(
                items = (_uiState.value as? HomeAssistantUiState.Entities)?.items ?: emptyList(),
                isRefreshing = false,
                listError = t.toMessage()
            )
        }
    }
}

/** Message utilisateur pour une erreur (typée ou générique). */
internal fun Throwable.toMessage(): String = when (this) {
    is HomeAssistantException.Unauthorized -> "Jeton refusé (401). Vérifie le jeton d'accès."
    is HomeAssistantException.Unreachable -> "Home Assistant injoignable à cette adresse."
    is HomeAssistantException.NotConfigured -> "Home Assistant n'est pas configuré."
    else -> message ?: "Erreur inconnue."
}
