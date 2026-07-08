# Connexion & contrôle ESP32 (BLE) — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Se connecter à un ESP32 depuis la liste de scan, piloter sa LED intégrée (write) et afficher un compteur poussé en direct (notify), via un profil GATT sur mesure.

**Architecture:** Extension de la Clean Architecture existante avec une couche `connection` (interface `EspDeviceClient` dans domain, implémentation `AndroidEspDeviceClient` via `BluetoothGatt` dans data, `DeviceControlViewModel` dans presentation). Navigation entre l'écran de scan et l'écran de contrôle avec **Navigation 3 (Nav3)**. DI manuelle étendue.

**Tech Stack:** Kotlin, Jetpack Compose, Coroutines/Flow, `android.bluetooth` (GATT), Navigation 3 (alpha), firmware Arduino (`BLEDevice.h` sur ESP32).

**Spec de référence :** `docs/superpowers/specs/2026-07-06-esp32-connexion-design.md`

> ⚠️ **Rappel commits :** l'utilisateur gère lui-même tous les `git commit` / `git push`. **Ne pas committer.** Les étapes « Commit » ci-dessous décrivent le contenu logique d'un commit à titre indicatif — c'est l'utilisateur qui les réalise dans Android Studio quand il le souhaite.

> ⚠️ **Matériel :** les tâches 1 à 11 ne nécessitent PAS l'ESP32 (code + tests JVM). Seule la **Task 12** (flashage + validation end-to-end) exige la carte.

---

## Structure des fichiers

Créés :
- `firmware/homecontroller_esp32/homecontroller_esp32.ino`
- `firmware/README.md`
- `app/src/main/java/com/francotte/homecontroller/domain/connection/EspConnectionState.kt`
- `app/src/main/java/com/francotte/homecontroller/domain/connection/EspDeviceClient.kt`
- `app/src/main/java/com/francotte/homecontroller/domain/connection/CounterCodec.kt`
- `app/src/main/java/com/francotte/homecontroller/data/connection/GattProfile.kt`
- `app/src/main/java/com/francotte/homecontroller/data/connection/AndroidEspDeviceClient.kt`
- `app/src/main/java/com/francotte/homecontroller/presentation/control/ControlUiState.kt`
- `app/src/main/java/com/francotte/homecontroller/presentation/control/DeviceControlViewModel.kt`
- `app/src/main/java/com/francotte/homecontroller/presentation/control/DeviceControlViewModelFactory.kt`
- `app/src/main/java/com/francotte/homecontroller/presentation/control/DeviceControlScreen.kt`
- `app/src/main/java/com/francotte/homecontroller/navigation/NavKeys.kt`
- `app/src/main/java/com/francotte/homecontroller/navigation/HomeControllerNavDisplay.kt`
- `app/src/test/java/com/francotte/homecontroller/domain/connection/CounterCodecTest.kt`
- `app/src/test/java/com/francotte/homecontroller/presentation/control/FakeEspDeviceClient.kt`
- `app/src/test/java/com/francotte/homecontroller/presentation/control/DeviceControlViewModelTest.kt`

Modifiés :
- `gradle/libs.versions.toml`, `app/build.gradle.kts` (dépendances Nav3)
- `app/src/main/java/com/francotte/homecontroller/di/AppContainer.kt` (fabrique du client)
- `app/src/main/java/com/francotte/homecontroller/presentation/scan/ScanScreen.kt` (lignes cliquables)
- `app/src/main/java/com/francotte/homecontroller/MainActivity.kt` (affiche le `NavDisplay`)

---

## Task 1 : Firmware ESP32

**Files:**
- Create: `firmware/homecontroller_esp32/homecontroller_esp32.ino`
- Create: `firmware/README.md`

> Code source uniquement (pas de toolchain Arduino ici). Compilation/flashage = Task 12, sur la carte.

- [ ] **Step 1 : Écrire le sketch**

`firmware/homecontroller_esp32/homecontroller_esp32.ino` :

