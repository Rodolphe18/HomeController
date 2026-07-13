package com.francotte.homecontroller.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.francotte.homecontroller.core.model.BtClassicDevice

/**
 * Carte d'un appareil Bluetooth Classic (nom, adresse, RSSI, badge « Appairé »).
 * Composite « model-aware » (prend un [BtClassicDevice]) → vit dans :core:ui.
 */
@Composable
fun BtClassicDeviceCard(
    device: BtClassicDevice,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.name ?: stringResource(R.string.core_ui_device_unknown),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(device.address, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = device.rssi?.let { stringResource(R.string.core_ui_rssi_dbm, it) }
                        ?: stringResource(R.string.core_ui_rssi_unknown),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (device.bonded) {
                Text(
                    text = stringResource(R.string.core_ui_paired),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
