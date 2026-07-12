package com.francotte.homecontroller.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.francotte.homecontroller.core.designsystem.theme.IndigoBackdropBottom
import com.francotte.homecontroller.core.designsystem.theme.IndigoBackdropTop
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

private enum class TopTab(
    val label: String,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int = icon
) {
    HomeAssistant("Maison", AppIcons.HomeAssistant, AppIcons.HomeFilled),
    Ble("BLE", AppIcons.Ble),
    BtClassic("Classic", AppIcons.BtClassic)
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

    // Dégradé plein écran : il passe DERRIÈRE la status bar (edge-to-edge). Les Scaffolds
    // au-dessus sont transparents pour le laisser transparaître.
    val backdrop = Brush.verticalGradient(listOf(IndigoBackdropTop, IndigoBackdropBottom))
    Box(modifier = Modifier.fillMaxSize().background(backdrop)) {
        Scaffold(
            containerColor = Color.Transparent,
            // contentColor explicite : contentColorFor(Transparent) est indéfini (sinon texte noir).
            contentColor = MaterialTheme.colorScheme.onSurface,
            bottomBar = {
                NavigationBar {
                    TopTab.entries.forEach { tab ->
                        val isSelected = selected == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selected = tab },
                            icon = {
                                val iconRes = if (isSelected) tab.selectedIcon else tab.icon
                                Icon(painterResource(iconRes), contentDescription = tab.label)
                            },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
        ) { padding ->
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                // padding = insets système + hauteur de la barre du bas ; consumeWindowInsets
                // évite que les Scaffolds enfants ne réappliquent les mêmes insets (double-padding).
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
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
}
