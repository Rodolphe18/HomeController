# Modularisation NIA + Hilt — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Réorganiser l'app mono-module (scan BLE + contrôle ESP32) en modules NIA (`:app` / `:feature:*` / `:core:*`) avec Hilt, **sans changer le comportement**, tous les tests restant verts.

**Architecture:** `:core:model` (Kotlin JVM pur) ; `:core:bluetooth` (capacité BLE, interfaces publiques + impls Android `internal` + module Hilt) ; `:core:designsystem`, `:core:testing` ; `:feature:scan`, `:feature:devicedetail` ; `:app` (nav Nav3 + Hilt). DI via Hilt (KSP).

**Tech Stack:** Kotlin 2.2.10, AGP 9.2.1, Compose (BOM 2026.02.01), Hilt + KSP, Nav3 1.0.1.

**Spec :** `docs/superpowers/specs/2026-07-07-modularisation-nia-hilt-design.md`

> ⚠️ **Commits :** l'utilisateur commite lui-même. **Ne pas committer.** Les étapes « Commit » indiquent le contenu logique.

> ⚠️ **Refactor à comportement constant.** Aucun changement d'UI/logique. Critère de réussite : build + tests verts + parcours identique.

> ⚠️ **Nouveaux packages par module** (convention NIA) :
> - `:core:model` → `com.francotte.homecontroller.core.model`
> - `:core:testing` → `com.francotte.homecontroller.core.testing`
> - `:core:designsystem` → `com.francotte.homecontroller.core.designsystem`
> - `:core:bluetooth` → `com.francotte.homecontroller.core.bluetooth`
> - `:feature:scan` → `com.francotte.homecontroller.feature.scan`
> - `:feature:devicedetail` → `com.francotte.homecontroller.feature.devicedetail`
> - `:app` → `com.francotte.homecontroller` (inchangé)
>
> Chaque déplacement de fichier = nouveau chemin **et** nouvelle ligne `package`. Les imports inter-modules se mettent à jour en conséquence ; le compilateur signale les oublis.

---

## Task 1 : Catalogue de versions (Hilt, KSP, plugins modules)

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1 : Ajouter versions**

Sous `[versions]`, ajouter :

```toml
ksp = "2.2.10-2.0.2"
hilt = "2.57"
hiltNavigationCompose = "1.2.0"
```

- [ ] **Step 2 : Ajouter libraries**

Sous `[libraries]`, ajouter :

```toml
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
```

- [ ] **Step 3 : Ajouter plugins**

Sous `[plugins]`, ajouter :

```toml
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

> `kotlin-serialization` n'est pas utilisé cet incrément mais sera prêt pour HA ; on peut l'omettre si tu préfères le stricte YAGNI.

- [ ] **Step 4 : Déclarer les plugins au niveau racine**

Dans `build.gradle.kts` (racine), remplacer par :

```kotlin
// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
```

- [ ] **Step 5 : Vérifier la résolution des plugins**

Run: `./gradlew help -q`
Expected: `BUILD SUCCESSFUL` (les plugins/versions se résolvent).

> Si une version (KSP, Hilt) ne se résout pas : ouvrir la page Maven correspondante, mettre la dernière version compatible (KSP doit matcher Kotlin 2.2.10), relancer. Noter les versions retenues.

- [ ] **Step 6 : Commit (utilisateur)** — catalogue + build racine.

---

## Task 2 : Module `:core:model` (valide le modèle multi-module)

**Files:**
- Create: `core/model/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Create: `core/model/src/main/java/com/francotte/homecontroller/core/model/BleDevice.kt`
- Create: `core/model/src/main/java/com/francotte/homecontroller/core/model/EspConnectionState.kt`
- Delete: `app/src/main/java/com/francotte/homecontroller/domain/model/BleDevice.kt`
- Delete: `app/src/main/java/com/francotte/homecontroller/domain/connection/EspConnectionState.kt`

> Ce module Kotlin/JVM pur valide la mécanique multi-module avec le moindre risque.

- [ ] **Step 1 : Déclarer le module dans settings**

Dans `settings.gradle.kts`, remplacer `include(":app")` par :

```kotlin
include(":app")
include(":core:model")
```

- [ ] **Step 2 : Créer `core/model/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    // modèles purs : aucune dépendance
}
```

