# Client Home Assistant — Design

**Date :** 2026-07-08
**Statut :** validé (sections 1→4 approuvées), prêt pour le plan d'implémentation.

## Objectif

Ajouter à HomeController un **client Home Assistant** : depuis l'app, se connecter à une
instance HA (Raspberry Pi 5, HTTP en clair sur IP locale), **lister** les entités
`light` et `switch` avec leur état, et les **commander** (allumer/éteindre). L'app
conserve **les deux mondes** : un onglet « Home Assistant » et un onglet « Bluetooth
Direct » (scan + contrôle ESP32 existants), chacun avec son propre back stack.

Increment **REST uniquement** — pas de WebSocket temps réel (incrément ultérieur).

## Décisions de cadrage

- **Périmètre** : shell bottom-nav + connexion + liste + commande.
- **Entités** : `light` et `switch` uniquement (liste + toggle). Autres domaines plus tard.
- **Transport** : Retrofit + OkHttp + kotlinx.serialization, **REST**.
- **Connexion** : URL **configurable dans l'app** (`http://192.168.x.x:8123`), **HTTP en
  clair** → `networkSecurityConfig` autorisant le cleartext.
- **Auth** : jeton d'accès longue durée (Long-Lived Access Token), saisi dans l'app.
- **Stockage config** : Preferences DataStore + jeton chiffré par une clé AES-GCM de
  l'Android Keystore (`security-crypto`/EncryptedSharedPreferences étant déprécié).
- **Rafraîchissement** : **pull-to-refresh** + refresh après chaque commande. Pas de polling.
- **Découpage** : NIA stratifié (option C) — `:core:network`, `:core:datastore`,
  `:core:data`, `:core:domain`, `:feature:homeassistant`, shell dans `:app`.
- **Convention de nommage** : préfixe explicite `HomeAssistant` (jamais `Ha`).

## 1. Graphe de modules & couches

```
:feature:homeassistant ──> :core:domain ──> :core:data ──┬─> :core:network
        (UI + VM Hilt)     (use cases)     (repository)   ├─> :core:datastore
                                                          └─> :core:model
:app (shell bottom-nav) ──> :feature:homeassistant + :feature:scan + :feature:devicedetail
```

| Module | Contenu | Visibilité |
|---|---|---|
| `:core:model` | `+ HomeAssistantEntity`, `+ HomeAssistantConfig` | public |
| `:core:network` | Retrofit/OkHttp/serialization, DTOs, `HomeAssistantApiService`, interceptor Bearer + base-URL dynamique | impls `internal`, module Hilt |
| `:core:datastore` | `HomeAssistantConfigStore` (interface) + impl Preferences DataStore (jeton chiffré via Android Keystore) | impl `internal`, module Hilt |
| `:core:data` | `HomeAssistantRepository` (interface + impl), mapping DTO→modèle, filtrage light/switch | impl `internal`, module Hilt |
| `:core:domain` | use cases fins | public |
| `:feature:homeassistant` | écrans config + liste/commande, `HomeAssistantViewModel` | — |
| `:app` | `NavigationBar` 2 onglets, un back stack par onglet | — |

