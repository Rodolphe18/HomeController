package com.francotte.homecontroller.feature.homeassistant

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.francotte.homecontroller.core.designsystem.AppIcons
import com.francotte.homecontroller.core.designsystem.component.LabeledTextField

/**
 * Écran de configuration Home Assistant. Il gère son propre [HomeAssistantConfigurationViewModel]
 * (formulaire + test/sauvegarde). À la sauvegarde réussie ([savedEvents]), il notifie l'aiguilleur
 * via [onConfigurationSaved] pour revenir à la liste.
 *
 * @param canCancel true en édition (une config existe déjà) : affiche le bouton fermer + back.
 * @param onCancel annule l'édition (géré par l'aiguilleur).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeAssistantConfigurationScreen(
    canCancel: Boolean,
    outerPadding: PaddingValues,
    onCancel: () -> Unit,
    onConfigurationSaved: () -> Unit,
    viewModel: HomeAssistantConfigurationViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsStateWithLifecycle()

    // La sauvegarde réussie fait quitter le formulaire (l'aiguilleur repasse sur la liste).
    LaunchedEffect(viewModel, onConfigurationSaved) {
        viewModel.savedEvents.collect { onConfigurationSaved() }
    }

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
                    LabeledTextField(
                        value = form.url,
                        onValueChange = viewModel::onUrlChange,
                        label = "Adresse locale",
                        placeholder = "http://192.168.1.x:8123",
                        leadingIcon = AppIcons.Link,
                        keyboardType = KeyboardType.Uri,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LabeledTextField(
                        value = form.token,
                        onValueChange = viewModel::onTokenChange,
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
                onClick = viewModel::onTestAndSave,
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
                    Text("En cours de test…")
                } else {
                    Text("Sauvegarder")
                }
            }
        }
    }
}