- [ ] **Step 3 : Créer `BleDevice.kt` (nouveau package)**

`core/model/src/main/java/com/francotte/homecontroller/core/model/BleDevice.kt` :

```kotlin
package com.francotte.homecontroller.core.model

/**
 * Un périphérique BLE détecté lors d'un scan.
 *
 * @property address adresse MAC, identifiant unique du périphérique
 * @property name    nom diffusé par le périphérique, ou null si non diffusé
 * @property rssi    force du signal reçu, en dBm (plus proche de 0 = plus fort)
 */
data class BleDevice(
    val address: String,
    val name: String?,
    val rssi: Int
)
```

- [ ] **Step 4 : Créer `EspConnectionState.kt` (nouveau package)**

`core/model/src/main/java/com/francotte/homecontroller/core/model/EspConnectionState.kt` :

```kotlin
package com.francotte.homecontroller.core.model

/** État d'une session de connexion à un appareil ESP32. */
sealed interface EspConnectionState {
    data object Connecting : EspConnectionState
    data object Connected : EspConnectionState
    data object Disconnected : EspConnectionState
    data class Error(val message: String) : EspConnectionState
}
```

- [ ] **Step 5 : Supprimer les originaux**

Supprimer `app/src/main/java/com/francotte/homecontroller/domain/model/BleDevice.kt` et `app/src/main/java/com/francotte/homecontroller/domain/connection/EspConnectionState.kt`.

> À ce stade `:app` ne compile plus (imports cassés) — c'est attendu ; corrigé quand les modules consommateurs seront migrés. On vérifie ici seulement `:core:model`.

- [ ] **Step 6 : Vérifier la compilation du module**

Run: `./gradlew :core:model:compileKotlin -q`
Expected: `BUILD SUCCESSFUL`.

> Si le module JVM ne compile pas (plugin), vérifier que `org.jetbrains.kotlin.jvm` est bien résolu (Task 1).

- [ ] **Step 7 : Commit (utilisateur)** — module `:core:model`.

---

## Task 3 : Module `:core:testing`

**Files:**
- Create: `core/testing/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Create: `core/testing/src/main/java/com/francotte/homecontroller/core/testing/MainDispatcherRule.kt`
- Delete: `app/src/test/java/com/francotte/homecontroller/MainDispatcherRule.kt`

- [ ] **Step 1 : Inclure le module**

Ajouter dans `settings.gradle.kts` : `include(":core:testing")`.

- [ ] **Step 2 : `core/testing/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.francotte.homecontroller.core.testing"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
}
```

> Si AGP 9.2.1 refuse le plugin `kotlin.android` (Kotlin déjà intégré), retirer la ligne `alias(libs.plugins.kotlin.android)` et relancer. Le build indiquera clairement le cas.

- [ ] **Step 3 : Déplacer `MainDispatcherRule` (source `main`, nouveau package)**

`core/testing/src/main/java/com/francotte/homecontroller/core/testing/MainDispatcherRule.kt` :

```kotlin
package com.francotte.homecontroller.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Remplace Dispatchers.Main par un dispatcher de test contrôlable. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) { Dispatchers.setMain(dispatcher) }
    override fun finished(description: Description) { Dispatchers.resetMain() }
}
```

Supprimer `app/src/test/java/com/francotte/homecontroller/MainDispatcherRule.kt`.

- [ ] **Step 4 : Vérifier la compilation**

Run: `./gradlew :core:testing:compileDebugKotlin -q`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5 : Commit (utilisateur)** — module `:core:testing`.

---

## Task 4 : Module `:core:designsystem` (thème)

**Files:**
- Create: `core/designsystem/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Move: `app/src/main/java/com/francotte/homecontroller/ui/theme/{Theme,Color,Type}.kt` → `core/designsystem/src/main/java/com/francotte/homecontroller/core/designsystem/theme/`

- [ ] **Step 1 : Inclure le module**

Ajouter dans `settings.gradle.kts` : `include(":core:designsystem")`.