```cpp
// HomeController — firmware ESP32
// Expose un service BLE : LED (write) + Compteur (notify).
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#define LED_PIN 2  // LED intégrée sur la plupart des ESP32 DevKit ; ajuster si besoin

#define SERVICE_UUID      "990c9205-4132-4360-aa80-2bd5ce8d6e93"
#define LED_CHAR_UUID     "d64abbf2-4dab-4198-8a93-fb7348943972"
#define COUNTER_CHAR_UUID "2a554b2a-5f4b-4c89-9a59-e4d8f6d4b9d8"

BLECharacteristic* counterCharacteristic = nullptr;
bool deviceConnected = false;
uint32_t counter = 0;
unsigned long lastTick = 0;

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* server) override { deviceConnected = true; }
  void onDisconnect(BLEServer* server) override {
    deviceConnected = false;
    server->getAdvertising()->start();  // ré-annonce pour permettre une reconnexion
  }
};

class LedCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* characteristic) override {
    std::string value = characteristic->getValue();
    if (!value.empty()) {
      digitalWrite(LED_PIN, value[0] == 0x01 ? HIGH : LOW);
    }
  }
};

void setup() {
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  BLEDevice::init("HomeController-ESP32");
  BLEServer* server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  BLEService* service = server->createService(SERVICE_UUID);

  BLECharacteristic* ledCharacteristic = service->createCharacteristic(
      LED_CHAR_UUID, BLECharacteristic::PROPERTY_WRITE);
  ledCharacteristic->setCallbacks(new LedCallbacks());

  counterCharacteristic = service->createCharacteristic(
      COUNTER_CHAR_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  counterCharacteristic->addDescriptor(new BLE2902());  // CCCD requis pour notify

  service->start();

  BLEAdvertising* advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setScanResponse(true);
  BLEDevice::startAdvertising();
}

void loop() {
  if (deviceConnected && millis() - lastTick >= 1000) {
    lastTick = millis();
    counter++;
    uint8_t payload[4];  // little-endian
    payload[0] = counter & 0xFF;
    payload[1] = (counter >> 8) & 0xFF;
    payload[2] = (counter >> 16) & 0xFF;
    payload[3] = (counter >> 24) & 0xFF;
    counterCharacteristic->setValue(payload, 4);
    counterCharacteristic->notify();
  }
}
```

- [ ] **Step 2 : Écrire le README de flashage**

`firmware/README.md` :

```markdown
# Firmware HomeController — ESP32

Sketch BLE : caractéristique LED (write) + caractéristique Compteur (notify).

## Prérequis (une seule fois)
1. Installer l'Arduino IDE.
2. Fichier → Préférences → « URL de gestionnaire de cartes supplémentaires » :
   `https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json`
3. Outils → Type de carte → Gestionnaire de cartes → installer **esp32** (Espressif).

## Flasher
1. Ouvrir `homecontroller_esp32/homecontroller_esp32.ino`.
2. Outils → Type de carte → **ESP32 Dev Module** (ou la carte ELEGOO ESP32).
3. Brancher l'ESP32 en USB, sélectionner le **Port**.
   (Si le port n'apparaît pas : installer le pilote USB CP2102 ou CH340.)
4. Cliquer **Téléverser** (flèche).

## Vérifier
- L'ESP32 apparaît dans un scanner BLE sous `HomeController-ESP32`.
- La LED `GPIO 2` répond aux écritures ; le compteur s'incrémente chaque seconde.
- Si la LED intégrée n'est pas sur GPIO 2 sur ta carte : modifier `#define LED_PIN`.
```

- [ ] **Step 3 : Commit (par l'utilisateur)**

Contenu : `firmware/` (sketch + README).

---

## Task 2 : Dépendances Navigation 3

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

> Nav3 est en **alpha** : les versions évoluent. Le Step 3 vérifie la résolution et permet d'ajuster.

- [ ] **Step 1 : Ajouter versions et libs au catalogue**

Dans `gradle/libs.versions.toml`, sous `[versions]`, ajouter :

```toml
navigation3 = "1.0.0-alpha11"
lifecycleViewmodelNav3 = "2.9.4"
```

Sous `[libraries]`, ajouter :

```toml
androidx-navigation3-runtime = { group = "androidx.navigation3", name = "navigation3-runtime", version.ref = "navigation3" }
androidx-navigation3-ui = { group = "androidx.navigation3", name = "navigation3-ui", version.ref = "navigation3" }
androidx-lifecycle-viewmodel-navigation3 = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-navigation3", version.ref = "lifecycleViewmodelNav3" }
```

- [ ] **Step 2 : Déclarer les dépendances**

Dans `app/build.gradle.kts`, dans `dependencies { ... }`, ajouter parmi les `implementation` :

```kotlin
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
```

- [ ] **Step 3 : Vérifier la résolution (et ajuster si alpha obsolète)**

Run: `./gradlew :app:dependencies --configuration debugRuntimeClasspath -q`
Expected: `BUILD SUCCESSFUL`, et `navigation3-runtime` / `navigation3-ui` apparaissent.

> Si une version ne se résout pas : ouvrir la page Maven de `androidx.navigation3` et `androidx.lifecycle:lifecycle-viewmodel-navigation3`, mettre la **dernière version alpha stable** dans le catalogue, relancer. Noter la version retenue.

- [ ] **Step 4 : Commit (par l'utilisateur)**

Contenu : `libs.versions.toml`, `build.gradle.kts` (dépendances Nav3).

---

## Task 3 : `CounterCodec` (TDD)

**Files:**
- Test: `app/src/test/java/com/francotte/homecontroller/domain/connection/CounterCodecTest.kt`
- Create: `app/src/main/java/com/francotte/homecontroller/domain/connection/CounterCodec.kt`

- [ ] **Step 1 : Écrire les tests (qui échouent)**

`CounterCodecTest.kt` :

```kotlin
package com.francotte.homecontroller.domain.connection

