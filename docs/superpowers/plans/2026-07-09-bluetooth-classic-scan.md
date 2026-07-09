# Scan Bluetooth Classic — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter un scan Bluetooth Classic (découverte + liste) dans un 3e onglet, symétrique du scan BLE ; renommer l'onglet BLE ; remplacer material-icons par des icônes Phosphor importées en vector drawables.

**Architecture:** `:core:model` (+BtClassicDevice), `:core:bluetooth` (+BtClassicScanner/impl Android via découverte), `:feature:btclassic` (écran + VM Hilt, cycle une-passe + relance manuelle), `:core:designsystem` (icônes Phosphor + AppIcons), `:app` (renommage onglet + 3e onglet, migration icônes).

**Tech Stack:** Kotlin 2.2.21, AGP 9.2.1 (Kotlin intégré), KSP 2.3.9, Hilt 2.60.1, Compose (BOM 2026.02.01), Nav3 1.0.1, androidx.core 1.19.0.

**Spec :** `docs/superpowers/specs/2026-07-09-bluetooth-classic-scan-design.md`

> ⚠️ **Commits :** l'utilisateur commite lui-même. **Ne pas committer.** Les étapes « Commit » indiquent le contenu logique.

> ⚠️ **Chaîne AGP 9 :** modules Android = `com.android.library`/`application` (+ `kotlin.compose`, `hilt`, `ksp` au besoin). **Jamais** de plugin `kotlin.android`.

> ⚠️ **Nommage :** préfixe explicite `BtClassic`. Package feature : `com.francotte.homecontroller.feature.btclassic`.

---

## Task 1 : `:core:model` — BtClassicDevice

**Files:**
- Create: `core/model/src/main/java/com/francotte/homecontroller/core/model/BtClassicDevice.kt`

- [ ] **Step 1 : Créer le modèle**

```kotlin
package com.francotte.homecontroller.core.model

/**
 * Un appareil Bluetooth Classic (BR/EDR) détecté lors d'une découverte.
 *
 * @property address MAC, identifiant unique
 * @property name    nom diffusé, ou null
 * @property rssi    force du signal si fournie (EXTRA_RSSI), sinon null
 * @property bonded  true si l'appareil est déjà appairé (BOND_BONDED)
 */
data class BtClassicDevice(
    val address: String,
    val name: String?,
    val rssi: Int?,
    val bonded: Boolean
)
```

- [ ] **Step 2 : Vérifier la compilation**

Run: `./gradlew :core:model:compileKotlin -q`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3 : Commit (utilisateur)** — modèle BtClassicDevice.

---

## Task 2 : `:core:bluetooth` — scanner Classic (découverte)

**Files:**
- Modify: `core/bluetooth/build.gradle.kts`
- Create: `core/bluetooth/src/main/java/com/francotte/homecontroller/core/bluetooth/BtClassicScanner.kt`
- Create: `core/bluetooth/src/main/java/com/francotte/homecontroller/core/bluetooth/BtClassicScanException.kt`
- Create: `core/bluetooth/src/main/java/com/francotte/homecontroller/core/bluetooth/AndroidBtClassicScanner.kt`
- Modify: `core/bluetooth/src/main/java/com/francotte/homecontroller/core/bluetooth/BluetoothModule.kt`

- [ ] **Step 1 : Ajouter la dépendance androidx.core**

Dans `core/bluetooth/build.gradle.kts`, dans `dependencies { … }`, ajouter :

```kotlin
    implementation(libs.androidx.core.ktx)
```

- [ ] **Step 2 : Interface publique**

`BtClassicScanner.kt` :

```kotlin
package com.francotte.homecontroller.core.bluetooth

import com.francotte.homecontroller.core.model.BtClassicDevice
import kotlinx.coroutines.flow.Flow

/**
 * Contrat de découverte Bluetooth Classic. Le [Flow] est FROID : la découverte
 * démarre à la souscription, fait UNE passe (~12 s) puis le Flow se termine ;
 * l'annulation arrête la découverte. Chaque émission = liste courante des
 * appareils détectés (dédupliquée par adresse MAC).
 */
interface BtClassicScanner {
    fun scan(): Flow<List<BtClassicDevice>>
}
```