- [ ] **Step 2 : `core/designsystem/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.francotte.homecontroller.core.designsystem"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

- [ ] **Step 3 : Déplacer les 3 fichiers de thème (nouveau package)**

Déplacer `Theme.kt`, `Color.kt`, `Type.kt` vers `core/designsystem/src/main/java/com/francotte/homecontroller/core/designsystem/theme/`, et **remplacer la ligne `package`** de chacun par :

```kotlin
package com.francotte.homecontroller.core.designsystem.theme
```

Supprimer l'ancien dossier `app/src/main/java/com/francotte/homecontroller/ui/theme/`.

- [ ] **Step 4 : Vérifier la compilation**

Run: `./gradlew :core:designsystem:compileDebugKotlin -q`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5 : Commit (utilisateur)** — module `:core:designsystem`.

---

## Task 5 : Module `:core:bluetooth` (capacité BLE + Hilt)

**Files:**
- Create: `core/bluetooth/build.gradle.kts`, `core/bluetooth/src/main/AndroidManifest.xml`
- Modify: `settings.gradle.kts`
- Move + adapter : `BleScanner`, `BleScanException`, `EspDeviceClient`, `AndroidBleScanner`, `AndroidEspDeviceClient`, `GattProfile`, `CounterCodec`
- Create: `core/bluetooth/.../BluetoothModule.kt`
- Move test: `CounterCodecTest`

- [ ] **Step 1 : Inclure le module**

Ajouter dans `settings.gradle.kts` : `include(":core:bluetooth")`.

- [ ] **Step 2 : `core/bluetooth/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.francotte.homecontroller.core.bluetooth"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":core:model"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
}
```

- [ ] **Step 3 : Manifest des permissions BLE (déplacé de `:app`)**

`core/bluetooth/src/main/AndroidManifest.xml` — les permissions BLE appartiennent désormais à la capacité :

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission
        android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />
    <uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
</manifest>
```

Retirer ces mêmes `<uses-permission>`/`<uses-feature>` de `app/src/main/AndroidManifest.xml` (elles seront fusionnées automatiquement depuis le module). **Conserver** le `<application …>` dans `:app`.

- [ ] **Step 4 : Déplacer les interfaces publiques (nouveau package `...core.bluetooth`)**

