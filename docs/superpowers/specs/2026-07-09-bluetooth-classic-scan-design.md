# Scan Bluetooth Classic — Design

**Date :** 2026-07-09
**Statut :** validé (sections 1→3 approuvées), prêt pour le plan d'implémentation.

## Objectif

Ajouter un **scan Bluetooth Classic** (BR/EDR) : découvrir les appareils Classic
alentour et les afficher dans une liste. Nouvel onglet dédié, symétrique du scan BLE
existant. **Scope : scan + liste uniquement, sans appairage ni connexion.**

## Décisions de cadrage

- **UI** : **3 onglets** dans la bottom nav — Home Assistant / **BLE** (onglet actuel
  renommé) / **Bluetooth Classic** (nouveau).
- **Infos par appareil** : nom (ou « Inconnu »), MAC, RSSI (si fourni), badge
  **« Appairé »** si déjà lié.
- **Cycle de scan** : **une seule passe de découverte (~12 s)** puis arrêt ; **relance
  manuelle** via un bouton (pas de relance automatique).
- **Icônes** : on n'utilise **pas** `material-icons`. On récupère le **code SVG de chaque
  icône Phosphor** (poids *regular*) individuellement et on l'importe en **vector drawable**
  dans `:core:designsystem`. `material-icons-core` est **retiré**, ses 3 usages actuels migrés.
- **Convention de nommage** : préfixe explicite `BtClassic`.

## 1. Architecture & modules

Symétrique du BLE, en réutilisant les modules existants :

```
:feature:btclassic ──> :core:bluetooth ──> :core:model
   (écran + VM Hilt)     (+ BtClassicScanner)  (+ BtClassicDevice)
:feature:btclassic ──> :core:designsystem (icônes Phosphor)
:app ──> :feature:btclassic   (renommage onglet + 3e onglet)
```

- **`:core:model`** : `+ BtClassicDevice(address, name?, rssi?, bonded)`.
- **`:core:bluetooth`** : `+ BtClassicScanner` (interface publique), `AndroidBtClassicScanner`
  (impl `internal`), `BtClassicScanException` (public) ; `@Binds` ajouté au `BluetoothModule`
  existant. **Permissions déjà présentes** dans le manifest de `:core:bluetooth`
  (`BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` + legacy location).
- **`:core:designsystem`** : jeu d'icônes Phosphor (vector drawables) + objet `AppIcons`.
- **`:feature:btclassic`** (nouveau) : `BtClassicScanScreen`, `BtClassicScanViewModel`,
  `BtClassicUiState`, calqués sur `:feature:scan`.
- **`:app`** : onglet BLE renommé « BLE » ; 3e onglet « Bluetooth Classic »
  (nouvelle `BtClassicKey`, back stack propre) ; migration des icônes vers Phosphor.

**Différence technique clé avec le BLE** : pas de `BluetoothLeScanner` mais la **découverte** —
`adapter.startDiscovery()` + un `BroadcastReceiver` sur `ACTION_FOUND`.

## 2. Scanner & modèle

**Modèle** (`:core:model`) :

```kotlin
data class BtClassicDevice(
    val address: String,   // MAC, identifiant unique
    val name: String?,     // null si non diffusé
    val rssi: Int?,        // EXTRA_RSSI si fourni, sinon null
    val bonded: Boolean    // déjà appairé (BOND_BONDED)
)
```

