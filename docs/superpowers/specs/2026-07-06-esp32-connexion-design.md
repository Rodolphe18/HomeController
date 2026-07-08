# HomeController — Connexion & contrôle ESP32 (BLE) — Spécification de conception

- **Date** : 2026-07-06
- **Projet** : HomeController (app Android, Kotlin, Jetpack Compose)
- **Package** : `com.francotte.homecontroller`
- **Statut** : Validé, prêt pour le plan d'implémentation
- **Fait suite à** : `2026-07-05-ble-scan-design.md` (fonctionnalité de scan, terminée et fusionnée)

## 1. Objectif

Premier incrément d'**interaction** avec l'ESP32 : depuis la liste de scan, se **connecter** à un appareil, puis, sur un écran de contrôle dédié, **piloter la LED intégrée** (commande app → ESP32) et **afficher un compteur** poussé par l'ESP32 (flux ESP32 → app). L'incrément couvre donc les deux sens de communication BLE autour d'un objectif concret et sans câblage.

On conçoit **les deux côtés** : le firmware ESP32 (sketch Arduino) et l'app Android, qui partagent le même profil GATT.

### Hors périmètre (incréments ultérieurs)

- Écrans de contrôle plus riches / plusieurs objets.
- Capteurs réels du kit (potentiomètre, température…) en remplacement du compteur.
- Reconnexion automatique / gestion de plusieurs connexions simultanées.
- Migration vers Hilt (prévue comme incrément dédié après celui-ci).
- Persistance (appareils favoris, historique).

## 2. Critères de succès

- L'ESP32 flashé apparaît dans le scan sous le nom `HomeController-ESP32`.
- Toucher un appareil dans la liste ouvre un écran de contrôle et lance la connexion.
- L'interrupteur LED de l'écran allume/éteint la **vraie LED** de la carte.
- Le **compteur s'incrémente en direct** dans l'app (notifications BLE).
- Quitter l'écran (retour) **déconnecte proprement** (l'ESP32 redevient disponible au scan).
- Les erreurs sont gérées : échec de connexion, profil absent, déconnexion, échec d'écriture.

## 3. Décisions d'architecture (rappel des choix validés)

- **Firmware conçu sur mesure** : profil GATT défini par nous (§4).
- **Navigation** : **Navigation 3 (Nav3)** — back stack = état possédé, `NavDisplay` (§6).
- **Injection de dépendances** : **DI manuelle** conservée pour cet incrément (extension de `AppContainer`). Migration Hilt = incrément séparé ultérieur. Motif : isoler la nouveauté sur Nav3.
- **Modèle réactif** : Kotlin `Flow` / `StateFlow`, cohérent avec la fonctionnalité de scan.
- **Clean Architecture** : couches `domain` / `data` / `presentation`, dépendances vers l'intérieur.

## 4. Le contrat : firmware ESP32 + profil GATT

### 4.1 Firmware

- Rangé dans le dépôt : `firmware/homecontroller_esp32/homecontroller_esp32.ino` + `firmware/README.md` (procédure de flashage).
- **Carte** : ESP32 Dev Module. **LED intégrée** : `#define LED_PIN 2` (ajustable selon la carte).
- **Librairie** : BLE intégrée au core ESP32 (`BLEDevice.h`), livrée avec le package de cartes ESP32 (aucune installation supplémentaire). Alternative future : NimBLE-Arduino.
- **Nom diffusé** : `HomeController-ESP32`. Le firmware **annonce son service UUID** dans l'advertising.
- **Comportement** :
  - Au boot : init BLE, publication du service, démarrage de l'advertising.
  - Sur write LED : `digitalWrite(LED_PIN, valeur)`.
  - Toutes les 1000 ms : incrémente le compteur ; si un client est abonné, `notify`.

### 4.2 Profil GATT (UUID figés)

| Élément | UUID | Propriétés | Format |
|---|---|---|---|
| Service `HomeController` | `990c9205-4132-4360-aa80-2bd5ce8d6e93` | — | — |
| Caractéristique **LED** | `d64abbf2-4dab-4198-8a93-fb7348943972` | Write (avec réponse) | 1 octet : `0x00` = éteint, `0x01` = allumé |
| Caractéristique **Compteur** | `2a554b2a-5f4b-4c89-9a59-e4d8f6d4b9d8` | Notify | entier 32 bits **little-endian** (4 octets), +1 chaque seconde |

Les mêmes constantes UUID sont déclarées côté firmware **et** côté app (source unique de vérité documentée ici).

## 5. Architecture app — couches

