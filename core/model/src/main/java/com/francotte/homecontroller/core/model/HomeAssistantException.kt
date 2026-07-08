package com.francotte.homecontroller.core.model

import java.io.IOException

/** Erreurs typées de la couche Home Assistant. */
sealed class HomeAssistantException(message: String) : IOException(message) {
    /** Jeton refusé (HTTP 401). */
    data object Unauthorized : HomeAssistantException("Jeton d'accès refusé")
    /** Hôte injoignable (erreur réseau). */
    data object Unreachable : HomeAssistantException("Home Assistant injoignable")
    /** Aucune configuration enregistrée. */
    data object NotConfigured : HomeAssistantException("Home Assistant non configuré")
    /** Autre échec. */
    data object Unknown : HomeAssistantException("Erreur inconnue")
}
