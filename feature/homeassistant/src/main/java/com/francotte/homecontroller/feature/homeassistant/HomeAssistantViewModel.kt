package com.francotte.homecontroller.feature.homeassistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeAssistantViewModel @Inject constructor(
    private val observeConfig: ObserveConfigUseCase,
    private val saveConfig: SaveConfigUseCase,
    private val testConnection: TestConnectionUseCase,
    private val getEntities: GetControllableEntitiesUseCase,
    private val setEntityState: SetEntityStateUseCase,
    private val observeEntityStates: ObserveEntityStatesUseCase
) : ViewModel() {

    /**
     * Source de vérité interne, mutée par les actions (saisie, toggle, refresh) et par le
     * temps réel. `entities == null` = pas encore chargé (→ Loading).
     */
    private data class InternalState(
        val form: ConfigFormState = ConfigFormState(),
        val editing: Boolean = false,
        val entities: List<HomeAssistantEntity>? = null,
        val isRefreshing: Boolean = false,
        val listError: String? = null,
        val transientError: String? = null
    )

    private val internal = MutableStateFlow(InternalState())

    /**
     * Le WebSocket, **fondu** dans le pipeline d'état : `flatMapLatest` sur la config n'ouvre le
     * socket que lorsqu'on est configuré (sinon `emptyFlow`, pour éviter une boucle de reconnexion
     * `NotConfigured`). Ses émissions (`Unit`) sont ignorées par [uiState] ; il n'est là que pour
     * **lier la durée de vie du socket à l'abonnement de [uiState]** (via `WhileSubscribed`). Les
     * effets utiles passent par les `onEach` qui mettent à jour [internal]. `onStart` émet une
     * première valeur pour que `combine` puisse produire un état avant le premier event WS.
     */
    private val realtimeGate: Flow<Unit> = observeConfig()
        .flatMapLatest { config ->
            if (config == null) {
                emptyFlow()
            } else {
                observeEntityStates().onEach { event ->
                    when (event) {
                        EntityRealtimeEvent.Resync -> loadEntities()
                        is EntityRealtimeEvent.Changed -> applyChange(event.change)
                    }
                }.map { }
            }
        }
        .onStart { emit(Unit) }

    /**
     * État exposé, en `WhileSubscribed(5 s)` : le socket WS (via [realtimeGate]) vit tant qu'un
     * collecteur lifecycle-aware est présent, avec 5 s de grâce pour traverser les rotations sans
     * rouvrir la connexion. Collecté par l'écran avec `collectAsStateWithLifecycle`.
     */
    val uiState: StateFlow<HomeAssistantUiState> = combine(
        observeConfig(),
        internal,
        realtimeGate
    ) { config, s, _ ->
        when {
            config == null || s.editing -> HomeAssistantUiState.Unconfigured(
                form = s.form,
                // Annulable seulement s'il y a déjà une config à laquelle revenir (édition).
                canCancel = config != null
            )
            s.entities == null -> HomeAssistantUiState.Loading
            else -> HomeAssistantUiState.Entities(
                items = s.entities,
                isRefreshing = s.isRefreshing,
                listError = s.listError,
                transientError = s.transientError
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeAssistantUiState.Loading)

    init {
        // Contenu immédiat à l'ouverture si déjà configuré ; le WS resynchronisera à l'abonnement.
        viewModelScope.launch {
            if (observeConfig().first() != null) loadEntities()
        }
    }

    fun onUrlChange(value: String) = internal.update { it.copy(form = it.form.copy(url = value)) }
    fun onTokenChange(value: String) = internal.update { it.copy(form = it.form.copy(token = value)) }

    fun onTestAndSave() {
        val form = internal.value.form
        val candidate = HomeAssistantConfig(form.url.trim(), form.token.trim())
        internal.update { it.copy(form = it.form.copy(isTesting = true, error = null)) }
        viewModelScope.launch {
            testConnection(candidate).fold(
                onSuccess = {
                    saveConfig(candidate)   // la config émet → sortie du formulaire
                    internal.update { it.copy(editing = false, form = it.form.copy(isTesting = false)) }
                    loadEntities()
                },
                onFailure = { error ->
                    internal.update { it.copy(form = it.form.copy(isTesting = false, error = error.toMessage())) }
                }
            )
        }
    }

    fun onEditConfig() = internal.update { it.copy(editing = true) }

    /** Annule l'édition et revient à la liste (sans effet si aucune config n'existe encore). */
    fun onCancelEdit() = internal.update { it.copy(editing = false, form = ConfigFormState()) }

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
                setEntityState(entityId, on)
                // Pas de rechargement immédiat : les appareils Tapo passent par le cloud et mettent
                // un délai à refléter le nouvel état. L'état optimiste tient ; le push WS confirmera,
                // et le pull-to-refresh réconcilie à la demande.
            } catch (t: Throwable) {
                internal.update { s ->
                    s.copy(
                        entities = s.entities?.map { if (it.entityId == entityId) it.copy(isOn = !on) else it },
                        transientError = t.toMessage()
                    )
                }
            }
        }
    }

    private suspend fun loadEntities() {
        try {
            val items = getEntities()
            internal.update { it.copy(entities = items, isRefreshing = false, listError = null) }
        } catch (t: Throwable) {
            internal.update {
                it.copy(
                    entities = it.entities ?: emptyList(),   // reste sur Entities(vide) plutôt que Loading
                    isRefreshing = false,
                    listError = t.toMessage()
                )
            }
        }
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

/** Message utilisateur pour une erreur (typée ou générique). */
internal fun Throwable.toMessage(): String = when (this) {
    is HomeAssistantException.Unauthorized -> "Token rejected. Check your access token."
    is HomeAssistantException.Unreachable -> "Home Assistant unreachable. Make sure your device's Wi-Fi is on."
    is HomeAssistantException.NotConfigured -> "Home Assistant doesn't seem to be configured."
    else -> message ?: "Unknown error."
}