```
com.francotte.homecontroller
├── domain/
│   ├── model/BleDevice.kt                    (existant)
│   └── connection/
│       ├── EspDeviceClient.kt                ← interface (contrat de connexion)
│       ├── EspConnectionState.kt             ← sealed interface d'état
│       └── CounterCodec.kt                   ← parseur pur 4 octets LE → Int
│
├── data/
│   ├── scan/AndroidBleScanner.kt             (existant)
│   └── connection/AndroidEspDeviceClient.kt  ← implémente via BluetoothGatt
│
├── presentation/
│   ├── scan/…                                (existant ; lignes rendues cliquables)
│   └── control/
│       ├── ControlUiState.kt
│       ├── DeviceControlViewModel.kt
│       ├── DeviceControlViewModelFactory.kt
│       └── DeviceControlScreen.kt
│
├── navigation/
│   ├── NavKeys.kt                            ← ScanKey, DeviceControlKey(address)
│   └── HomeControllerNavDisplay.kt           ← NavDisplay + back stack
│
└── di/AppContainer.kt                        (existant ; + fabrique du client)
```

### 5.1 Domain

**`EspDeviceClient`** — contrat de connexion à usage d'une session :

```kotlin
interface EspDeviceClient {
    val state: Flow<EspConnectionState>   // Connecting / Connected / Disconnected / Error
    val counter: Flow<Int>                // notifications du compteur ESP32
    fun connect(address: String)
    fun disconnect()
    suspend fun setLed(on: Boolean)       // écrit la caractéristique LED
}
```

**`EspConnectionState`** :

```kotlin
sealed interface EspConnectionState {
    data object Connecting : EspConnectionState
    data object Connected : EspConnectionState
    data object Disconnected : EspConnectionState
    data class Error(val message: String) : EspConnectionState
}
```

**`CounterCodec`** — logique pure, testable isolément :

```kotlin
object CounterCodec {
    /** Convertit 4 octets little-endian en Int. */
    fun decode(bytes: ByteArray): Int
}
```

### 5.2 Data

**`AndroidEspDeviceClient(context)`** implémente `EspDeviceClient` via `BluetoothGatt` + `BluetoothGattCallback`. Détient un `MutableStateFlow<EspConnectionState>` et un flow pour le compteur, mis à jour depuis les callbacks. Séquence :