import org.junit.Assert.assertEquals
import org.junit.Test

class CounterCodecTest {

    @Test
    fun `decode 42 en little-endian`() {
        assertEquals(42, CounterCodec.decode(byteArrayOf(0x2A, 0x00, 0x00, 0x00)))
    }

    @Test
    fun `decode une valeur multi-octets`() {
        // 0x00010203 = 66051, en little-endian : 03 02 01 00
        assertEquals(66051, CounterCodec.decode(byteArrayOf(0x03, 0x02, 0x01, 0x00)))
    }

    @Test
    fun `decode gere les octets hauts sans signe`() {
        // 200 = 0xC8 ; un Byte vaut -56, il faut le lire non signé
        assertEquals(200, CounterCodec.decode(byteArrayOf(0xC8.toByte(), 0x00, 0x00, 0x00)))
    }

    @Test
    fun `decode renvoie zero pour un tableau trop court`() {
        assertEquals(0, CounterCodec.decode(byteArrayOf(0x01)))
    }
}
```

- [ ] **Step 2 : Lancer, vérifier l'échec**

Run: `./gradlew :app:testDebugUnitTest --tests "com.francotte.homecontroller.domain.connection.CounterCodecTest"`
Expected: FAIL (compilation : `CounterCodec` inexistant).

- [ ] **Step 3 : Implémenter**

`CounterCodec.kt` :

```kotlin
package com.francotte.homecontroller.domain.connection

/** Décodage du compteur ESP32 : 4 octets little-endian → Int. */
object CounterCodec {
    fun decode(bytes: ByteArray): Int {
        if (bytes.size < 4) return 0
        return (bytes[0].toInt() and 0xFF) or
            ((bytes[1].toInt() and 0xFF) shl 8) or
            ((bytes[2].toInt() and 0xFF) shl 16) or
            ((bytes[3].toInt() and 0xFF) shl 24)
    }
}
```

- [ ] **Step 4 : Lancer, vérifier le succès**

Run: `./gradlew :app:testDebugUnitTest --tests "com.francotte.homecontroller.domain.connection.CounterCodecTest"`
Expected: PASS (4 tests).

- [ ] **Step 5 : Commit (par l'utilisateur)** — `CounterCodec` + test.

---

## Task 4 : Contrat de connexion (domain)

**Files:**
- Create: `app/src/main/java/com/francotte/homecontroller/domain/connection/EspConnectionState.kt`
- Create: `app/src/main/java/com/francotte/homecontroller/domain/connection/EspDeviceClient.kt`

> Sealed interface + interface : pas de logique, pas de test dédié (exercés par les tests du ViewModel, Task 6).

- [ ] **Step 1 : Créer `EspConnectionState`**

```kotlin
package com.francotte.homecontroller.domain.connection

/** État d'une session de connexion à un appareil ESP32. */
sealed interface EspConnectionState {
    data object Connecting : EspConnectionState
    data object Connected : EspConnectionState
    data object Disconnected : EspConnectionState
    data class Error(val message: String) : EspConnectionState
}
```

- [ ] **Step 2 : Créer `EspDeviceClient`**

```kotlin
package com.francotte.homecontroller.domain.connection

import kotlinx.coroutines.flow.Flow

/**
 * Client de connexion à un ESP32 exposant le profil HomeController.
 * Une instance = une session. L'implémentation vit dans la couche data.
 */
