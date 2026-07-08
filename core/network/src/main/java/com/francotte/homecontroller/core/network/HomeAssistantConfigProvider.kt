package com.francotte.homecontroller.core.network

import com.francotte.homecontroller.core.model.HomeAssistantConfig

/**
 * Fournit, de façon synchrone, la configuration HA enregistrée à l'interceptor.
 * L'implémentation vit dans `:core:data` (adossée au store chiffré).
 */
interface HomeAssistantConfigProvider {
    fun current(): HomeAssistantConfig?
}
