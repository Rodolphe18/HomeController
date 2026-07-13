package com.francotte.homecontroller.feature.homeassistant

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.francotte.homecontroller.core.data.HomeAssistantEntities
import com.francotte.homecontroller.core.domain.GetControllableEntitiesUseCase
import com.francotte.homecontroller.core.domain.ObserveEntityStatesUseCase
import com.francotte.homecontroller.core.domain.SetEntityStateUseCase
import com.francotte.homecontroller.core.model.EntityRealtimeEvent
import com.francotte.homecontroller.core.model.EntityStateChange
import com.francotte.homecontroller.core.model.HomeAssistantEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel de l'écran des entités : charge la liste, applique les toggles (optimistes) et
 * reflète le temps réel (WebSocket). Le WS ([realtimeGate]) vit tant que [uiState] est collecté
 * (WhileSubscribed). Ce VM n'existe que lorsqu'une config est présente (garanti par l'aiguilleur
 * [HomeAssistantViewModel]), d'où l'observation directe des états sans re-vérifier la config.
 */
@HiltViewModel
class HomeAssistantEntitiesViewModel @Inject constructor(
    private val homeAssistantEntities: HomeAssistantEntities
) : ViewModel() {

    /** `entities == null` = pas encore chargé (→ [EntitiesUiState.Loading]). */
    private data class InternalState(
        val entities: List<HomeAssistantEntity>? = null,
        val isRefreshing: Boolean = false,
        @param:StringRes val listError: Int? = null,
        @param:StringRes val transientError: Int? = null
    )

    private val internal = MutableStateFlow(InternalState())

    /**
     * WS **fondu** dans le pipeline : ses émissions (`Unit`) sont ignorées par [uiState] ; il n'est
     * là que pour lier la durée de vie du socket à l'abonnement de [uiState] (via `WhileSubscribed`).
     * Les effets utiles passent par les `onEach` qui mettent à jour [internal]. `onStart` émet une
     * première valeur pour que `combine` produise un état avant le premier event WS.
     */
    private val realtimeGate: Flow<Unit> = homeAssistantEntities
        .observeEntityStates()
        .onEach { event ->
            when (event) {
                EntityRealtimeEvent.Resync -> loadEntities()
                is EntityRealtimeEvent.Changed -> applyChange(event.change)
            }
        }
        .map { }
        .onStart { emit(Unit) }

    val uiState: StateFlow<EntitiesUiState> = combine(internal, realtimeGate) { s, _ ->
        if (s.entities == null) {
            EntitiesUiState.Loading
        } else {
            EntitiesUiState.Content(
                items = s.entities,
                isRefreshing = s.isRefreshing,
                listError = s.listError,
                transientError = s.transientError
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntitiesUiState.Loading)

    init {
        // Chargement initial ; le WS resynchronisera à l'abonnement.
        viewModelScope.launch { loadEntities() }
    }

    fun onRefresh() {
        if (internal.value.entities == null) return
        internal.update { it.copy(isRefreshing = true, listError = null) }
        viewModelScope.launch { loadEntities() }
    }

    fun onToggle(entityId: String, on: Boolean) {
        val items = internal.value.entities ?: return
        if (items.none { it.entityId == entityId }) return
        // Optimiste : bascule immédiate.
        internal.update { s ->
            s.copy(
                entities = s.entities?.map { if (it.entityId == entityId) it.copy(isOn = on) else it },
                transientError = null
            )
        }
        viewModelScope.launch {
            try {
                homeAssistantEntities.setEntityState(entityId, on)
                // Pas de rechargement immédiat : les appareils Tapo passent par le cloud et mettent
                // un délai à refléter le nouvel état. L'état optimiste tient ; le push WS confirmera,
                // et le pull-to-refresh réconcilie à la demande.
            } catch (t: Throwable) {
                internal.update { s ->
                    s.copy(
                        entities = s.entities?.map { if (it.entityId == entityId) it.copy(isOn = !on) else it },
                        transientError = t.toMessageRes()
                    )
                }
            }
        }
    }

    private suspend fun loadEntities() {
        homeAssistantEntities.getControllableEntities().fold(
            onSuccess = { items ->
                internal.update { it.copy(entities = items, isRefreshing = false, listError = null) }
            },
            onFailure = { t ->
                internal.update {
                    it.copy(
                        entities = it.entities ?: emptyList(),   // reste sur Content(vide) plutôt que Loading
                        isRefreshing = false,
                        listError = t.toMessageRes()
                    )
                }
            }
        )
    }

    private fun applyChange(change: EntityStateChange) {
        internal.update { s ->
            val items = s.entities ?: return@update s
            if (items.none { it.entityId == change.entityId }) return@update s
            s.copy(
                entities = items.map {
                    if (it.entityId == change.entityId) {
                        it.copy(isOn = change.isOn, rawState = change.rawState)
                    } else {
                        it
                    }
                }
            )
        }
    }
}