interface EspDeviceClient {
    val state: Flow<EspConnectionState>
    val counter: Flow<Int>
    fun connect(address: String)
    fun disconnect()
    suspend fun setLed(on: Boolean)
}
```

- [ ] **Step 3 : Vérifier la compilation**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4 : Commit (par l'utilisateur)** — les deux fichiers domain/connection.

---

## Task 5 : `ControlUiState` (presentation)

**Files:**
- Create: `app/src/main/java/com/francotte/homecontroller/presentation/control/ControlUiState.kt`

> Note de conception (simplification DRY vs spéc) : la spéc mentionnait un type `ConnectionStatus` distinct. On **réutilise directement `EspConnectionState`** dans l'UI state pour éviter un type miroir redondant — même ensemble de cas, un seul type à maintenir.

- [ ] **Step 1 : Créer `ControlUiState`**

```kotlin
package com.francotte.homecontroller.presentation.control

import com.francotte.homecontroller.domain.connection.EspConnectionState

/** État de l'écran de contrôle d'un appareil. */
data class ControlUiState(
    val connection: EspConnectionState = EspConnectionState.Connecting,
    val counter: Int? = null,          // dernière valeur reçue, null tant qu'aucune notif
    val ledOn: Boolean = false,        // état voulu de la LED (optimiste)
    val transientError: String? = null // message transitoire (ex. échec d'écriture LED)
)
```

- [ ] **Step 2 : Vérifier la compilation**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3 : Commit (par l'utilisateur)** — `ControlUiState`.

---

## Task 6 : `DeviceControlViewModel` (TDD)

**Files:**
- Create: `app/src/test/java/com/francotte/homecontroller/presentation/control/FakeEspDeviceClient.kt`
- Test: `app/src/test/java/com/francotte/homecontroller/presentation/control/DeviceControlViewModelTest.kt`
- Create: `app/src/main/java/com/francotte/homecontroller/presentation/control/DeviceControlViewModel.kt`

- [ ] **Step 1 : Créer le faux client**

`FakeEspDeviceClient.kt` :

```kotlin
package com.francotte.homecontroller.presentation.control

import com.francotte.homecontroller.domain.connection.EspConnectionState
import com.francotte.homecontroller.domain.connection.EspDeviceClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeEspDeviceClient : EspDeviceClient {
    val stateFlow = MutableStateFlow<EspConnectionState>(EspConnectionState.Connecting)
    val counterFlow = MutableSharedFlow<Int>(replay = 0, extraBufferCapacity = 8)

    override val state = stateFlow
    override val counter = counterFlow

    var connectedAddress: String? = null
    var disconnectCalled = false
    val ledWrites = mutableListOf<Boolean>()
    var failLed = false

    override fun connect(address: String) { connectedAddress = address }
    override fun disconnect() { disconnectCalled = true }
    override suspend fun setLed(on: Boolean) {
        if (failLed) throw RuntimeException("échec simulé")
        ledWrites.add(on)
    }
}
```

- [ ] **Step 2 : Écrire les tests (qui échouent)**

`DeviceControlViewModelTest.kt` :

```kotlin
package com.francotte.homecontroller.presentation.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.francotte.homecontroller.MainDispatcherRule
import com.francotte.homecontroller.domain.connection.EspConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceControlViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `connecte a l adresse au demarrage`() {
        val fake = FakeEspDeviceClient()
        DeviceControlViewModel("AA:BB:CC", fake)
        assertEquals("AA:BB:CC", fake.connectedAddress)
    }

    @Test
    fun `reflete l etat de connexion`() = runTest {
        val fake = FakeEspDeviceClient()
        val vm = DeviceControlViewModel("AA:BB:CC", fake)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }

        fake.stateFlow.value = EspConnectionState.Connected
        runCurrent()

        assertEquals(EspConnectionState.Connected, vm.uiState.value.connection)
    }

    @Test
    fun `reflete la valeur du compteur`() = runTest {
        val fake = FakeEspDeviceClient()
        val vm = DeviceControlViewModel("AA:BB:CC", fake)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }

        fake.counterFlow.emit(42)
        runCurrent()

        assertEquals(42, vm.uiState.value.counter)
    }

    @Test
    fun `onLedToggle ecrit la LED et met a jour l etat`() = runTest {
        val fake = FakeEspDeviceClient()
        val vm = DeviceControlViewModel("AA:BB:CC", fake)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }

        vm.onLedToggle(true)
        advanceUntilIdle()

        assertEquals(listOf(true), fake.ledWrites)
        assertTrue(vm.uiState.value.ledOn)
    }

    @Test
    fun `un echec d ecriture LED annule la bascule et remonte une erreur`() = runTest {
        val fake = FakeEspDeviceClient().apply { failLed = true }
        val vm = DeviceControlViewModel("AA:BB:CC", fake)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }

        vm.onLedToggle(true)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.ledOn)
        assertNotNull(vm.uiState.value.transientError)
    }

    @Test
    fun `deconnecte quand le ViewModel est detruit`() {
        val fake = FakeEspDeviceClient()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DeviceControlViewModel("AA:BB:CC", fake) as T
        }
        val store = ViewModelStore()
        ViewModelProvider(store, factory)[DeviceControlViewModel::class.java]

        store.clear() // déclenche onCleared()

        assertTrue(fake.disconnectCalled)
    }
}
```

- [ ] **Step 3 : Lancer, vérifier l'échec**

Run: `./gradlew :app:testDebugUnitTest --tests "com.francotte.homecontroller.presentation.control.DeviceControlViewModelTest"`
Expected: FAIL (compilation : `DeviceControlViewModel` inexistant).

- [ ] **Step 4 : Implémenter le ViewModel**

`DeviceControlViewModel.kt` :

```kotlin
package com.francotte.homecontroller.presentation.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.francotte.homecontroller.domain.connection.EspConnectionState
import com.francotte.homecontroller.domain.connection.EspDeviceClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Pilote une session de connexion à un ESP32 : expose l'état, le compteur,
 * et commande la LED (avec retour optimiste). Sans dépendance Android.
 */