1. `connect(address)` → `adapter.getRemoteDevice(address).connectGatt(context, autoConnect = false, callback)` → état `Connecting`.
2. `onConnectionStateChange(CONNECTED)` → `gatt.discoverServices()`.
3. `onServicesDiscovered` → retrouve le service + les 2 caractéristiques par UUID ; **active les notifications** du compteur (`setCharacteristicNotification` + écriture du descripteur CCCD `ENABLE_NOTIFICATION_VALUE`) → état `Connected`. Si le service/les caractéristiques sont absents → état `Error("Ce périphérique n'expose pas le profil HomeController")` puis déconnexion.
4. `onCharacteristicChanged` (compteur) → `CounterCodec.decode(...)` → émet sur `counter`.
5. `onConnectionStateChange(DISCONNECTED)` → état `Disconnected` + `gatt.close()`.
6. `setLed(on)` : `suspend` ; écrit `0x00/0x01` sur la caractéristique LED et attend `onCharacteristicWrite` (gestion des API d'écriture selon la version Android : `writeCharacteristic(char, value, type)` sur Android 13+, ancienne API sinon).

Reste **volontairement fin** (non testable unitairement) — validé sur matériel.

### 5.3 Presentation

**`ControlUiState`** :

```kotlin
data class ControlUiState(
    val status: ConnectionStatus,   // Connecting / Connected / Disconnected / Error(message)
    val counter: Int?,              // dernière valeur, null tant qu'aucune notification
    val ledOn: Boolean              // état voulu de la LED (optimiste)
)
```

`ConnectionStatus` reflète `EspConnectionState` côté UI (mêmes cas, `Error` porte un message).

**`DeviceControlViewModel(address, client)`** :

- Au démarrage : `client.connect(address)`.
- Combine `client.state` + `client.counter` en un `StateFlow<ControlUiState>`.
- `onLedToggle(on)` : passe `ledOn = on` immédiatement (optimiste), puis `viewModelScope.launch { client.setLed(on) }` ; en cas d'échec → **revient à l'état précédent** + message transitoire.
- `onCleared()` : `client.disconnect()`.
- Sans dépendance Android → testable avec un `FakeEspDeviceClient`.

**`DeviceControlScreen(address, onBack)`** : observe l'état via `collectAsStateWithLifecycle` ; affiche le statut de connexion, un **interrupteur LED**, la **valeur du compteur**, une action **retour** ; états `Error`/`Disconnected` proposent **Réessayer**/**Reconnecter**. Aucune logique métier.

**`DeviceControlViewModelFactory`** : construit le ViewModel à partir de l'adresse (issue de `DeviceControlKey`) + du client fourni par `AppContainer`.

### 5.4 DI

**`AppContainer`** : ajoute une fabrique `fun createEspDeviceClient(): EspDeviceClient` renvoyant un `AndroidEspDeviceClient(applicationContext)` (nouvelle instance par session de connexion).

## 6. Navigation (Nav3)

**Clés** (`navigation/NavKeys.kt`) :

```kotlin
sealed interface NavKey
data object ScanKey : NavKey
data class DeviceControlKey(val address: String) : NavKey
```

**`NavDisplay`** (`navigation/HomeControllerNavDisplay.kt`), modèle :

```kotlin
val backStack = rememberNavBackStack(ScanKey)   // liste observable ; sommet = écran affiché

NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider = entryProvider {
        entry<ScanKey> {
            ScanScreen(onDeviceClick = { address -> backStack.add(DeviceControlKey(address)) })
        }
        entry<DeviceControlKey> { key ->
            DeviceControlScreen(address = key.address, onBack = { backStack.removeLastOrNull() })
        }
    }
)
```

**Flux utilisateur** : `ScanScreen` (sommet initial) → clic sur une ligne (les lignes deviennent cliquables via un callback `onDeviceClick(address)`) → empilement de `DeviceControlKey(address)` → `DeviceControlScreen` ; retour → dépilement.

**Scoping ViewModel + déconnexion auto** : les décorateurs d'entrée Nav3 (`androidx.lifecycle:lifecycle-viewmodel-navigation3`) scopent le `DeviceControlViewModel` à l'entrée. Au dépilement, l'entrée est détruite → `onCleared()` → `disconnect()` automatique.

**Note alpha** : les noms exacts d'artefacts/fonctions Nav3 (`rememberNavBackStack`, `entryProvider`, décorateurs de scoping) et leurs versions seront **vérifiés et épinglés à l'implémentation**. Le modèle « back stack = état + `NavDisplay` » est stable.

## 7. Flux de données

**Montant (compteur, ESP32 → app)** :
```
notify (4 octets LE) → onCharacteristicChanged → CounterCodec.decode → counter: Flow<Int>
   → DeviceControlViewModel (combine) → StateFlow<ControlUiState> → DeviceControlScreen
```

**Descendant (LED, app → ESP32)** :
```
interrupteur → VM.onLedToggle(on) [ledOn := on, optimiste]
   → viewModelScope.launch { client.setLed(on) }
   → AndroidEspDeviceClient.writeCharacteristic(LED, 0x00/0x01) → BluetoothGatt → ESP32
```

## 8. Gestion d'erreurs

| Situation | Comportement |
|---|---|
| Échec de connexion (statut GATT ≠ succès, timeout, **erreur 133**) | État `Error` + **Réessayer**. |
| Profil absent (service/caractéristiques introuvables) | État `Error` : « Ce périphérique n'expose pas le profil HomeController ». |
| Déconnexion en cours de session (ESP32 éteint / hors de portée) | État `Disconnected` + **Reconnecter**. |
| Échec d'écriture LED | Annulation de la bascule optimiste (l'interrupteur revient à l'état réel) + message transitoire ; connexion maintenue. |
| Permission `BLUETOOTH_CONNECT` manquante | Normalement déjà accordée via le scan ; garde défensive → message pour l'accorder. |

**LED optimiste** : la caractéristique LED étant en écriture seule, l'app ne peut pas relire l'état réel. On reflète la bascule immédiatement et on ne revient en arrière qu'en cas d'échec d'écriture.

## 9. Stratégie de test

**Tests unitaires (JVM) :**
- **`CounterCodec`** : décodage 4 octets LE → Int (ex. `[0x2A,0,0,0]` → `42`).
- **`DeviceControlViewModel`** (avec `FakeEspDeviceClient`) : `connect` appelé au démarrage ; `Connecting → Connected` reflété ; émission compteur → `uiState.counter` ; `onLedToggle(true)` → `setLed(true)` + `ledOn = true` ; échec `setLed` → `ledOn` revient en arrière + erreur ; `disconnect()` appelé dans `onCleared()`.
- **`FakeEspDeviceClient`** : double de test (états + compteur pilotables, enregistre les `setLed`, peut simuler un échec).

**Non testé unitairement (validé à la main) :**
- `AndroidEspDeviceClient` (framework `BluetoothGatt`) — gardé fin, validé sur matériel.
- Navigation Nav3 / UI Compose — validation manuelle (clic empile, retour dépile).
- Firmware — validé sur la carte.

**Validation end-to-end sur matériel :**
- [ ] ESP32 flashé, visible au scan sous `HomeController-ESP32`.
- [ ] Clic → connexion → écran de contrôle.
- [ ] Interrupteur allume/éteint la vraie LED.
- [ ] Compteur incrémenté en direct dans l'app.
- [ ] Retour → déconnexion propre (ESP32 de nouveau disponible).

## 10. Risques & notes

- **Nav3 en alpha** : API/artefacts susceptibles de bouger → versions épinglées et API vérifiée à l'implémentation.
- **Quirks GATT Android** (erreur 133, threading des callbacks, différences d'API d'écriture selon la version) : concentrés dans `AndroidEspDeviceClient`, traités explicitement, validés sur matériel.
- **Émulateur inutilisable** : validation sur téléphone physique + ESP32.
- **Broche LED** : `GPIO 2` par défaut, à ajuster selon la carte reçue.