- [ ] **Step 3 : Exception typée**

`BtClassicScanException.kt` :

```kotlin
package com.francotte.homecontroller.core.bluetooth

/** Erreur de découverte Bluetooth Classic (adaptateur indisponible, démarrage impossible). */
class BtClassicScanException(message: String) : Exception(message)
```

- [ ] **Step 4 : Impl Android (BroadcastReceiver + startDiscovery)**

`AndroidBtClassicScanner.kt` :

```kotlin
package com.francotte.homecontroller.core.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.francotte.homecontroller.core.model.BtClassicDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

internal class AndroidBtClassicScanner @Inject constructor(
    @ApplicationContext private val context: Context
) : BtClassicScanner {

    private val adapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    override fun scan(): Flow<List<BtClassicDevice>> = callbackFlow {
        val bt = adapter
        if (bt == null) {
            close(BtClassicScanException("Adaptateur Bluetooth indisponible"))
            return@callbackFlow
        }

        val found = LinkedHashMap<String, BtClassicDevice>()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = IntentCompat.getParcelableExtra(
                            intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
                        ) ?: return
                        val rssi = if (intent.hasExtra(BluetoothDevice.EXTRA_RSSI)) {
                            intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        } else {
                            null
                        }
                        found[device.address] = BtClassicDevice(
                            address = device.address,
                            name = device.name,
                            rssi = rssi,
                            bonded = device.bondState == BluetoothDevice.BOND_BONDED
                        )
                        trySend(found.values.toList())
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        close()   // une seule passe : le Flow se termine (pas de relance auto)
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        if (!bt.startDiscovery()) {
            close(BtClassicScanException("Impossible de démarrer la découverte"))
            return@callbackFlow
        }

        awaitClose {
            bt.cancelDiscovery()
            context.unregisterReceiver(receiver)
        }
    }
}
```

- [ ] **Step 5 : Lier dans le module Hilt**

Dans `BluetoothModule.kt`, ajouter le binding sous les deux existants :

```kotlin
    @Binds
    abstract fun bindBtClassicScanner(impl: AndroidBtClassicScanner): BtClassicScanner
```

- [ ] **Step 6 : Vérifier la compilation**

Run: `./gradlew :core:bluetooth:compileDebugKotlin -q`
Expected: `BUILD SUCCESSFUL` (l'impl framework n'est pas testée en JVM ; validée en Task 7).

- [ ] **Step 7 : Commit (utilisateur)** — scanner Bluetooth Classic dans `:core:bluetooth`.

---

## Task 3 : `:feature:btclassic` — écran + ViewModel

**Files:**
- Modify: `settings.gradle.kts`
- Create: `feature/btclassic/.gitignore`, `feature/btclassic/build.gradle.kts`
- Create: `feature/btclassic/src/main/java/com/francotte/homecontroller/feature/btclassic/BtClassicUiState.kt`
- Create: `feature/btclassic/src/main/java/com/francotte/homecontroller/feature/btclassic/BtClassicScanViewModel.kt`
- Create: `feature/btclassic/src/main/java/com/francotte/homecontroller/feature/btclassic/BtClassicScanScreen.kt`
- Test: `feature/btclassic/src/test/java/com/francotte/homecontroller/feature/btclassic/FakeBtClassicScanner.kt`
- Test: `feature/btclassic/src/test/java/com/francotte/homecontroller/feature/btclassic/BtClassicScanViewModelTest.kt`

- [ ] **Step 1 : Inclure le module**

Dans `settings.gradle.kts`, ajouter : `include(":feature:btclassic")`.

- [ ] **Step 2 : `.gitignore`**

Create `feature/btclassic/.gitignore` avec `/build`.

- [ ] **Step 3 : `feature/btclassic/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.francotte.homecontroller.feature.btclassic"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:bluetooth"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 4 : UiState**

`BtClassicUiState.kt` :

```kotlin
package com.francotte.homecontroller.feature.btclassic

import com.francotte.homecontroller.core.model.BtClassicDevice

/** État de l'écran de scan Bluetooth Classic. */
sealed interface BtClassicUiState {
    /** Permissions non accordées. */
    data object PermissionRequired : BtClassicUiState