class DeviceControlViewModel(
    private val address: String,
    private val client: EspDeviceClient
) : ViewModel() {

    private val ledOn = MutableStateFlow(false)
    private val transientError = MutableStateFlow<String?>(null)

    val uiState = combine(
        client.state,
        client.counter.map<Int, Int?> { it }.onStart { emit(null) },
        ledOn,
        transientError
    ) { connection, counter, led, error ->
        ControlUiState(connection = connection, counter = counter, ledOn = led, transientError = error)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ControlUiState()
    )

    init {
        client.connect(address)
    }

    fun onLedToggle(on: Boolean) {
        ledOn.value = on            // optimiste : retour visuel immédiat
        transientError.value = null
        viewModelScope.launch {
            try {
                client.setLed(on)
            } catch (t: Throwable) {
                ledOn.value = !on   // échec → on revient en arrière
                transientError.value = "Échec de l'écriture de la LED"
            }
        }
    }

    /** Relance la connexion (bouton Réessayer / Reconnecter). */
    fun onRetry() {
        client.connect(address)
    }

    override fun onCleared() {
        client.disconnect()
    }
}
```

- [ ] **Step 5 : Lancer, vérifier le succès**

Run: `./gradlew :app:testDebugUnitTest --tests "com.francotte.homecontroller.presentation.control.DeviceControlViewModelTest"`
Expected: PASS (6 tests).

- [ ] **Step 6 : Commit (par l'utilisateur)** — ViewModel + fake + tests.

---

## Task 7 : `AndroidEspDeviceClient` (data, GATT)

**Files:**
- Create: `app/src/main/java/com/francotte/homecontroller/data/connection/GattProfile.kt`
- Create: `app/src/main/java/com/francotte/homecontroller/data/connection/AndroidEspDeviceClient.kt`

> Dépend de `BluetoothGatt` (framework) → non testé unitairement. Gardé aussi fin que possible, validé au Task 12.

- [ ] **Step 1 : Créer les constantes du profil**

`GattProfile.kt` :

```kotlin
package com.francotte.homecontroller.data.connection

import java.util.UUID

/** UUID du profil GATT HomeController (identiques au firmware ESP32). */
object GattProfile {
    val SERVICE_UUID: UUID = UUID.fromString("990c9205-4132-4360-aa80-2bd5ce8d6e93")
    val LED_UUID: UUID = UUID.fromString("d64abbf2-4dab-4198-8a93-fb7348943972")
    val COUNTER_UUID: UUID = UUID.fromString("2a554b2a-5f4b-4c89-9a59-e4d8f6d4b9d8")
    // Descripteur standard Client Characteristic Configuration (active les notifications).
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
```

- [ ] **Step 2 : Implémenter le client GATT**

`AndroidEspDeviceClient.kt` :

```kotlin
package com.francotte.homecontroller.data.connection

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresPermission
import com.francotte.homecontroller.domain.connection.CounterCodec
import com.francotte.homecontroller.domain.connection.EspConnectionState
import com.francotte.homecontroller.domain.connection.EspDeviceClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeout

/** Implémentation Android de [EspDeviceClient] via BluetoothGatt. */
class AndroidEspDeviceClient(
    private val context: Context
) : EspDeviceClient {

    private val adapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    private val _state = MutableStateFlow<EspConnectionState>(EspConnectionState.Disconnected)
    override val state = _state

    private val _counter = MutableSharedFlow<Int>(replay = 0, extraBufferCapacity = 16)
    override val counter = _counter

    private var gatt: BluetoothGatt? = null
    private var ledCharacteristic: BluetoothGattCharacteristic? = null
    private var pendingLedWrite: CompletableDeferred<Unit>? = null

    private val callback = object : android.bluetooth.BluetoothGattCallback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = EspConnectionState.Error("Connexion échouée (code $status)")
                closeGatt()
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _state.value = EspConnectionState.Disconnected
                    closeGatt()
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(GattProfile.SERVICE_UUID)
            val led = service?.getCharacteristic(GattProfile.LED_UUID)
            val counterChar = service?.getCharacteristic(GattProfile.COUNTER_UUID)
            if (service == null || led == null || counterChar == null) {
                _state.value =
                    EspConnectionState.Error("Ce périphérique n'expose pas le profil HomeController")
                g.disconnect()
                return
            }
            ledCharacteristic = led
            enableCounterNotifications(g, counterChar)
            _state.value = EspConnectionState.Connected
        }

