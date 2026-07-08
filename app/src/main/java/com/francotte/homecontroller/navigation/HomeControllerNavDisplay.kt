package com.francotte.homecontroller.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.francotte.homecontroller.presentation.control.DeviceControlScreen
import com.francotte.homecontroller.presentation.scan.ScanScreen

@Composable
fun HomeControllerNavDisplay() {
    val backStack = remember { mutableStateListOf<NavKey>(ScanKey) }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<ScanKey> {
                ScanScreen(
                    onDeviceClick = { address -> backStack.add(DeviceControlKey(address)) }
                )
            }
            entry<DeviceControlKey> { key ->
                DeviceControlScreen(
                    address = key.address,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}
