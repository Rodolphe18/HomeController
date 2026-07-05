# HomeController — Fonctionnalité « Scan BLE » — Spécification de conception

- **Date** : 2026-07-05
- **Projet** : HomeController (app Android, Kotlin, Jetpack Compose)
- **Package** : `com.francotte.homecontroller`
- **Statut** : Validé, prêt pour le plan d'implémentation

## 1. Objectif

Première fonctionnalité du projet HomeController : **scanner les périphériques Bluetooth Low Energy (BLE) environnants et les afficher dans une liste live, en lecture seule.**

C'est une fonctionnalité **100 % téléphone** : elle ne nécessite pas le matériel Arduino de l'ELEGOO Super Starter Kit. Elle sert de fondation d'apprentissage du BLE côté Android avant d'aborder la connexion et le contrôle d'objets.

### Hors périmètre (fonctionnalités ultérieures)

- Se **connecter** à un périphérique BLE.
- Lire / écrire des caractéristiques GATT.
- Contrôler l'Arduino (nécessitera un module BLE type HM-10 / AT-09 / HC-08 — les modules HC-05 / HC-06 sont du Bluetooth **Classic** et n'apparaîtront pas dans un scan BLE).
- Persistance des appareils, favoris, historique.

## 2. Critères de succès

- Sur un **téléphone Android physique**, lancer le scan affiche les périphériques BLE réels environnants.
- Chaque appareil affiche : **nom** (ou « Inconnu » si non diffusé), **adresse MAC**, **RSSI** (dBm).
- La liste se met à jour **en direct** et se trie par **RSSI décroissant** (le plus proche en haut).
- Un même appareil vu plusieurs fois apparaît **une seule fois** (déduplication par adresse MAC), avec son RSSI actualisé.
- Un bouton **Start / Stop** contrôle le scan.
- L'app gère proprement : permissions manquantes, Bluetooth éteint, échec de scan.
- Le scan **s'arrête automatiquement** quand l'écran n'est plus actif (pas de fuite, économie de batterie).

## 3. Contexte technique

