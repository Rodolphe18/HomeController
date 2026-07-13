package com.francotte.homecontroller.feature.homeassistant

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.francotte.homecontroller.core.data.HomeAssistantEntities
import com.francotte.homecontroller.core.model.EntityDetail
import com.francotte.homecontroller.core.model.EntityRealtimeEvent
import com.francotte.homecontroller.core.model.EntityStateChange
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
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

@HiltViewModel(assistedFactory = EntityDetailViewModel.Factory::class)
class EntityDetailViewModel @AssistedInject constructor(
    @Assisted private val entityId: String,
    private val homeAssistantEntities: HomeAssistantEntities
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(entityId: String): EntityDetailViewModel
    }

    private data class Internal(
        val detail: EntityDetail? = null,     // null = pas encore chargé (→ Loading)
        val draggingPercent: Int? = null,     // non-null = glissement en cours (affichage)
        @param:StringRes val loadError: Int? = null,
        @param:StringRes val transientError: Int? = null
    )

    private val internal = MutableStateFlow(Internal())

    // Temps réel filtré sur cette entité, fondu dans le pipeline pour vivre le temps de l'écran.
    private val realtimeGate: Flow<Unit> = homeAssistantEntities.observeEntityStates()
        .onEach { event ->
            when (event) {
                EntityRealtimeEvent.Resync -> loadDetail()
                is EntityRealtimeEvent.Changed ->
                    if (event.change.entityId == entityId) applyChange(event.change)
            }
        }
        .map { }
        .onStart { emit(Unit) }

    val uiState: StateFlow<EntityDetailUiState> = combine(internal, realtimeGate) { s, _ ->
        when {
            s.detail == null && s.loadError != null -> EntityDetailUiState.Error(s.loadError)
            s.detail == null -> EntityDetailUiState.Loading
            else -> EntityDetailUiState.Content(
                friendlyName = s.detail.friendlyName,
                isOn = s.detail.isOn,
                supportsBrightness = s.detail.supportsBrightness,
                brightnessPercent = s.draggingPercent ?: s.detail.brightnessPercent,
                transientError = s.transientError
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntityDetailUiState.Loading)

    init {
        viewModelScope.launch { loadDetail() }
    }

    fun onBrightnessDrag(percent: Int) =
        internal.update { it.copy(draggingPercent = percent.coerceIn(0, 100)) }

    fun onBrightnessCommit(percent: Int) {
        val p = percent.coerceIn(0, 100)
        val previous = internal.value.detail ?: return
        internal.update {
            it.copy(
                detail = it.detail?.copy(isOn = p > 0, brightnessPercent = p),
                draggingPercent = null,
                transientError = null
            )
        }
        viewModelScope.launch {
            try {
                homeAssistantEntities.setBrightness(entityId, p)
            } catch (t: Throwable) {
                internal.update { it.copy(detail = previous, transientError = t.toMessageRes()) }
            }
        }
    }

    fun onToggle(on: Boolean) {
        val previous = internal.value.detail ?: return
        internal.update { it.copy(detail = it.detail?.copy(isOn = on), transientError = null) }
        viewModelScope.launch {
            try {
                homeAssistantEntities.setEntityState(entityId, on)
            } catch (t: Throwable) {
                internal.update { it.copy(detail = previous, transientError = t.toMessageRes()) }
            }
        }
    }

    private suspend fun loadDetail() {
        try {
            val d = homeAssistantEntities.getEntityDetail(entityId)
            internal.update { it.copy(detail = d, loadError = null) }
        } catch (t: Throwable) {
            internal.update { it.copy(loadError = t.toMessageRes()) }
        }
    }

    private fun applyChange(change: EntityStateChange) {
        internal.update { s ->
            if (s.draggingPercent != null) return@update s   // ne pas écraser le doigt
            val d = s.detail ?: return@update s
            s.copy(
                detail = d.copy(
                    isOn = change.isOn,
                    brightnessPercent = change.brightnessPercent ?: d.brightnessPercent
                )
            )
        }
    }
}
