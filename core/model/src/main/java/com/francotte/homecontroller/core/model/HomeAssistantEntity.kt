package com.francotte.homecontroller.core.model

/**
 * Une entité Home Assistant commandable (light/switch), vue par l'UI.
 *
 * @property entityId    identifiant HA, ex. "light.salon"
 * @property domain      préfixe avant le "." : "light" ou "switch"
 * @property friendlyName nom lisible (attribut friendly_name, sinon entityId)
 * @property isOn        true si l'état HA vaut "on"
 * @property rawState    état brut renvoyé par HA (ex. "on", "off", "unavailable")
 */

data class HomeAssistantEntity(
    val entityId: String,
    val domain: String,
    val friendlyName: String,
    val isOn: Boolean,
    val rawState: String
)