    /** Permissions OK mais Bluetooth désactivé. */
    data object BluetoothOff : BtClassicUiState

    /** Prêt à scanner, aucune passe en cours. */
    data object Idle : BtClassicUiState

    /** Découverte en cours ; [devices] triés. */
    data class Scanning(val devices: List<BtClassicDevice>) : BtClassicUiState

    /** Découverte terminée (une passe) ; [devices] triés ; relance manuelle. */
    data class Finished(val devices: List<BtClassicDevice>) : BtClassicUiState

    /** Échec de la découverte. */
    data class Error(val message: String) : BtClassicUiState
}
```

- [ ] **Step 5 : Écrire les tests (échouent d'abord)**

`FakeBtClassicScanner.kt` :

```kotlin
package com.francotte.homecontroller.feature.btclassic

import com.francotte.homecontroller.core.bluetooth.BtClassicScanner
import com.francotte.homecontroller.core.model.BtClassicDevice
import kotlinx.coroutines.flow.Flow

/** Scanner Classic de test : rejoue le Flow fourni. */
class FakeBtClassicScanner(private val flow: Flow<List<BtClassicDevice>>) : BtClassicScanner {
    override fun scan(): Flow<List<BtClassicDevice>> = flow
}
```

`BtClassicScanViewModelTest.kt` :

```kotlin
package com.francotte.homecontroller.feature.btclassic