        // API < 33
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            if (ch.uuid == GattProfile.COUNTER_UUID) {
                _counter.tryEmit(CounterCodec.decode(ch.value))
            }
        }

        // API >= 33
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (ch.uuid == GattProfile.COUNTER_UUID) {
                _counter.tryEmit(CounterCodec.decode(value))
            }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (ch.uuid != GattProfile.LED_UUID) return
            val deferred = pendingLedWrite ?: return
            pendingLedWrite = null
            if (status == BluetoothGatt.GATT_SUCCESS) {
                deferred.complete(Unit)
            } else {
                deferred.completeExceptionally(IllegalStateException("Écriture LED refusée (code $status)"))
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun connect(address: String) {
        val device = adapter?.getRemoteDevice(address)
        if (device == null) {
            _state.value = EspConnectionState.Error("Adaptateur Bluetooth indisponible")
            return
        }
        closeGatt()
        _state.value = EspConnectionState.Connecting
        gatt = device.connectGatt(context, false, callback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun disconnect() {
        gatt?.disconnect()
        closeGatt()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun setLed(on: Boolean) {
        val g = gatt ?: throw IllegalStateException("Non connecté")
        val ch = ledCharacteristic ?: throw IllegalStateException("Caractéristique LED absente")
        val value = byteArrayOf(if (on) 0x01 else 0x00)
        val deferred = CompletableDeferred<Unit>()
        pendingLedWrite = deferred
        if (!writeLed(g, ch, value)) {
            pendingLedWrite = null
            throw IllegalStateException("Écriture LED refusée par la pile Bluetooth")
        }
        withTimeout(5_000) { deferred.await() }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun enableCounterNotifications(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
        g.setCharacteristicNotification(ch, true)
        val cccd = ch.getDescriptor(GattProfile.CCCD_UUID) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            g.writeDescriptor(cccd)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun writeLed(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(ch, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                ch.value = value
                ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                g.writeCharacteristic(ch)
            }
        }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun closeGatt() {
        gatt?.close()
        gatt = null
        ledCharacteristic = null
    }
}
```

- [ ] **Step 3 : Vérifier la compilation**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4 : Commit (par l'utilisateur)** — `GattProfile` + `AndroidEspDeviceClient`.

---

## Task 8 : DI — fabrique du client + factory du ViewModel

**Files:**
- Modify: `app/src/main/java/com/francotte/homecontroller/di/AppContainer.kt`
- Create: `app/src/main/java/com/francotte/homecontroller/presentation/control/DeviceControlViewModelFactory.kt`

- [ ] **Step 1 : Étendre `AppContainer`**

Ajouter dans `AppContainer` (garder l'existant) une fabrique renvoyant une **nouvelle instance par session** :

```kotlin
// imports à ajouter en tête de fichier :
// import com.francotte.homecontroller.data.connection.AndroidEspDeviceClient
// import com.francotte.homecontroller.domain.connection.EspDeviceClient

    private val appContext = context.applicationContext

    /** Nouvelle session de connexion à chaque appel. */
    fun createEspDeviceClient(): EspDeviceClient = AndroidEspDeviceClient(appContext)
```

> `context` est déjà le paramètre du constructeur `AppContainer(context: Context)`. Ajouter `appContext` et la méthode `createEspDeviceClient()` à la classe.

- [ ] **Step 2 : Créer la factory du ViewModel**

`DeviceControlViewModelFactory.kt` :

```kotlin
package com.francotte.homecontroller.presentation.control

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.francotte.homecontroller.HomeControllerApplication

/** Construit un [DeviceControlViewModel] pour une adresse donnée. */
fun deviceControlViewModelFactory(address: String) = viewModelFactory {
    initializer {
        val app = this[APPLICATION_KEY] as HomeControllerApplication
        DeviceControlViewModel(address, app.container.createEspDeviceClient())
    }
}
```

- [ ] **Step 3 : Vérifier la compilation**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4 : Commit (par l'utilisateur)** — `AppContainer` + factory.

---

## Task 9 : `DeviceControlScreen` (UI)

**Files:**
- Create: `app/src/main/java/com/francotte/homecontroller/presentation/control/DeviceControlScreen.kt`

> UI Compose : validée manuellement (Task 12).

- [ ] **Step 1 : Créer l'écran**

`DeviceControlScreen.kt` :

```kotlin
package com.francotte.homecontroller.presentation.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.francotte.homecontroller.domain.connection.EspConnectionState

@Composable
fun DeviceControlScreen(
    address: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeviceControlViewModel = viewModel(factory = deviceControlViewModelFactory(address))
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = address, style = MaterialTheme.typography.titleMedium)

            when (val connection = uiState.connection) {
                EspConnectionState.Connecting ->
                    Text("Connexion en cours…")

                EspConnectionState.Connected -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("LED")
                        Switch(
                            checked = uiState.ledOn,
                            onCheckedChange = { viewModel.onLedToggle(it) }
                        )
                    }
                    Text("Compteur : ${uiState.counter ?: "—"}")
                    uiState.transientError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }

                EspConnectionState.Disconnected -> {
                    Text("Déconnecté.")
                    Button(onClick = { viewModel.onRetry() }) { Text("Reconnecter") }
                }

                is EspConnectionState.Error -> {
                    Text(connection.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.onRetry() }) { Text("Réessayer") }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = onBack) { Text("Retour") }
        }
    }
}
```

> `MaterialTheme.colorScheme` nécessite l'import `androidx.compose.material3.MaterialTheme` (déjà présent) — `colorScheme` en est une propriété.

- [ ] **Step 2 : Vérifier la compilation**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3 : Commit (par l'utilisateur)** — `DeviceControlScreen`.

---

## Task 10 : Rendre les lignes de scan cliquables

**Files:**
- Modify: `app/src/main/java/com/francotte/homecontroller/presentation/scan/ScanScreen.kt`

- [ ] **Step 1 : Ajouter le paramètre `onDeviceClick` et le propager**

Modifier la signature de `ScanScreen` :

```kotlin
@Composable
fun ScanScreen(
    onDeviceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = viewModel(factory = ScanViewModelFactory)
) {
```

Dans le `when`, remplacer l'appel `DeviceList(state.devices)` par :

```kotlin
                        DeviceList(state.devices, onDeviceClick)
```

Remplacer `DeviceList` et `DeviceRow` par :

```kotlin
@Composable
private fun DeviceList(devices: List<BleDevice>, onDeviceClick: (String) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(devices, key = { it.address }) { device ->
            DeviceRow(device, onClick = { onDeviceClick(device.address) })
        }
    }
}

