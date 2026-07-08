# HomeController — Modularisation NIA + Hilt — Spécification de conception

- **Date** : 2026-07-07
- **Projet** : HomeController (app Android, Kotlin, Jetpack Compose)
- **Statut** : Validé, prêt pour le plan d'implémentation
- **Nature** : Refactoring **à comportement constant** (aucune nouvelle fonctionnalité)

## 1. Objectif

Réorganiser l'app mono-module existante (scan BLE + contrôle ESP32) en une **architecture multi-module proche de Now in Android (NIA)**, et remplacer la DI manuelle par **Hilt**. À la fin de l'incrément, l'app se lance et se comporte **exactement comme avant** ; **tous les tests restent verts**.

C'est la fondation qui permettra d'ajouter proprement la feature Home Assistant (incrément suivant) et les futures features.

### Hors périmètre

- La feature Home Assistant, la barre d'onglets (bottom navigation), et les modules `:core:network` / `:core:data` / `:core:domain` / `:feature:homeassistant` → **incrément suivant**.
- Les plugins de convention Gradle (`build-logic`) → raffinement ultérieur dédié.
- Toute modification de comportement, d'UI ou de logique métier.

## 2. Critères de succès

- Le projet compile en multi-module (`./gradlew assembleDebug`) et le graphe **Hilt** est valide.
- **Tous les tests unitaires existants passent**, déplacés dans leurs modules respectifs.
- L'app se lance et le parcours **scan → contrôle → retour** est **identique** à l'actuel (validation manuelle sur téléphone).
- `AppContainer`, `ScanViewModelFactory`, `deviceControlViewModelFactory` sont **supprimés** (remplacés par Hilt).
- Les classes d'implémentation BLE Android (`Android*`, `GattProfile`, `CounterCodec`) sont `internal` à `:core:bluetooth`.

## 3. Décisions d'architecture (validées)