Déplacer et re-`package` :
- `BleScanner.kt` → `core/bluetooth/src/main/java/com/francotte/homecontroller/core/bluetooth/BleScanner.kt`, package `com.francotte.homecontroller.core.bluetooth`, et **mettre à jour l'import** du modèle : `import com.francotte.homecontroller.core.model.BleDevice`.
- `BleScanException.kt` → même package.
- `EspDeviceClient.kt` → même package (pas d'import modèle à changer ; il utilise `Flow`).

- [ ] **Step 5 : Déplacer les impls en `internal` (+ `@Inject`)**

Déplacer dans le package `com.francotte.homecontroller.core.bluetooth`, et rendre `internal` + injectable.

`AndroidBleScanner.kt` — passer `internal` et **changer le constructeur** pour dériver l'adaptateur (aligné sur `AndroidEspDeviceClient`) :

```kotlin
package com.francotte.homecontroller.core.bluetooth

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import androidx.annotation.RequiresPermission
import com.francotte.homecontroller.core.model.BleDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

internal class AndroidBleScanner @Inject constructor(
    @ApplicationContext context: Context
) : BleScanner {

    private val bluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    override fun scan(): Flow<List<BleDevice>> = callbackFlow {
        val leScanner = bluetoothAdapter?.bluetoothLeScanner
        if (leScanner == null) {
            close(BleScanException(ERROR_ADAPTER_UNAVAILABLE))
            return@callbackFlow
        }
        val found = LinkedHashMap<String, BleDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                found[device.address] = BleDevice(
                    address = device.address,
                    name = device.name,
                    rssi = result.rssi
                )
                trySend(found.values.toList())
            }
            override fun onScanFailed(errorCode: Int) { close(BleScanException(errorCode)) }
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        leScanner.startScan(null, settings, callback)
        awaitClose { leScanner.stopScan(callback) }
    }

    private companion object { const val ERROR_ADAPTER_UNAVAILABLE = -1 }
}
```

`AndroidEspDeviceClient.kt` — passer `internal` et annoter le constructeur `@Inject` :

```kotlin
package com.francotte.homecontroller.core.bluetooth
// … (imports inchangés, + les deux ci-dessous)
import com.francotte.homecontroller.core.model.EspConnectionState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal class AndroidEspDeviceClient @Inject constructor(
    @ApplicationContext private val context: Context
) : EspDeviceClient {
    // … corps inchangé (voir plan ESP32 Task 7) …
}
```

> Le corps de `AndroidEspDeviceClient` est identique à l'actuel ; seuls changent le `package`, le `internal`, l'annotation `@Inject constructor(@ApplicationContext …)`, et les imports du modèle (`EspConnectionState` depuis `core.model`).

`GattProfile.kt` → package `...core.bluetooth`, passer `internal object GattProfile`.

`CounterCodec.kt` → package `...core.bluetooth`, passer `internal object CounterCodec`.

- [ ] **Step 6 : Créer le module Hilt**

`core/bluetooth/src/main/java/com/francotte/homecontroller/core/bluetooth/BluetoothModule.kt` :

```kotlin
package com.francotte.homecontroller.core.bluetooth

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BluetoothModule {

    @Binds
    abstract fun bindBleScanner(impl: AndroidBleScanner): BleScanner

    // non scopé → une nouvelle instance par injection (une session par écran de contrôle)
    @Binds
    abstract fun bindEspDeviceClient(impl: AndroidEspDeviceClient): EspDeviceClient
}
```

- [ ] **Step 7 : Déplacer le test `CounterCodec`**

`core/bluetooth/src/test/java/com/francotte/homecontroller/core/bluetooth/CounterCodecTest.kt` : même contenu que l'actuel mais **package** `com.francotte.homecontroller.core.bluetooth` (le test peut voir `internal` car même module). Supprimer l'ancien test.

- [ ] **Step 8 : Vérifier compilation + test du module**

Run: `./gradlew :core:bluetooth:testDebugUnitTest -q`
Expected: `BUILD SUCCESSFUL` (CounterCodecTest vert, graphe Hilt du module compile).

- [ ] **Step 9 : Commit (utilisateur)** — module `:core:bluetooth`.

---

## Task 6 : Module `:feature:scan`

**Files:**
- Create: `feature/scan/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Move + adapter : `ScanScreen`, `ScanViewModel`, `ScanUiState`, `RssiStabilizer` (package `...feature.scan`)
- Delete: `ScanViewModelFactory.kt`
- Move tests : `ScanViewModelTest`, `RssiStabilizerTest`, `FakeBleScanner`

- [ ] **Step 1 : Inclure le module**

Ajouter dans `settings.gradle.kts` : `include(":feature:scan")`.

- [ ] **Step 2 : `feature/scan/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.francotte.homecontroller.feature.scan"
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
    implementation(project(":core:designsystem"))

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

- [ ] **Step 3 : Déplacer `ScanUiState` et `RssiStabilizer`**

Vers `feature/scan/src/main/java/com/francotte/homecontroller/feature/scan/`, package `com.francotte.homecontroller.feature.scan`, imports modèle → `com.francotte.homecontroller.core.model.BleDevice`. Contenu logique inchangé.

- [ ] **Step 4 : Adapter `ScanViewModel` pour Hilt**

Même corps qu'aujourd'hui, mais **package** `...feature.scan`, import `BleScanner` depuis `com.francotte.homecontroller.core.bluetooth`, et annotations Hilt :

```kotlin
package com.francotte.homecontroller.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.francotte.homecontroller.core.bluetooth.BleScanException
import com.francotte.homecontroller.core.bluetooth.BleScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanner: BleScanner
) : ViewModel() {
    // refreshIntervalMs devient une constante interne (plus de paramètre injecté)
    private val refreshIntervalMs: Long = 1_000L
    // … reste du corps inchangé …
}
```

> Le paramètre `refreshIntervalMs` du constructeur disparaît (Hilt n'injecte pas de `Long`) → il devient un champ interne `1_000L`. Le test qui l'utilisait est adapté au Step 6.

- [ ] **Step 5 : Adapter `ScanScreen` (hiltViewModel)**

Package `...feature.scan`, imports modèle/bluetooth mis à jour, et le `viewModel(factory = …)` remplacé :

```kotlin
import androidx.hilt.navigation.compose.hiltViewModel
// …
@Composable
fun ScanScreen(
    onDeviceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = hiltViewModel()
) { /* corps inchangé */ }
```

Supprimer `ScanViewModelFactory.kt`.

- [ ] **Step 6 : Déplacer les tests (package `...feature.scan`)**

- `FakeBleScanner.kt`, `RssiStabilizerTest.kt` : package `...feature.scan`, imports modèle mis à jour.
- `ScanViewModelTest.kt` : package `...feature.scan` ; **import** `com.francotte.homecontroller.core.testing.MainDispatcherRule` ; et le constructeur du VM qui passait `refreshIntervalMs` doit être adapté. Le test « publie les appareils triés » utilisait `ScanViewModel(FakeBleScanner(...), refreshIntervalMs = 1_000L)` → remplacer par `ScanViewModel(FakeBleScanner(...))` (l'intervalle est désormais la constante interne 1 s ; le test avance déjà de 1 100 ms).

- [ ] **Step 7 : Vérifier compilation + tests du module**

Run: `./gradlew :feature:scan:testDebugUnitTest -q`
Expected: `BUILD SUCCESSFUL` (ScanViewModelTest + RssiStabilizerTest verts).

- [ ] **Step 8 : Commit (utilisateur)** — module `:feature:scan`.

---

## Task 7 : Module `:feature:devicedetail`

**Files:**
- Create: `feature/devicedetail/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Move + adapter : `DeviceControlScreen`, `DeviceControlViewModel`, `ControlUiState`
- Delete: `DeviceControlViewModelFactory.kt`
- Move tests : `DeviceControlViewModelTest`, `FakeEspDeviceClient`

- [ ] **Step 1 : Inclure le module**

Ajouter dans `settings.gradle.kts` : `include(":feature:devicedetail")`.

- [ ] **Step 2 : `feature/devicedetail/build.gradle.kts`**

Identique à `:feature:scan` (Task 6 Step 2) à un détail près : le `namespace` :

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.francotte.homecontroller.feature.devicedetail"
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
    implementation(project(":core:designsystem"))

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

- [ ] **Step 3 : Déplacer `ControlUiState`**

Package `...feature.devicedetail`, import `com.francotte.homecontroller.core.model.EspConnectionState`. Contenu inchangé.

- [ ] **Step 4 : Adapter `DeviceControlViewModel` (Hilt + injection assistée)**

```kotlin
package com.francotte.homecontroller.feature.devicedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.francotte.homecontroller.core.bluetooth.EspDeviceClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = DeviceControlViewModel.Factory::class)
class DeviceControlViewModel @AssistedInject constructor(
    @Assisted private val address: String,
    private val client: EspDeviceClient
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(address: String): DeviceControlViewModel
    }

    // … corps inchangé (ledOn, transientError, uiState via combine, onLedToggle, onRetry, onCleared) …
}
```

Supprimer `DeviceControlViewModelFactory.kt`.

- [ ] **Step 5 : Adapter `DeviceControlScreen` (hiltViewModel assisté)**

```kotlin
package com.francotte.homecontroller.feature.devicedetail

import androidx.hilt.navigation.compose.hiltViewModel
// … imports inchangés (+ EspConnectionState depuis core.model) …

@Composable
fun DeviceControlScreen(
    address: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeviceControlViewModel = hiltViewModel(
        creationCallback = { factory: DeviceControlViewModel.Factory -> factory.create(address) }
    )
) { /* corps inchangé */ }
```

- [ ] **Step 6 : Déplacer les tests**

- `FakeEspDeviceClient.kt` : package `...feature.devicedetail`, import `EspConnectionState`/`EspDeviceClient` depuis `core.model`/`core.bluetooth`.
- `DeviceControlViewModelTest.kt` : package `...feature.devicedetail` ; import `MainDispatcherRule` depuis `core.testing` ; le corps est inchangé (il construit `DeviceControlViewModel("AA:BB:CC", fake)` directement — l'injection assistée n'empêche pas l'appel direct du constructeur en test).

- [ ] **Step 7 : Vérifier compilation + tests du module**

Run: `./gradlew :feature:devicedetail:testDebugUnitTest -q`
Expected: `BUILD SUCCESSFUL` (6 tests verts).

- [ ] **Step 8 : Commit (utilisateur)** — module `:feature:devicedetail`.

---

## Task 8 : `:app` — Hilt, navigation, nettoyage

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/francotte/homecontroller/HomeControllerApplication.kt`
- Modify: `app/src/main/java/com/francotte/homecontroller/MainActivity.kt`
- Modify: `app/src/main/java/com/francotte/homecontroller/navigation/HomeControllerNavDisplay.kt`
- Delete: `app/src/main/java/com/francotte/homecontroller/di/AppContainer.kt` (et le dossier `di/`), tout `presentation/`, `domain/`, `data/` résiduels dans `:app`.

- [ ] **Step 1 : Réécrire `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.francotte.homecontroller"
    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "com.francotte.homecontroller"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":feature:scan"))
    implementation(project(":feature:devicedetail"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

- [ ] **Step 2 : Annoter l'Application**

`HomeControllerApplication.kt` :

```kotlin
package com.francotte.homecontroller

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HomeControllerApplication : Application()
```

- [ ] **Step 3 : Annoter `MainActivity`**

`MainActivity.kt` :

```kotlin
package com.francotte.homecontroller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.francotte.homecontroller.core.designsystem.theme.HomeControllerTheme
import com.francotte.homecontroller.navigation.HomeControllerNavDisplay
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeControllerTheme {
                HomeControllerNavDisplay()
            }
        }
    }
}
```

> Noter le nouvel import du thème : `com.francotte.homecontroller.core.designsystem.theme.HomeControllerTheme`.

- [ ] **Step 4 : Mettre à jour les imports du `NavDisplay`**

`HomeControllerNavDisplay.kt` : les écrans viennent maintenant des features. Mettre à jour :

```kotlin
import com.francotte.homecontroller.feature.devicedetail.DeviceControlScreen
import com.francotte.homecontroller.feature.scan.ScanScreen
```

Le corps (back stack, `entryProvider`, `entry<ScanKey>` / `entry<DeviceControlKey>`) est **inchangé** — `hiltViewModel()` est résolu à l'intérieur des écrans.

- [ ] **Step 5 : Supprimer les résidus dans `:app`**

Supprimer `app/src/main/java/com/francotte/homecontroller/di/` (AppContainer) et tout dossier `presentation/`, `domain/`, `data/` restant dans `:app` (leurs fichiers ont été déplacés). Garder `MainActivity`, `HomeControllerApplication`, `navigation/`.

- [ ] **Step 6 : Build complet + toute la suite de tests**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. Le graphe Hilt de l'app compile ; tous les tests des modules passent.

> Si Hilt se plaint d'un binding manquant pour `EspDeviceClient`/`BleScanner`, vérifier que `:app` dépend bien (transitivement) de `:core:bluetooth` via les features et que `BluetoothModule` est `@InstallIn(SingletonComponent::class)`.

- [ ] **Step 7 : Commit (utilisateur)** — `:app` migré + nettoyage.

---

## Task 9 : Validation manuelle (comportement constant)

**Files:** aucun.

- [ ] **Step 1 : Installer sur le téléphone**

Téléphone branché (débogage USB) :
Run: `./gradlew :app:installDebug`
Expected: `BUILD SUCCESSFUL`, app installée.

- [ ] **Step 2 : Vérifier que RIEN n'a changé**

- [ ] L'app démarre sur l'écran de **scan**.
- [ ] Le scan liste les appareils BLE (nom/Inconnu, MAC, RSSI), liste stable.
- [ ] **Toucher un appareil** → écran de contrôle (titre = adresse), « Connexion en cours… » puis erreur/déconnexion attendue.
- [ ] **Retour** → liste de scan.
- [ ] Permissions/Bluetooth : mêmes écrans qu'avant.

Comportement **identique à l'avant-refactor** = succès.

- [ ] **Step 3 : Commit éventuel (utilisateur)** — ajustements.

---

## Validation croisée avec la spec

- **Graphe de modules** (§4) → Tasks 2–8.
- **Migration fichier par fichier** (§5) → Tasks 2–8 (chaque fichier a sa cible).
- **`:core:bluetooth` interfaces publiques / impls `internal`** (§4) → Task 5.
- **Hilt** (§6) : Application/Activity → Task 8 ; `BluetoothModule` → Task 5 ; `ScanViewModel` → Task 6 ; `DeviceControlViewModel` assisté → Task 7 ; `hiltViewModel()` → Tasks 6–7.
- **Setup Gradle** (§7) : catalogue → Task 1 ; modules Kotlin/JVM vs Android → Tasks 2–8.
- **Suppression AppContainer/factories** (§2) → Tasks 6, 7, 8.
- **Tests verts déplacés** (§8) → Tasks 5–8 ; **validation manuelle** → Task 9.
```