**Flux (commande d'une lumière)** : UI toggle → VM `SetEntityStateUseCase(entityId, on)` →
repository → `HomeAssistantApiService.callService("light","turn_on", {entity_id})` →
interceptor OkHttp injecte `http://<ip>:8123` + `Authorization: Bearer …` → HA exécute.
Retour **optimiste** (bascule immédiate, rollback + erreur transitoire si échec), puis refresh.

## 2. Réseau (`:core:network`)

**Dépendances** : Retrofit, `retrofit2-kotlinx-serialization-converter`, OkHttp,
`okhttp-logging-interceptor` (debug), `kotlinx-serialization-json`, plugin `kotlin-serialization`.

> Risque à vérifier : le plugin compilateur `org.jetbrains.kotlin.plugin.serialization`
> doit fonctionner avec le Kotlin intégré d'AGP 9 (comme `kotlin.plugin.compose`). À
> confirmer au premier build du module ; sinon, replier sur un mapping manuel.

**DTOs internes** (kotlinx.serialization, `internal`) :

```kotlin
@Serializable
internal data class EntityStateDto(
    @SerialName("entity_id") val entityId: String,
    val state: String,
    val attributes: AttributesDto = AttributesDto()
)
@Serializable
internal data class AttributesDto(
    @SerialName("friendly_name") val friendlyName: String? = null
)
@Serializable
internal data class ServiceCallDto(@SerialName("entity_id") val entityId: String)
@Serializable
internal data class ApiInfoDto(val message: String)
```

**Service** (`internal interface HomeAssistantApiService`) :

```kotlin
@GET("api/")
suspend fun ping(): ApiInfoDto
@GET("api/states")
suspend fun getStates(): List<EntityStateDto>
@POST("api/services/{domain}/{service}")
suspend fun callService(
    @Path("domain") domain: String,
    @Path("service") service: String,
    @Body body: ServiceCallDto
)
```

**Interceptor dynamique** (`internal`) :
- Retrofit bâti avec base-URL placeholder `http://localhost/`.
- À chaque requête : lit la `HomeAssistantConfig` courante (fournie par un provider
  alimenté par `:core:datastore`). Si absente → `NotConfiguredException`. Sinon **réécrit
  scheme/host/port** depuis `baseUrl` et ajoute `Authorization: Bearer <token>`.

**Erreurs typées** (mappées par le repository) : `Unauthorized` (401), `Unreachable`
(IOException), `NotConfigured`, `Unknown`.

**Manifest `:core:network`** : `<uses-permission android:name="android.permission.INTERNET"/>`
et un `networkSecurityConfig` autorisant le HTTP en clair (commenté : usage LAN HA),
fusionné dans l'app.

## 3. Modèles, stockage config, repository, use cases

**`:core:model`** :

```kotlin
data class HomeAssistantConfig(val baseUrl: String, val token: String)

data class HomeAssistantEntity(
    val entityId: String,        // "light.salon"
    val domain: String,          // "light" | "switch"
    val friendlyName: String,    // attribute ?: entityId
    val isOn: Boolean,           // state == "on"
    val rawState: String
)
```

**`:core:datastore`** :

```kotlin
interface HomeAssistantConfigStore {
    val config: Flow<HomeAssistantConfig?>   // null = pas configuré
    suspend fun save(config: HomeAssistantConfig)
    suspend fun clear()
}
```

Impl `internal` sur Preferences DataStore ; le jeton est chiffré par une clé AES-GCM de
l'Android Keystore (ciphertext + IV persistés). Le provider de config de l'interceptor
(`:core:network`) lit cette source.

**`:core:data`** :

```kotlin
interface HomeAssistantRepository {
    val config: Flow<HomeAssistantConfig?>
    suspend fun saveConfig(config: HomeAssistantConfig)
    suspend fun testConnection(config: HomeAssistantConfig): Result<Unit>
    suspend fun getControllableEntities(): List<HomeAssistantEntity>
    suspend fun setEntityState(entityId: String, on: Boolean)
}
```

- `testConnection` teste avec **une config candidate** (avant enregistrement). Impl : le
  provider de config de l'interceptor est surchargeable le temps du ping (config candidate),
  sinon retombe sur la config stockée.
- `getControllableEntities` : `getStates()` → filtre domaines `light`/`switch` → mappe en
  `HomeAssistantEntity` (`domain` = préfixe avant `.`, `isOn` = `state == "on"`,
  `friendlyName` = attribut sinon `entityId`).
- `setEntityState` : dérive `domain` depuis `entityId`, choisit `turn_on`/`turn_off`.
- Mapping des erreurs réseau → erreurs typées (section 2).

**`:core:domain`** — use cases fins (`operator fun invoke`, délégation au repository) :

```
ObserveConfigUseCase()              → Flow<HomeAssistantConfig?>
SaveConfigUseCase(config)           → Unit
TestConnectionUseCase(config)       → Result<Unit>
GetControllableEntitiesUseCase()    → List<HomeAssistantEntity>
SetEntityStateUseCase(entityId,on)  → Unit
```

## 4. Feature UI & shell app

**`:feature:homeassistant`** — `HomeAssistantViewModel` (`@HiltViewModel`) + écran racine qui
bascule selon la config :

```kotlin
sealed interface HomeAssistantUiState {
    data object Loading : HomeAssistantUiState
    data class Unconfigured(val form: ConfigFormState) : HomeAssistantUiState
    data class Entities(
        val items: List<HomeAssistantEntity>,
        val isRefreshing: Boolean,
        val listError: String? = null,
        val transientError: String? = null
    ) : HomeAssistantUiState
}
```

- **Non configuré** → formulaire (champ URL, champ jeton, bouton « Tester & enregistrer »).
  Le VM appelle `TestConnectionUseCase` ; succès → `SaveConfigUseCase` → liste ; échec →
  message clair (`Unauthorized`/`Unreachable`).
- **Configuré** → liste light/switch ; chaque ligne = nom + `Switch`. Toggle **optimiste**
  (rollback + `transientError` si échec) puis refresh. **Pull-to-refresh** (`isRefreshing`).
  Icône de ré-réglage → repasse au formulaire.

**`:app` — shell bottom navigation** (Nav3, un back stack par onglet) :

```
enum class TopTab { HomeAssistant, BluetoothDirect }
// SnapshotStateList<NavKey> mémorisé PAR onglet :
//   HomeAssistant   -> [HomeAssistantKey]
//   BluetoothDirect -> [ScanKey]  (push DeviceControlKey)
// Scaffold(bottomBar = NavigationBar { 2 items }) héberge un NavDisplay rendu sur
// le back stack de l'onglet sélectionné ; chaque back stack persiste au changement d'onglet.
```

Logique des écrans features inchangée ; seul l'hôte de navigation évolue. `NavKeys` gagne
`HomeAssistantKey`.

## Gestion des erreurs

| Situation | Comportement |
|---|---|
| URL mal formée | Validation locale avant `testConnection` → message inline |
| Hôte injoignable (IOException) | `Unreachable` → message « HA injoignable à cette adresse » |
| Jeton invalide (401) | `Unauthorized` → message « Jeton refusé » |
| Liste en échec | État `Entities(listError=…)` + action de réessai (pull-to-refresh) |
| Échec de commande | Rollback optimiste + `transientError` |
| Non configuré au démarrage | `Unconfigured` (formulaire) |

## Stratégie de tests

Pattern établi : fakes + `coroutines-test`, `MainDispatcherRule` de `:core:testing`.

- **`:core:data`** : `HomeAssistantRepository` contre un **faux `HomeAssistantApiService`** —
  filtrage light/switch, mapping DTO→`HomeAssistantEntity`, `setEntityState` appelle le bon
  `domain/service`, mapping des erreurs (401→`Unauthorized`, IOException→`Unreachable`).
- **`:core:domain`** : use cases contre un **faux repository** (délégation).
- **`:feature:homeassistant`** : `HomeAssistantViewModel` contre de **faux use cases** —
  transitions `Loading→Unconfigured→Entities`, échec de connexion, toggle optimiste +
  rollback, refresh.
- **`:core:datastore`** : impl chiffrée (Android) non testée en JVM ; consommateurs testés
  via l'interface `HomeAssistantConfigStore` fakée. Validation manuelle de l'impl.

## Dépendances à ajouter (catalogue)

`retrofit`, `retrofit-kotlinx-serialization-converter`, `okhttp`, `okhttp-logging-interceptor`,
`kotlinx-serialization-json`, plugin `kotlin-serialization`, `androidx-datastore-preferences`.
Hilt/KSP déjà en place (voir chaîne AGP 9 + KSP 2.3.9 + Hilt 2.60.1).

## Validation manuelle finale

Sur téléphone + HA sur le Pi : saisir URL+jeton, voir les light/switch réels, en
allumer/éteindre un, pull-to-refresh, changer d'onglet et retrouver l'état BLE intact.

## Hors périmètre (incréments ultérieurs)

- WebSocket temps réel (états poussés).
- Autres domaines (sensor, climate, cover, media_player…).
- Zones/aires, dashboards, groupes.
- Découverte automatique de l'instance HA (mDNS).
```