- **Style NIA** : modules `:app` / `:feature:*` / `:core:*`.
- **Convention NIA** (≠ clean archi scolaire) : modèles purs dans `:core:model`, interfaces de repository dans `:core:data` (pas dans le domaine — non concerné cet incrément), use cases dans `:core:domain`.
- **`:core:bluetooth`** = capacité BLE autonome : expose les **interfaces** (`BleScanner`, `EspDeviceClient`), cache les **implémentations Android** (`internal`). Contient aussi `GattProfile` et `CounterCodec`.
- **DI = Hilt** (KSP), remplace la DI manuelle.
- **Gradle** : config par module explicite via le **catalogue de versions** partagé (pas de plugins de convention pour l'instant).
- **`:core:model`** = module **Kotlin/JVM pur** (zéro Android).

## 4. Graphe de modules (cet incrément)

```
:app                  (com.android.application)      → MainActivity, Application (@HiltAndroidApp),
                                                        navigation Nav3, assemblage
:feature:scan         (com.android.library + compose) → écran + VM du scan
:feature:devicedetail (com.android.library + compose) → écran + VM du contrôle BLE
:core:bluetooth       (com.android.library)          → capacité BLE (interfaces publiques + impls internal + module Hilt)
:core:model           (kotlin/jvm pur)               → modèles purs
:core:designsystem    (com.android.library + compose) → thème Compose
:core:testing         (com.android.library)          → utilitaires de test partagés
```

**Règle de dépendances :**
```
:app            → :feature:scan, :feature:devicedetail, :core:designsystem
:feature:*      → :core:bluetooth, :core:model, :core:designsystem
:core:bluetooth → :core:model
(les features ne dépendent PAS l'une de l'autre)
```

## 5. Migration fichier par fichier

| Fichier actuel | Module cible | Visibilité |
|---|---|---|
| `domain/model/BleDevice.kt` | `:core:model` | public |
| `domain/connection/EspConnectionState.kt` | `:core:model` | public |
| `domain/scan/BleScanner.kt`, `BleScanException.kt` | `:core:bluetooth` | public (API) |
| `domain/connection/EspDeviceClient.kt` | `:core:bluetooth` | public (API) |
| `data/scan/AndroidBleScanner.kt` | `:core:bluetooth` | `internal` |
| `data/connection/AndroidEspDeviceClient.kt`, `GattProfile.kt` | `:core:bluetooth` | `internal` |
| `domain/connection/CounterCodec.kt` (+ test) | `:core:bluetooth` | `internal` |
| `presentation/scan/*` (Screen, VM, UiState, RssiStabilizer) | `:feature:scan` | — |
| `presentation/control/*` (Screen, VM, UiState) | `:feature:devicedetail` | — |
| `ui/theme/*` (Theme, Color, Type) | `:core:designsystem` | public |
| `navigation/*` (NavKeys, HomeControllerNavDisplay) | `:app` | — |
| `MainActivity`, `HomeControllerApplication` | `:app` | — |
| `di/AppContainer.kt`, `*ViewModelFactory.kt` | **supprimés** | — |
| `presentation/scan` tests + `FakeBleScanner` | `:feature:scan` (test) | — |
| `presentation/control` tests + `FakeEspDeviceClient` | `:feature:devicedetail` (test) | — |
| `CounterCodecTest` | `:core:bluetooth` (test) | — |
| `MainDispatcherRule` | `:core:testing` | public |

## 6. Hilt

**Point d'entrée** (`:app`) : `@HiltAndroidApp` sur l'Application, `@AndroidEntryPoint` sur `MainActivity`.

**Impls injectables** (`:core:bluetooth`) : `AndroidBleScanner` et `AndroidEspDeviceClient` reçoivent `@Inject constructor(@ApplicationContext context: Context)` (on aligne `AndroidBleScanner` : il dérive l'adaptateur en interne, comme `AndroidEspDeviceClient`).

**Module de liaison** (`:core:bluetooth`, `internal`) :
```kotlin
@Module @InstallIn(SingletonComponent::class)
internal abstract class BluetoothModule {
    @Binds abstract fun bindBleScanner(impl: AndroidBleScanner): BleScanner
    // non scopé → nouvelle instance par injection = une session de connexion par écran
    @Binds abstract fun bindEspDeviceClient(impl: AndroidEspDeviceClient): EspDeviceClient
}
```

**ViewModels** :
- `ScanViewModel` : `@HiltViewModel` + `@Inject constructor(scanner: BleScanner)`.
- `DeviceControlViewModel` : **injection assistée** (l'adresse vient de la navigation) :
```kotlin
@HiltViewModel(assistedFactory = DeviceControlViewModel.Factory::class)
class DeviceControlViewModel @AssistedInject constructor(
    @Assisted private val address: String,
    private val client: EspDeviceClient
) : ViewModel() {
    @AssistedFactory interface Factory { fun create(address: String): DeviceControlViewModel }
}
```

**Écrans** : `viewModel(factory = …)` → `hiltViewModel()` (via `hilt-navigation-compose`). Pour le contrôle :
```kotlin
val vm: DeviceControlViewModel =
    hiltViewModel(creationCallback = { f: DeviceControlViewModel.Factory -> f.create(address) })
```
Le scoping par destination Nav3 (`rememberViewModelStoreNavEntryDecorator`, déjà en place) fournit le bon `ViewModelStoreOwner`.

**Supprimés** : `AppContainer`, `ScanViewModelFactory`, `deviceControlViewModelFactory`.

## 7. Setup Gradle

- **`settings.gradle.kts`** : `include(":app", ":core:model", ":core:bluetooth", ":core:designsystem", ":core:testing", ":feature:scan", ":feature:devicedetail")`.
- **Catalogue de versions** partagé (existant) : on y ajoute Hilt, KSP, `hilt-navigation-compose`.
- **Plugins par module** :
  - `:core:model` → `org.jetbrains.kotlin.jvm` (pur).
  - `:core:bluetooth`, `:core:testing` → `com.android.library` + Hilt/KSP.
  - `:core:designsystem`, `:feature:*` → `com.android.library` + Compose (+ Hilt/KSP pour les features).
  - `:app` → `com.android.application` + Compose + Hilt/KSP.
- **Config explicite par module** (pas de plugins de convention pour l'instant), en s'appuyant sur le catalogue.
- Le bloc `compileSdk = 37` / `minSdk = 26` est répliqué dans les modules Android.

## 8. Stratégie de test / vérification

- **Tests unitaires déplacés** dans leurs modules, **tous verts** :
  - `:core:bluetooth` → `CounterCodecTest`.
  - `:feature:scan` → `ScanViewModelTest`, `RssiStabilizerTest` (+ `FakeBleScanner`).
  - `:feature:devicedetail` → `DeviceControlViewModelTest` (+ `FakeEspDeviceClient`).
  - `MainDispatcherRule` dans `:core:testing`, en `testImplementation` des modules qui en ont besoin.
- **Compilation Hilt** : `./gradlew assembleDebug` réussit (graphe DI valide).
- **Suite complète** : `./gradlew testDebugUnitTest` (tous modules) verte.
- **Validation manuelle** (comportement constant) : app installée, parcours scan → contrôle → retour identique à l'actuel.

## 9. Risques & notes

- **Ampleur du refactor** : beaucoup de fichiers déplacés + 7 modules. Le plan procédera module par module, en gardant la compilation/les tests verts à chaque étape autant que possible.
- **Hilt + Nav3 + injection assistée** : combinaison à valider à l'implémentation (API `hiltViewModel(creationCallback = …)`, versions Hilt/KSP épinglées et vérifiées).
- **`internal` inter-module** : les impls `Android*` passant `internal`, vérifier qu'aucun test ne les référençait directement (les tests utilisent les interfaces + des fakes → OK).
- **Comportement constant** : aucune modification d'UI/logique ; toute divergence de comportement = régression à corriger.
