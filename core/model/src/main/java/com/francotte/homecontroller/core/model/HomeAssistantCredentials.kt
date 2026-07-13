package com.francotte.homecontroller.core.model

/** Configuration de connexion à une instance Home Assistant. */

data class HomeAssistantCredentials(
    val baseUrl: String,   // ex. "http://192.168.1.20:8123"
    val token: String      // jeton d'accès longue durée
)
