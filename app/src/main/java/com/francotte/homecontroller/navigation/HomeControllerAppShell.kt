package com.francotte.homecontroller.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.francotte.homecontroller.core.designsystem.AppIcons
import com.francotte.homecontroller.feature.btclassic.BtClassicScanScreen
import com.francotte.homecontroller.feature.devicedetail.DeviceControlScreen
import com.francotte.homecontroller.feature.homeassistant.EntityDetailScreen
import com.francotte.homecontroller.feature.homeassistant.HomeAssistantScreen
import com.francotte.homecontroller.feature.btlowenergy.BleScanScreen

private enum class TopTab(val label: String, @DrawableRes val icon: Int) {
    HomeAssistant("Home Assistant", AppIcons.HomeAssistant),
    Ble("BLE", AppIcons.Ble),
    BtClassic("Bluetooth Classic", AppIcons.BtClassic)
}

@Composable
fun HomeControllerAppShell() {
    var selected by rememberSaveable { mutableStateOf(TopTab.HomeAssistant) }

    // Un back stack mémorisé par onglet ; la position de navigation persiste au changement d'onglet.
    val haBackStack = remember { mutableStateListOf<NavKey>(HomeAssistantKey) }
    val bleBackStack = remember { mutableStateListOf<NavKey>(BleScanKey) }
    val classicBackStack = remember { mutableStateListOf<NavKey>(BtClassicKey) }
    val backStack = when (selected) {
        TopTab.HomeAssistant -> haBackStack
        TopTab.Ble -> bleBackStack
        TopTab.BtClassic -> classicBackStack
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab },
                        icon = { Icon(painterResource(tab.icon), contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.padding(padding),
            entryDecorators = listOf(rememberViewModelStoreNavEntryDecorator()),
            entryProvider = entryProvider {
                entry<HomeAssistantKey> {
                    HomeAssistantScreen(onEntityClick = { id -> haBackStack.add(EntityDetailKey(id)) })
                }
                entry<EntityDetailKey> { key ->
                    EntityDetailScreen(
                        entityId = key.entityId,
                        onBack = { haBackStack.removeLastOrNull() }
                    )
                }
                entry<BleScanKey> {
                    BleScanScreen(onDeviceClick = { address -> bleBackStack.add(DeviceControlKey(address)) })
                }
                entry<DeviceControlKey> { key ->
                    DeviceControlScreen(
                        address = key.address,
                        onBack = { bleBackStack.removeLastOrNull() }
                    )
                }
                entry<BtClassicKey> {
                    BtClassicScanScreen()
                }
            }
        )
    }
}
