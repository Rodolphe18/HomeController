package com.francotte.homecontroller.core.model

/** Un changement d'état d'entité, issu du flux temps réel. */
data class EntityStateChange(
    val entityId: String,
    val isOn: Boolean,
    val rawState: String,
    val brightnessPercent: Int? = null   // null = event sans info luminosité
)

/** Événement du flux temps réel Home Assistant. */
sealed interface EntityRealtimeEvent {
    /** (Ré)abonnement confirmé → resynchroniser via un rechargement REST. */
    data object Resync : EntityRealtimeEvent
    data class Changed(val change: EntityStateChange) : EntityRealtimeEvent
}