- Kotlin, Jetpack Compose, Material 3.
- `minSdk = 26`, `targetSdk = 36`.
- Test sur **téléphone Android physique** (l'émulateur ne supporte pas le scan BLE réel).
- Aucune dépendance Bluetooth tierce : on utilise l'API Android native (`android.bluetooth.le`).

## 4. Décisions d'architecture

### 4.1 Clean Architecture — trois couches

Règle de dépendance orientée vers l'intérieur : `presentation → domain ← data`. Le `domain` n'importe **rien** d'Android.

```
com.francotte.homecontroller
├── domain/                      ← Kotlin pur, zéro import Android
│   ├── model/
│   │   └── BleDevice.kt
│   └── scan/
│       └── BleScanner.kt        ← interface : fun scan(): Flow<List<BleDevice>>
│
├── data/                        ← implémentation Android
│   └── scan/
│       └── AndroidBleScanner.kt ← implémente BleScanner via BluetoothLeScanner
│
├── presentation/
│   └── scan/
│       ├── ScanUiState.kt
│       ├── ScanViewModel.kt
│       └── ScanScreen.kt
│
└── di/
    └── AppContainer.kt          ← câblage manuel + ViewModel Factory
```

**Inversion de dépendance (SOLID « D »)** : l'interface `BleScanner` vit dans le `domain` ; son implémentation `AndroidBleScanner` vit dans `data`. Le `ScanViewModel` ne dépend que de l'interface → testable sans Bluetooth réel.

### 4.2 Modèle réactif : Kotlin `Flow`

La couche BLE expose un `Flow<List<BleDevice>>`. Choix retenu plutôt que des callbacks classiques : idiome Android moderne, mariage naturel avec Compose (réactif), gestion propre du cycle de vie du scan, meilleure testabilité.

### 4.3 Injection de dépendances : manuelle (pour l'instant)

Câblage manuel via un `AppContainer` + une `ViewModelProvider.Factory`. Pédagogiquement, on voit le câblage à la main. Migration vers **Hilt** prévue quand le projet comptera plusieurs écrans.

## 5. Conception détaillée des unités

### 5.1 Domain

**`BleDevice`** — modèle métier immuable :

```kotlin
data class BleDevice(
    val address: String,  // adresse MAC, identifiant unique
    val name: String?,    // null si non diffusé
    val rssi: Int         // force du signal, en dBm
)
```

**`BleScanner`** — le contrat :

```kotlin
interface BleScanner {
    fun scan(): Flow<List<BleDevice>>
}
```

Le `Flow` retourné est **froid** : le scan démarre à la souscription et s'arrête à l'annulation.

### 5.2 Data

**`AndroidBleScanner`** — implémente `BleScanner` :

- S'appuie sur `BluetoothAdapter.bluetoothLeScanner`.
- `scan()` retourne un `callbackFlow` :
  - construit un `ScanCallback` ;
  - à chaque `onScanResult`, met à jour une `Map<String, BleDevice>` clé = adresse MAC (déduplication + RSSI actualisé), puis `trySend(map.values.toList())` ;
  - `onScanFailed` → ferme le flow avec une exception dédiée ;
  - `startScan` à l'ouverture, `awaitClose { stopScan() }` à l'annulation.
- Traduit `ScanResult` → `BleDevice` (mapping pur).
- Reste **volontairement fin** : le minimum de logique, car cette couche n'est pas testable unitairement.

### 5.3 Presentation

**`ScanUiState`** — état de l'écran (sealed interface) couvrant :

- `PermissionRequired` — permissions à accorder.
- `BluetoothOff` — Bluetooth désactivé.
- `Idle` — prêt, scan non démarré.
- `Scanning(devices: List<BleDevice>)` — scan en cours, liste live.
- `Error(message: String)` — échec de scan.

**`ScanViewModel`** :

- Dépend de l'interface `BleScanner` (injection par constructeur).
- Expose `StateFlow<ScanUiState>`.
- `startScan()` : s'abonne à `scanner.scan()`, **trie par RSSI décroissant**, mappe vers `Scanning`.
- `stopScan()` : annule le job de collecte.
- Gère les erreurs du flow → état `Error`.

**`ScanScreen`** (Composable) :

- Observe l'état via `collectAsStateWithLifecycle`.
- Gère les permissions via `rememberLauncherForActivityResult(RequestMultiplePermissions)`.
- Propose l'activation du Bluetooth via `ACTION_REQUEST_ENABLE`.
- Affiche une `LazyColumn` de lignes (nom, adresse MAC, RSSI) + bouton Start/Stop.
- Aucune logique métier.

### 5.4 DI

**`AppContainer`** : instancie `AndroidBleScanner` (avec le contexte applicatif) et fournit une `ViewModelProvider.Factory` qui injecte le scanner dans `ScanViewModel`.

## 6. Flux de données

```
[BluetoothLeScanner Android]
        │  ScanResult (callback)
        ▼
AndroidBleScanner ──(callbackFlow)──► Flow<List<BleDevice>>   // dédupliqué
        │
        ▼
ScanViewModel  ──(map + tri par RSSI)──► StateFlow<ScanUiState>
        │
        ▼
ScanScreen  ──(collectAsStateWithLifecycle)──► LazyColumn
```

## 7. Permissions & cas limites

Le comportement dépend de la version Android (piège classique du BLE) :

- **Android 12+ (API 31+)** : permissions runtime `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` (cette dernière pour lire le nom des appareils).
- **Android ≤ 11 (API < 31)** : `BLUETOOTH` + `BLUETOOTH_ADMIN` (manifest) **et** `ACCESS_FINE_LOCATION` (runtime, + service de localisation activé).

Le `AndroidManifest.xml` déclare ces permissions avec `maxSdkVersion` / `usesPermissionFlags="neverForLocation"` appropriés pour ne pas exiger la localisation sur les versions récentes.

L'ensemble des permissions demandées à l'exécution est choisi dynamiquement selon `Build.VERSION.SDK_INT`.

Les 4 états limites (permissions, BT éteint, scan, erreur) sont modélisés dans `ScanUiState` et rendus par `ScanScreen`.

## 8. Stratégie de test

- **`domain`** : Kotlin pur → testable trivialement.
- **`ScanViewModel`** : test unitaire avec un `FakeBleScanner` (émet des listes prédéfinies). Vérifie : tri par RSSI, déduplication, transitions d'état. Outils : `kotlinx-coroutines-test` (+ `turbine` en option pour les Flows).
- **`AndroidBleScanner`** : non testé unitairement (framework) — gardé fin et **validé manuellement sur téléphone physique**.

Dépendances de test à ajouter : `kotlinx-coroutines-test` (et éventuellement `turbine`).

## 9. Risques & notes

- **Émulateur inutilisable** pour cette fonctionnalité : tests manuels sur appareil physique obligatoires.
- **Fragmentation des permissions** selon la version Android : source d'erreurs n°1, traitée explicitement en §7.
- **BLE ≠ Bluetooth Classic** : rappel pour la suite du projet, sans impact sur cette fonctionnalité (le scan liste tout ce qui émet en BLE, indépendamment de l'Arduino).