@Composable
private fun DeviceRow(device: BleDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = device.name ?: "Inconnu",
                style = MaterialTheme.typography.titleMedium
            )
            Text(text = device.address, style = MaterialTheme.typography.bodySmall)
            Text(text = "${device.rssi} dBm", style = MaterialTheme.typography.bodySmall)
        }
    }
}
```

Ajouter l'import en tête de fichier :

```kotlin
import androidx.compose.foundation.clickable
```

- [ ] **Step 2 : Vérifier la compilation**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: `BUILD SUCCESSFUL`. (`MainActivity` ne compile plus encore : `ScanScreen` exige désormais `onDeviceClick` — corrigé au Task 11.)

> Si tu veux un check isolé de ce fichier avant Task 11, c'est attendu que `MainActivity` casse ; on le règle juste après.

- [ ] **Step 3 : Commit (par l'utilisateur)** — `ScanScreen` (fait de préférence avec le Task 11 pour garder le projet compilable).

---

## Task 11 : Navigation Nav3 + branchement `MainActivity`

**Files:**
- Create: `app/src/main/java/com/francotte/homecontroller/navigation/NavKeys.kt`
- Create: `app/src/main/java/com/francotte/homecontroller/navigation/HomeControllerNavDisplay.kt`
- Modify: `app/src/main/java/com/francotte/homecontroller/MainActivity.kt`

> ⚠️ **Zone alpha Nav3.** Le code ci-dessous suit le modèle Nav3 connu (back stack = liste observable, `NavDisplay`, `entryProvider`, décorateur de scoping ViewModel). **Avant d'implémenter, ouvrir la doc/le sample officiel Navigation 3 correspondant à la version épinglée (Task 2)** et aligner les noms exacts (`entryProvider`, `entry`, `rememberViewModelStoreNavEntryDecorator`, signature de `NavDisplay`). Le compilateur est ton filet : ajuster jusqu'à `BUILD SUCCESSFUL`.

- [ ] **Step 1 : Créer les clés de navigation**

`NavKeys.kt` :

```kotlin
package com.francotte.homecontroller.navigation