import com.francotte.homecontroller.core.bluetooth.BtClassicScanException
import com.francotte.homecontroller.core.model.BtClassicDevice
import com.francotte.homecontroller.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BtClassicScanViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun dev(address: String, rssi: Int?, bonded: Boolean = false) =
        BtClassicDevice(address = address, name = "dev-$address", rssi = rssi, bonded = bonded)

    @Test
    fun `etat initial est PermissionRequired`() {
        val vm = BtClassicScanViewModel(FakeBtClassicScanner(flowOf(emptyList())))
        assertEquals(BtClassicUiState.PermissionRequired, vm.uiState.value)
    }

    @Test
    fun `permission OK mais bluetooth eteint donne BluetoothOff`() {
        val vm = BtClassicScanViewModel(FakeBtClassicScanner(flowOf(emptyList())))
        vm.updateAvailability(hasPermissions = true, isBluetoothEnabled = false)
        assertEquals(BtClassicUiState.BluetoothOff, vm.uiState.value)
    }

    @Test
    fun `permission OK et bluetooth ON donne Idle`() {
        val vm = BtClassicScanViewModel(FakeBtClassicScanner(flowOf(emptyList())))
        vm.updateAvailability(hasPermissions = true, isBluetoothEnabled = true)
        assertEquals(BtClassicUiState.Idle, vm.uiState.value)
    }

    @Test
    fun `scan termine passe en Finished avec liste triee appaires puis rssi`() = runTest {
        val devices = listOf(
            dev("AA", -80),
            dev("BB", -40),
            dev("CC", -60, bonded = true)   // appairé → en tête
        )
        val vm = BtClassicScanViewModel(FakeBtClassicScanner(flowOf(devices)))
        vm.updateAvailability(hasPermissions = true, isBluetoothEnabled = true)

        vm.startScan()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is BtClassicUiState.Finished)
        assertEquals(
            listOf("CC", "BB", "AA"),
            (state as BtClassicUiState.Finished).devices.map { it.address }
        )
    }

    @Test
    fun `stopScan pendant le scan passe en Finished avec la liste courante`() = runTest {
        val vm = BtClassicScanViewModel(
            FakeBtClassicScanner(flow { emit(listOf(dev("AA", -50))); awaitCancellation() })
        )
        vm.updateAvailability(hasPermissions = true, isBluetoothEnabled = true)

        vm.startScan()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is BtClassicUiState.Scanning)

        vm.stopScan()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is BtClassicUiState.Finished)
        assertEquals(listOf("AA"), (state as BtClassicUiState.Finished).devices.map { it.address })
    }

    @Test
    fun `une erreur du scan donne Error`() = runTest {
        val failing = flow<List<BtClassicDevice>> { throw BtClassicScanException("boom") }
        val vm = BtClassicScanViewModel(FakeBtClassicScanner(failing))
        vm.updateAvailability(hasPermissions = true, isBluetoothEnabled = true)

        vm.startScan()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is BtClassicUiState.Error)
    }

    @Test
    fun `startScan sans disponibilite ne fait rien`() = runTest {
        val vm = BtClassicScanViewModel(FakeBtClassicScanner(flowOf(listOf(dev("AA", -50)))))
        vm.startScan()
        advanceUntilIdle()
        assertEquals(BtClassicUiState.PermissionRequired, vm.uiState.value)
    }
}
```

- [ ] **Step 6 : Vérifier que les tests échouent (compilation)**

Run: `./gradlew :feature:btclassic:testDebugUnitTest`
Expected: échec de compilation (VM absent).

- [ ] **Step 7 : ViewModel**

`BtClassicScanViewModel.kt` :

```kotlin
package com.francotte.homecontroller.feature.btclassic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.francotte.homecontroller.core.bluetooth.BtClassicScanner
import com.francotte.homecontroller.core.model.BtClassicDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BtClassicScanViewModel @Inject constructor(
    private val scanner: BtClassicScanner
) : ViewModel() {

    private val _uiState = MutableStateFlow<BtClassicUiState>(BtClassicUiState.PermissionRequired)
    val uiState: StateFlow<BtClassicUiState> = _uiState.asStateFlow()

    private var hasPermissions = false
    private var isBluetoothEnabled = false
    private var scanJob: Job? = null

    /** Faits Android fournis par l'écran (permissions, Bluetooth activé). */
    fun updateAvailability(hasPermissions: Boolean, isBluetoothEnabled: Boolean) {
        this.hasPermissions = hasPermissions
        this.isBluetoothEnabled = isBluetoothEnabled

        if (_uiState.value is BtClassicUiState.Scanning) {
            if (!hasPermissions || !isBluetoothEnabled) stopScan()
            return
        }

        _uiState.value = when {
            !hasPermissions -> BtClassicUiState.PermissionRequired
            !isBluetoothEnabled -> BtClassicUiState.BluetoothOff
            _uiState.value is BtClassicUiState.Finished -> _uiState.value  // conserve les résultats
            else -> BtClassicUiState.Idle
        }
    }

    fun startScan() {
        if (!hasPermissions || !isBluetoothEnabled) return
        if (scanJob != null) return

        _uiState.value = BtClassicUiState.Scanning(emptyList())
        scanJob = viewModelScope.launch {
            try {
                scanner.scan().collect { devices ->
                    _uiState.value = BtClassicUiState.Scanning(sortDevices(devices))
                }
                // Flow terminé (découverte finie) → Finished avec la dernière liste.
                val last = (_uiState.value as? BtClassicUiState.Scanning)?.devices ?: emptyList()
                _uiState.value = BtClassicUiState.Finished(last)
            } catch (c: CancellationException) {
                throw c   // annulation (stopScan / VM détruit) : pas une erreur
            } catch (t: Throwable) {
                _uiState.value = BtClassicUiState.Error(t.message ?: "Échec du scan")
            } finally {
                scanJob = null
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        val last = (_uiState.value as? BtClassicUiState.Scanning)?.devices ?: emptyList()
        _uiState.value = BtClassicUiState.Finished(last)
    }

    private fun sortDevices(devices: List<BtClassicDevice>): List<BtClassicDevice> =
        devices.sortedWith(
            compareByDescending<BtClassicDevice> { it.bonded }
                .thenByDescending { it.rssi ?: Int.MIN_VALUE }
                .thenBy { it.address }
        )
}
```

- [ ] **Step 8 : Vérifier que les tests passent**

Run: `./gradlew :feature:btclassic:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` (6 tests verts).

- [ ] **Step 9 : Écran Compose**

`BtClassicScanScreen.kt` :

```kotlin
package com.francotte.homecontroller.feature.btclassic

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.francotte.homecontroller.core.model.BtClassicDevice

/** Permissions runtime selon la version Android (mêmes que le scan BLE). */
private fun requiredScanPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun Context.hasScanPermissions(): Boolean =
    requiredScanPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

private fun Context.isBluetoothEnabled(): Boolean {
    val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    return manager?.adapter?.isEnabled == true
}

@Composable
fun BtClassicScanScreen(
    modifier: Modifier = Modifier,
    viewModel: BtClassicScanViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    fun refreshAvailability() {
        viewModel.updateAvailability(
            hasPermissions = context.hasScanPermissions(),
            isBluetoothEnabled = context.isBluetoothEnabled()
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshAvailability() }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshAvailability() }

    LaunchedEffect(Unit) { refreshAvailability() }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when (val state = uiState) {
                BtClassicUiState.PermissionRequired -> GateMessage(
                    message = "L'application a besoin de la permission Bluetooth pour scanner les appareils.",
                    actionLabel = "Accorder la permission",
                    onAction = { permissionLauncher.launch(requiredScanPermissions()) }
                )

                BtClassicUiState.BluetoothOff -> GateMessage(
                    message = "Le Bluetooth est désactivé.",
                    actionLabel = "Activer le Bluetooth",
                    onAction = {
                        enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    }
                )

                BtClassicUiState.Idle -> {
                    Button(onClick = { viewModel.startScan() }) { Text("Scanner") }
                    Spacer(Modifier.height(16.dp))
                    Text("Prêt à scanner les appareils Bluetooth Classic.")
                }

                is BtClassicUiState.Scanning -> {
                    Button(onClick = { viewModel.stopScan() }) { Text("Arrêter") }
                    Spacer(Modifier.height(16.dp))
                    if (state.devices.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Recherche d'appareils…")
                        }
                    } else {
                        DeviceList(state.devices)
                    }
                }

                is BtClassicUiState.Finished -> {
                    Button(onClick = { viewModel.startScan() }) { Text("Scanner à nouveau") }
                    Spacer(Modifier.height(16.dp))
                    if (state.devices.isEmpty()) {
                        Text("Aucun appareil trouvé.")
                    } else {
                        DeviceList(state.devices)
                    }
                }

                is BtClassicUiState.Error -> GateMessage(
                    message = state.message,
                    actionLabel = "Réessayer",
                    onAction = {
                        refreshAvailability()
                        viewModel.startScan()
                    }
                )
            }
        }
    }
}

