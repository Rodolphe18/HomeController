package com.francotte.homecontroller.navigation

import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavKey

data object HomeAssistantKey : NavKey

data object BtClassicKey : NavKey

data object ScanKey : NavKey

@Immutable
data class DeviceControlKey(val address: String) : NavKey

@Immutable
data class EntityDetailKey(val entityId: String) : NavKey