import androidx.navigation3.runtime.NavKey

data object ScanKey : NavKey

data class DeviceControlKey(val address: String) : NavKey
```

- [ ] **Step 2 : Créer le `NavDisplay`**

`HomeControllerNavDisplay.kt` :

```kotlin
package com.francotte.homecontroller.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
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
```

- [ ] **Step 3 : Brancher dans `MainActivity`**

Remplacer le contenu de `MainActivity.kt` par :

```kotlin
package com.francotte.homecontroller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.francotte.homecontroller.navigation.HomeControllerNavDisplay
import com.francotte.homecontroller.ui.theme.HomeControllerTheme

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

- [ ] **Step 4 : Compiler + lancer toute la suite de tests**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. Si les imports Nav3 diffèrent de la version épinglée, ajuster selon la doc officielle jusqu'au succès.

- [ ] **Step 5 : Commit (par l'utilisateur)** — navigation Nav3 + `MainActivity` (+ `ScanScreen` du Task 10).

---

## Task 12 : Validation end-to-end (ESP32 requis)

**Files:** aucun (matériel + manuel).

- [ ] **Step 1 : Flasher l'ESP32**

Suivre `firmware/README.md` : installer le support ESP32 dans l'Arduino IDE, sélectionner **ESP32 Dev Module** + le port, **Téléverser** `homecontroller_esp32.ino`.
Attendu : téléversement réussi.

> Si la LED intégrée de la carte ELEGOO n'est pas sur `GPIO 2`, ajuster `#define LED_PIN` et re-flasher.

- [ ] **Step 2 : Installer l'app**

Téléphone branché (débogage USB) :
Run: `./gradlew :app:installDebug`
Expected: `BUILD SUCCESSFUL`, app installée.

- [ ] **Step 3 : Dérouler la checklist**

- [ ] L'ESP32 apparaît dans le scan sous **`HomeController-ESP32`**.
- [ ] Toucher la ligne → écran de contrôle, statut **Connexion en cours…** puis **connecté**.
- [ ] L'interrupteur **LED** allume/éteint la **vraie LED** de la carte.
- [ ] Le **compteur s'incrémente** (~1/s) dans l'app.
- [ ] Couper l'ESP32 (débrancher) → l'app passe à **Déconnecté** avec **Reconnecter**.
- [ ] Bouton **Retour** → retour au scan ; l'ESP32 redevient scannable (déconnexion propre).
- [ ] Rebrancher, re-scanner, se reconnecter → tout refonctionne.

- [ ] **Step 4 : Commit éventuel (par l'utilisateur)** — ajustements (ex. `LED_PIN`).

---

## Validation croisée avec la spec

- **Firmware + profil GATT** (§4) → Task 1 (UUID identiques côté app : Task 7 `GattProfile`).
- **Connexion / découverte / notifications** (§5.2) → Task 7.
- **`EspDeviceClient` / états** (§5.1) → Task 4 ; **`CounterCodec`** → Task 3.
- **`DeviceControlViewModel` + `ControlUiState`, LED optimiste** (§5.3, §8) → Tasks 5, 6.
- **DI manuelle étendue** (§5.4) → Task 8.
- **Écran de contrôle** (§5.3) → Task 9.
- **Navigation Nav3 + clic sur une ligne** (§6) → Tasks 10, 11.
- **Flux de données** (§7) → Tasks 6, 7.
- **Gestion d'erreurs** (§8) → Tasks 6 (LED/échec), 7 (connexion/profil/déconnexion), 9 (UI Réessayer/Reconnecter).
- **Tests** (§9) → Tasks 3, 6 ; non-testable validé aux Tasks 7/11/12.
- **Note ConnectionStatus** : simplifié en réutilisant `EspConnectionState` (Task 5).
```
