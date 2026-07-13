package com.francotte.homecontroller.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.francotte.homecontroller.core.designsystem.AppIcons
import com.francotte.homecontroller.core.model.HomeAssistantEntity

/**
 * Carte d'un appareil Home Assistant : avatar coloré, nom, statut et interrupteur.
 * Composite « model-aware » (prend un [HomeAssistantEntity]) → vit dans :core:ui.
 *
 * [accentIndex] cycle l'accent (indigo → magenta → cyan) selon la position dans la liste :
 * l'avatar est toujours coloré, et allumer un 2e appareil donne une couleur de carte différente.
 */
@Composable
fun EntityCard(
    entity: HomeAssistantEntity,
    accentIndex: Int,
    onToggle: (String, Boolean) -> Unit,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val accent = when (accentIndex % 3) {
        0 -> RowAccent(colorScheme.primary, colorScheme.onPrimary, colorScheme.primaryContainer, colorScheme.onPrimaryContainer)
        1 -> RowAccent(colorScheme.tertiary, colorScheme.onTertiary, colorScheme.tertiaryContainer, colorScheme.onTertiaryContainer)
        else -> RowAccent(
            colorScheme.secondary,
            colorScheme.onSecondary,
            colorScheme.secondaryContainer,
            colorScheme.onSecondaryContainer
        )
    }
    // Icône selon le type : puce pour l'ESP32, ampoule pour les lumières, prise sinon.
    val icon = when {
        entity.entityId.contains("esp32", ignoreCase = true) -> AppIcons.Chip
        entity.domain == "light" -> AppIcons.LightbulbEmpty
        else -> AppIcons.Plug
    }
    val containerColor = if (entity.isOn) accent.container else colorScheme.surfaceContainerHigh
    val contentColor = if (entity.isOn) accent.onContainer else colorScheme.onSurface
    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(entity.entityId) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(accent.avatar),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = accent.onAvatar,
                    modifier = Modifier.size(32.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    entity.friendlyName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2
                )
                Text(
                    text = stringResource(
                        if (entity.isOn) R.string.core_ui_entity_state_on
                        else R.string.core_ui_entity_state_off
                    ),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Switch(
                checked = entity.isOn,
                onCheckedChange = { onToggle(entity.entityId, it) },
                modifier = Modifier.scale(1.2f),
                // Piste pleine sans contour extérieur, comme la maquette.
                colors = SwitchDefaults.colors(uncheckedBorderColor = Color.Transparent)
            )
        }
    }
}

/** Regroupe les 4 teintes d'un accent de carte (avatar + carte allumée). */
private data class RowAccent(
    val avatar: Color,
    val onAvatar: Color,
    val container: Color,
    val onContainer: Color
)