@Composable
private fun DeviceList(devices: List<BtClassicDevice>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(devices, key = { it.address }) { device ->
            DeviceRow(device)
        }
    }
}

@Composable
private fun DeviceRow(device: BtClassicDevice) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name ?: "Inconnu", style = MaterialTheme.typography.titleMedium)
                Text(device.address, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = device.rssi?.let { "$it dBm" } ?: "— dBm",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (device.bonded) {
                Text(
                    text = "Appairé",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun GateMessage(message: String, actionLabel: String, onAction: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message)
        Button(onClick = onAction) { Text(actionLabel) }
    }
}
```

- [ ] **Step 10 : Vérifier la compilation du module**

Run: `./gradlew :feature:btclassic:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` (écran compile, tests toujours verts).

- [ ] **Step 11 : Commit (utilisateur)** — module `:feature:btclassic`.

---

## Task 4 : `:core:designsystem` — icônes Phosphor + AppIcons

**Files:**
- Create: `core/designsystem/src/main/res/drawable/ic_home_assistant.xml`
- Create: `core/designsystem/src/main/res/drawable/ic_ble.xml`
- Create: `core/designsystem/src/main/res/drawable/ic_bt_classic.xml`
- Create: `core/designsystem/src/main/res/drawable/ic_settings.xml`
- Create: `core/designsystem/src/main/java/com/francotte/homecontroller/core/designsystem/AppIcons.kt`

> Icônes Phosphor (poids *regular*, viewBox 256). `fillColor` neutre : le composable `Icon`
> applique la teinte du thème par-dessus.

- [ ] **Step 1 : `ic_home_assistant.xml` (house)**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="256"
    android:viewportHeight="256">
    <path
        android:fillColor="#000000"
        android:pathData="M219.31,108.68l-80-80a16,16,0,0,0-22.62,0l-80,80A15.87,15.87,0,0,0,32,120v96a8,8,0,0,0,8,8h64a8,8,0,0,0,8-8V160h32v56a8,8,0,0,0,8,8h64a8,8,0,0,0,8-8V120A15.87,15.87,0,0,0,219.31,108.68ZM208,208H160V152a8,8,0,0,0-8-8H104a8,8,0,0,0-8,8v56H48V120l80-80,80,80Z" />
</vector>
```

- [ ] **Step 2 : `ic_ble.xml` (bluetooth)**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="256"
    android:viewportHeight="256">
    <path
        android:fillColor="#000000"
        android:pathData="M196.8,169.6,141.33,128,196.8,86.4a8,8,0,0,0,0-12.8l-64-48A8,8,0,0,0,120,32v80L68.8,73.6a8,8,0,0,0-9.6,12.8L114.67,128,59.2,169.6a8,8,0,1,0,9.6,12.8L120,144v80a8,8,0,0,0,12.8,6.4l64-48a8,8,0,0,0,0-12.8ZM136,48l42.67,32L136,112Zm0,160V144l42.67,32Z" />
</vector>
```

- [ ] **Step 3 : `ic_bt_classic.xml` (broadcast)**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="256"
    android:viewportHeight="256">
    <path
        android:fillColor="#000000"
        android:pathData="M128,88a40,40,0,1,0,40,40A40,40,0,0,0,128,88Zm0,64a24,24,0,1,1,24-24A24,24,0,0,1,128,152Zm73.71,7.14a80,80,0,0,1-14.08,22.2,8,8,0,0,1-11.92-10.67,63.95,63.95,0,0,0,0-85.33,8,8,0,1,1,11.92-10.67,80.08,80.08,0,0,1,14.08,84.47ZM69,103.09a64,64,0,0,0,11.26,67.58,8,8,0,0,1-11.92,10.67,79.93,79.93,0,0,1,0-106.67A8,8,0,1,1,80.29,85.34,63.77,63.77,0,0,0,69,103.09ZM248,128a119.58,119.58,0,0,1-34.29,84,8,8,0,1,1-11.42-11.2,103.9,103.9,0,0,0,0-145.56A8,8,0,1,1,213.71,44,119.58,119.58,0,0,1,248,128ZM53.71,200.78A8,8,0,1,1,42.29,212a119.87,119.87,0,0,1,0-168,8,8,0,1,1,11.42,11.2,103.9,103.9,0,0,0,0,145.56Z" />
</vector>
```

- [ ] **Step 4 : `ic_settings.xml` (gear)**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="256"
    android:viewportHeight="256">
    <path
        android:fillColor="#000000"
        android:pathData="M128,80a48,48,0,1,0,48,48A48.05,48.05,0,0,0,128,80Zm0,80a32,32,0,1,1,32-32A32,32,0,0,1,128,160Zm88-29.84q.06-2.16,0-4.32l14.92-18.64a8,8,0,0,0,1.48-7.06,107.21,107.21,0,0,0-10.88-26.25,8,8,0,0,0-6-3.93l-23.72-2.64q-1.48-1.56-3-3L186,40.54a8,8,0,0,0-3.94-6,107.71,107.71,0,0,0-26.25-10.87,8,8,0,0,0-7.06,1.49L130.16,40Q128,40,125.84,40L107.2,25.11a8,8,0,0,0-7.06-1.48A107.6,107.6,0,0,0,73.89,34.51a8,8,0,0,0-3.93,6L67.32,64.27q-1.56,1.49-3,3L40.54,70a8,8,0,0,0-6,3.94,107.71,107.71,0,0,0-10.87,26.25,8,8,0,0,0,1.49,7.06L40,125.84Q40,128,40,130.16L25.11,148.8a8,8,0,0,0-1.48,7.06,107.21,107.21,0,0,0,10.88,26.25,8,8,0,0,0,6,3.93l23.72,2.64q1.49,1.56,3,3L70,215.46a8,8,0,0,0,3.94,6,107.71,107.71,0,0,0,26.25,10.87,8,8,0,0,0,7.06-1.49L125.84,216q2.16.06,4.32,0l18.64,14.92a8,8,0,0,0,7.06,1.48,107.21,107.21,0,0,0,26.25-10.88,8,8,0,0,0,3.93-6l2.64-23.72q1.56-1.48,3-3L215.46,186a8,8,0,0,0,6-3.94,107.71,107.71,0,0,0,10.87-26.25,8,8,0,0,0-1.49-7.06Zm-16.1-6.5a73.93,73.93,0,0,1,0,8.68,8,8,0,0,0,1.74,5.48l14.19,17.73a91.57,91.57,0,0,1-6.23,15L187,173.11a8,8,0,0,0-5.1,2.64,74.11,74.11,0,0,1-6.14,6.14,8,8,0,0,0-2.64,5.1l-2.51,22.58a91.32,91.32,0,0,1-15,6.23l-17.74-14.19a8,8,0,0,0-5-1.75h-.48a73.93,73.93,0,0,1-8.68,0,8,8,0,0,0-5.48,1.74L100.45,215.8a91.57,91.57,0,0,1-15-6.23L82.89,187a8,8,0,0,0-2.64-5.1,74.11,74.11,0,0,1-6.14-6.14,8,8,0,0,0-5.1-2.64L46.43,170.6a91.32,91.32,0,0,1-6.23-15l14.19-17.74a8,8,0,0,0,1.74-5.48,73.93,73.93,0,0,1,0-8.68,8,8,0,0,0-1.74-5.48L40.2,100.45a91.57,91.57,0,0,1,6.23-15L69,82.89a8,8,0,0,0,5.1-2.64,74.11,74.11,0,0,1,6.14-6.14A8,8,0,0,0,82.89,69L85.4,46.43a91.32,91.32,0,0,1,15-6.23l17.74,14.19a8,8,0,0,0,5.48,1.74,73.93,73.93,0,0,1,8.68,0,8,8,0,0,0,5.48-1.74L155.55,40.2a91.57,91.57,0,0,1,15,6.23L173.11,69a8,8,0,0,0,2.64,5.1,74.11,74.11,0,0,1,6.14,6.14,8,8,0,0,0,5.1,2.64l22.58,2.51a91.32,91.32,0,0,1,6.23,15l-14.19,17.74A8,8,0,0,0,199.87,123.66Z" />
</vector>
```

- [ ] **Step 5 : Objet `AppIcons`**

`AppIcons.kt` :

```kotlin
package com.francotte.homecontroller.core.designsystem

import androidx.annotation.DrawableRes

/** Icônes de l'app (Phosphor, importées en vector drawables). */
object AppIcons {
    @DrawableRes val HomeAssistant: Int = R.drawable.ic_home_assistant
    @DrawableRes val Ble: Int = R.drawable.ic_ble
    @DrawableRes val BtClassic: Int = R.drawable.ic_bt_classic
    @DrawableRes val Settings: Int = R.drawable.ic_settings
}
```

- [ ] **Step 6 : Vérifier la compilation**

Run: `./gradlew :core:designsystem:compileDebugKotlin -q`
Expected: `BUILD SUCCESSFUL` (R.drawable.* générés).

- [ ] **Step 7 : Commit (utilisateur)** — icônes Phosphor + AppIcons.

---

## Task 5 : `:app` — 3e onglet, renommage, migration icônes

**Files:**
- Modify: `gradle/libs.versions.toml` (retirer material-icons-core)
- Modify: `app/build.gradle.kts`
- Modify: `feature/homeassistant/build.gradle.kts`
- Modify: `feature/homeassistant/src/main/java/com/francotte/homecontroller/feature/homeassistant/HomeAssistantScreen.kt`
- Modify: `app/src/main/java/com/francotte/homecontroller/navigation/NavKeys.kt`
- Modify: `app/src/main/java/com/francotte/homecontroller/navigation/HomeControllerAppShell.kt`

- [ ] **Step 1 : Dépendance feature dans `:app`**

Dans `app/build.gradle.kts`, sous les autres `project(...)`, ajouter :

```kotlin
    implementation(project(":feature:btclassic"))
```

et **retirer** la ligne :

```kotlin
    implementation(libs.androidx.compose.material.icons.core)
```

- [ ] **Step 2 : Retirer material-icons-core de `:feature:homeassistant`**

Dans `feature/homeassistant/build.gradle.kts`, **retirer** la ligne :

```kotlin
    implementation(libs.androidx.compose.material.icons.core)
```

- [ ] **Step 3 : Retirer l'entrée du catalogue**

Dans `gradle/libs.versions.toml`, **supprimer** la ligne :

```toml
androidx-compose-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core" }
```

- [ ] **Step 4 : Migrer l'icône de l'écran HA vers Phosphor**

Dans `HomeAssistantScreen.kt` :

Remplacer les imports material-icons :

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
```

par :

```kotlin
import androidx.compose.ui.res.painterResource
import com.francotte.homecontroller.core.designsystem.AppIcons
```

Puis remplacer l'appel `Icon(Icons.Filled.Settings, …)` par :

```kotlin
            Icon(painterResource(AppIcons.Settings), contentDescription = "Configuration")
```

- [ ] **Step 5 : Nouvelle clé de navigation**

Dans `NavKeys.kt`, ajouter :

```kotlin
data object BtClassicKey : NavKey
```

- [ ] **Step 6 : Réécrire le shell (3 onglets + icônes Phosphor)**

`HomeControllerAppShell.kt` — remplacer tout le contenu par :

```kotlin
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
import com.francotte.homecontroller.feature.homeassistant.HomeAssistantScreen
import com.francotte.homecontroller.feature.scan.ScanScreen

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
    val bleBackStack = remember { mutableStateListOf<NavKey>(ScanKey) }
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
                    HomeAssistantScreen()
                }
                entry<ScanKey> {
                    ScanScreen(onDeviceClick = { address -> bleBackStack.add(DeviceControlKey(address)) })
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
```

- [ ] **Step 7 : Build complet + toute la suite de tests**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` — plus aucune référence à material-icons ; le graphe Hilt (dont `BtClassicScanner`) compile ; tous les tests passent.

> Si une référence `Icons.` subsiste (erreur de compilation « Unresolved reference Icons »),
> c'est un usage material-icons oublié : le migrer vers `AppIcons` + `painterResource`.

- [ ] **Step 8 : Commit (utilisateur)** — 3e onglet Classic, renommage BLE, migration Phosphor.

---

## Task 6 : Validation manuelle (téléphone)

**Files:** aucun.

- [ ] **Step 1 : Installer sur le téléphone**

Run: `./gradlew :app:installDebug`
Expected: `BUILD SUCCESSFUL`, app installée.

- [ ] **Step 2 : Parcours Bluetooth Classic**

- [ ] 3 onglets visibles : Home Assistant / BLE / Bluetooth Classic, avec icônes Phosphor.
- [ ] Onglet **Bluetooth Classic** → bouton **Scanner** → si besoin, accorder permission / activer Bluetooth.
- [ ] La liste se remplit pendant ~12 s (nom/Inconnu, MAC, RSSI si présent, badge « Appairé » sur les appareils liés), puis s'arrête sur **Scanner à nouveau**.
- [ ] **Scanner à nouveau** relance une passe.
- [ ] **Arrêter** pendant un scan fige la liste courante (passe en « Scanner à nouveau »).

- [ ] **Step 3 : Non-régression**

- [ ] Onglet **BLE** (renommé) → scan BLE et contrôle ESP32 inchangés.
- [ ] Onglet **Home Assistant** → liste/commande inchangées ; l'icône ⚙️ (gear Phosphor) fonctionne.
- [ ] Changement d'onglets → position de navigation conservée par onglet.

- [ ] **Step 4 : Commit éventuel (utilisateur)** — ajustements.

---

## Validation croisée avec la spec

- **Modèle BtClassicDevice** → Task 1.
- **BtClassicScanner + impl découverte (une passe, `close()` sur DISCOVERY_FINISHED)** → Task 2.
- **@Binds Hilt + dépendance androidx.core** → Task 2.
- **Feature : UiState (Idle/Scanning/Finished…), VM (une passe + relance manuelle, tri appairés/RSSI), écran** → Task 3.
- **Icônes Phosphor en vector drawables + AppIcons ; retrait material-icons-core** → Tasks 4 & 5.
- **Shell : renommage BLE, 3e onglet Classic, migration icônes ; écran HA gear** → Task 5.
- **Tests VM (fakes + coroutines-test)** → Task 3 ; **scanner non testé JVM / validation manuelle** → Tasks 2 & 6.
- **Hors périmètre** (appairage, lissage RSSI, persistance) → non couvert, assumé.
```
