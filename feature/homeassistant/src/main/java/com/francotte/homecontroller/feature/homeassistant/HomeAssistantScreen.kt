package com.francotte.homecontroller.feature.homeassistant

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.francotte.homecontroller.core.designsystem.AppIcons
import com.francotte.homecontroller.core.designsystem.component.LoadingState
import com.francotte.homecontroller.core.designsystem.component.StatusScreen
import com.francotte.homecontroller.core.model.HomeAssistantEntity

@Composable
fun HomeAssistantScreen(
    onEntityClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeAssistantViewModel = hiltViewModel()
) {
    // Collecte lifecycle-aware : STARTED (écran visible) + 5 s de grâce côté ViewModel
    // (WhileSubscribed) pilotent à la fois l'affichage et la vie de la connexion WebSocket.
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Transparent : le dégradé plein écran est peint par le shell (il passe derrière la status bar).
    // contentColor explicite car contentColorFor(Transparent) est indéfini → sinon le texte vire au noir.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) { padding ->
        when (val state = uiState) {
            HomeAssistantUiState.Loading ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                ) {
                    LoadingState(label = "Chargement…")
                }

            // Le formulaire porte son propre Scaffold (top app bar) : on lui passe l'inset externe.
            is HomeAssistantUiState.Unconfigured -> ConfigForm(
                form = state.form,
                canCancel = state.canCancel,
                outerPadding = padding,
                onUrlChange = viewModel::onUrlChange,
                onTokenChange = viewModel::onTokenChange,
                onSubmit = viewModel::onTestAndSave,
                onCancel = viewModel::onCancelEdit
            )

            is HomeAssistantUiState.Entities ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(start = 16.dp, end = 16.dp, top = 18.dp)
                ) {
                    EntitiesContent(
                        state = state,
                        onRefresh = viewModel::onRefresh,
                        onToggle = viewModel::onToggle,
                        onEditConfig = viewModel::onEditConfig,
                        onEntityClick = onEntityClick
                    )
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigForm(
    form: ConfigFormState,
    canCancel: Boolean,
    outerPadding: PaddingValues,
    onUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    // Le back matériel/gestuel annule l'édition — mais seulement s'il y a une liste où revenir.
    if (canCancel) {
        BackHandler(onBack = onCancel)
    }
    Scaffold(
        modifier = Modifier.padding(outerPadding),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            TopAppBar(
                title = { Text("Connexion à Home Assistant") },
                navigationIcon = {
                    // Bouton fermer à gauche, sur la même ligne que le titre (édition uniquement).
                    if (canCancel) {
                        IconButton(onClick = onCancel) {
                            Icon(painterResource(AppIcons.Close), contentDescription = "Close")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Saisissez l'adresse locale de votre instance ainsi que le jeton d'accès longue durée",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Champs regroupés dans une carte.
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ConfigField(
                        value = form.url,
                        onValueChange = onUrlChange,
                        label = "Adresse locale",
                        placeholder = "http://192.168.1.x:8123",
                        leadingIcon = AppIcons.Link,
                        keyboardType = KeyboardType.Uri,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ConfigField(
                        value = form.token,
                        onValueChange = onTokenChange,
                        label = "Jeton d'accès longue durée",
                        placeholder = "Jeton d'accès longue durée",
                        leadingIcon = AppIcons.Lock,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Erreur dans un conteneur dédié plutôt qu'un simple texte rouge.
            form.error?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(AppIcons.Warning),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Action principale, pleine largeur, avec indicateur pendant le test.
            Button(
                onClick = onSubmit,
                enabled = !form.isTesting,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (form.isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Testing…")
                } else {
                    Text("Test & save")
                }
            }
        }
    }
}

/**
 * Champ de saisie custom bâti sur [BasicTextField] : label au-dessus, fond arrondi, icône de
 * tête et bordure qui s'illumine au focus. On reprend le contrôle du rendu via `decorationBox`,
 * là où un OutlinedTextField imposerait son propre style.
 */
@Composable
private fun ConfigField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    @DrawableRes leadingIcon: Int,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    var focused by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val accent = if (focused) cs.primary else cs.onSurfaceVariant
    val borderColor = if (focused) cs.primary else cs.outlineVariant
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = accent
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
            cursorBrush = SolidColor(cs.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .background(cs.surface)
                        .border(
                            width = if (focused) 2.dp else 1.dp,
                            color = borderColor,
                            shape = MaterialTheme.shapes.medium
                        )
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        painter = painterResource(leadingIcon),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = cs.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        innerTextField()
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntitiesContent(
    state: HomeAssistantUiState.Entities,
    onRefresh: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onEditConfig: () -> Unit,
    onEntityClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "Home Assistant",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        // Pastille bleue (primaryContainer) autour de l'icône Settings.
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onEditConfig),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(AppIcons.SettingsFilled),
                contentDescription = "Configuration",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(30.dp)
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    state.transientError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    state.listError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Spacer(Modifier.height(8.dp))

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        if (state.items.isEmpty() && state.listError == null) {
            StatusScreen(
                icon = AppIcons.LightbulbEmpty,
                title = "Aucun appareil",
                description = "Aucune lumière ni prise commandable n'a été trouvée sur votre instance Home Assistant."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(state.items, key = { _, it -> it.entityId }) { index, entity ->
                    EntityRow(entity, index, onToggle, onEntityClick)
                }
            }
        }
    }
}

@Composable
private fun EntityRow(
    entity: HomeAssistantEntity,
    accentIndex: Int,
    onToggle: (String, Boolean) -> Unit,
    onClick: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    // Chaque appareil reçoit un accent distinct, cyclé par sa position dans la liste :
    // indigo (primaire) → magenta (tertiaire) → cyan (secondaire). Ainsi l'avatar est
    // toujours coloré, et allumer un 2e appareil donne une couleur de carte différente.
    val accent = when (accentIndex % 3) {
        0 -> RowAccent(cs.primary, cs.onPrimary, cs.primaryContainer, cs.onPrimaryContainer)
        1 -> RowAccent(cs.tertiary, cs.onTertiary, cs.tertiaryContainer, cs.onTertiaryContainer)
        else -> RowAccent(
            cs.secondary,
            cs.onSecondary,
            cs.secondaryContainer,
            cs.onSecondaryContainer
        )
    }
    // Icône selon le type : puce pour l'ESP32, ampoule pour les lumières, prise sinon.
    val icon = when {
        entity.entityId.contains("esp32", ignoreCase = true) -> AppIcons.Chip
        entity.domain == "light" -> AppIcons.LightbulbEmpty
        else -> AppIcons.Plug
    }
    val containerColor = if (entity.isOn) accent.container else cs.surfaceContainerHigh
    val contentColor = if (entity.isOn) accent.onContainer else cs.onSurface
    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
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
                    text = if (entity.isOn) "Allumée" else "Éteinte",
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

/** Regroupe les 4 teintes d'un accent de ligne (avatar + carte allumée). */
private data class RowAccent(
    val avatar: Color,
    val onAvatar: Color,
    val container: Color,
    val onContainer: Color
)
