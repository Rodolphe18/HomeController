package com.francotte.homecontroller.navigation

import androidx.navigation3.runtime.NavKey

data object HomeAssistantKey : NavKey

data object BtClassicKey : NavKey

data object ScanKey : NavKey

data class DeviceControlKey(val address: String) : NavKey

data class EntityDetailKey(val entityId: String) : NavKey