**Contrat** (`:core:bluetooth`, public) — même forme que `BleScanner` (Flow froid, scan à la
souscription, arrêt à l'annulation, liste dédupliquée par MAC) :

```kotlin
interface BtClassicScanner {
    fun scan(): Flow<List<BtClassicDevice>>
}
```

`BtClassicScanException(message)` (public) : adaptateur indisponible ou `startDiscovery()` échoue.

**Impl Android** (`internal`, `callbackFlow`) :
- `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)` sur `ACTION_FOUND` +
  `ACTION_DISCOVERY_FINISHED`.
- `ACTION_FOUND` : `IntentCompat.getParcelableExtra` (sans dépréciation) → device ; RSSI via
  `EXTRA_RSSI` (absent → null) ; `bondState == BOND_BONDED` ; map dédupliquée par MAC ;
  `trySend(liste)`.
- `ACTION_DISCOVERY_FINISHED` : **`close()`** → le Flow **se termine** (une seule passe, pas
  de relance auto). La dernière liste émise reste chez le collecteur.
- `startDiscovery()` au démarrage (si `false` → `close(BtClassicScanException(...))`).
- `awaitClose { cancelDiscovery() ; unregisterReceiver() }`.
- `@RequiresPermission(allOf = [BLUETOOTH_SCAN, BLUETOOTH_CONNECT])` (`CONNECT` requis pour
  `device.name` sur Android 12+).

**Dépendance à ajouter à `:core:bluetooth`** : `androidx.core` (pour `ContextCompat`/`IntentCompat`).

**Risque noté** : `BLUETOOTH_SCAN` est déclaré `neverForLocation`. La découverte Classic
fonctionne avec ce flag sur Android 12+ ; si rien ne remonte à la validation device, retirer le
flag. Sur Android < 12, la découverte exige aussi la **localisation activée** (même limite que
le BLE actuel).

## 3. Feature UI, shell & icônes

**`:feature:btclassic`** — `BtClassicScanViewModel` (`@HiltViewModel`), cycle « une passe +
relance manuelle » :

```kotlin
sealed interface BtClassicUiState {
    data object PermissionRequired : BtClassicUiState
    data object BluetoothOff : BtClassicUiState
    data object Idle : BtClassicUiState                                   // bouton « Scanner »
    data class Scanning(val devices: List<BtClassicDevice>) : BtClassicUiState  // bouton « Arrêter »
    data class Finished(val devices: List<BtClassicDevice>) : BtClassicUiState  // bouton « Scanner à nouveau »
    data class Error(val message: String) : BtClassicUiState
}
```

- **updateAvailability(hasPermissions, isBluetoothEnabled)** : même garde que le BLE
  (PermissionRequired / BluetoothOff / Idle).
- **Scanner** (Idle/Finished) → collecte `scan()` → `Scanning`. Fin du Flow (découverte
  terminée) → `Finished(dernière liste)`.
- **Arrêter** (Scanning) → annule la collecte → `Finished(liste courante)`.
- **Erreur** du scan → `Error(message)`.
- **Tri d'affichage** : appairés d'abord, puis RSSI décroissant (null = plus faible), puis MAC.
- **Ligne** : nom (ou « Inconnu »), MAC, RSSI (`— dBm` si absent), badge « Appairé » si `bonded`.
- Helpers permission/Bluetooth : dupliqués depuis `:feature:scan` (~15 lignes ; factorisation
  en `:core:common` si un 3e consommateur apparaît).

**Icônes Phosphor** (`:core:designsystem`) :
- SVG *regular* officiels convertis en vector drawables (viewBox 256) :
  `ic_home_assistant` (house), `ic_ble` (bluetooth), `ic_bt_classic` (broadcast),
  `ic_settings` (gear).
- Objet `AppIcons` exposant les `@DrawableRes` ids ; consommateurs via
  `Icon(painterResource(AppIcons.X), …)`.
- **`material-icons-core` retiré** du catalogue et des `build.gradle` (`:app`,
  `:feature:homeassistant`). Usages migrés : shell (Home/Search → house/bluetooth + broadcast),
  écran HA (Settings → gear).

**`:app` — shell** :
- Onglet BLE : label « Bluetooth » → **« BLE »** (`ScanKey`/`ScanScreen` inchangés).
- 3e onglet **« Bluetooth Classic »** : `BtClassicKey`, back stack propre,
  `entry<BtClassicKey> { BtClassicScanScreen() }`.
- `TopTab` : HomeAssistant / Ble / BtClassic, icônes `AppIcons`.

## Gestion des erreurs

| Situation | Comportement |
|---|---|
| Permissions refusées | `PermissionRequired` (bouton pour demander) |
| Bluetooth éteint | `BluetoothOff` (bouton pour activer) |
| Adaptateur indisponible / `startDiscovery()` échoue | `Error(message)` |
| Découverte terminée | `Finished(liste)` + bouton « Scanner à nouveau » |

## Tests

- **`:feature:btclassic`** : `BtClassicScanViewModel` contre un **`FakeBtClassicScanner`**
  (Flow contrôlable) — garde permissions/Bluetooth, Idle→Scanning→Finished (fin de flux),
  Arrêter→Finished, tri (appairés d'abord puis RSSI décroissant), erreur → `Error`.
- **`AndroidBtClassicScanner`** (framework/BroadcastReceiver) : **pas de test JVM**, validé
  manuellement.

## Validation manuelle

Onglet Bluetooth Classic → **Scanner** → la liste se remplit ~12 s puis s'arrête sur
« Scanner à nouveau » ; appareils appairés badgés ; RSSI affiché s'il est fourni. Onglets **BLE**
(renommé) et **Home Assistant** intacts ; icônes Phosphor partout, plus aucune material-icons.

## Hors périmètre

- Appairage / connexion / SPP (Serial Port Profile) à un device Classic.
- Lissage RSSI (le Classic ne donne le RSSI qu'une fois par découverte).
- Persistance de la liste entre sessions.
