# Client Home Assistant — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter un client Home Assistant (REST) : connexion configurable (URL+jeton), liste des entités `light`/`switch` avec état, commande on/off ; le tout dans un shell bottom-nav à 2 onglets (Home Assistant / Bluetooth Direct).

**Architecture:** NIA stratifié. `:core:model` (modèles + erreurs typées), `:core:network` (Retrofit/OkHttp/serialization, data source publique, service+interceptor internal), `:core:datastore` (config chiffrée), `:core:data` (repository), `:core:domain` (use cases), `:feature:homeassistant` (UI+VM Hilt), shell bottom-nav dans `:app`. DI = Hilt.

**Tech Stack:** Kotlin 2.2.21, AGP 9.2.1, Kotlin intégré (pas de plugin kotlin.android), KSP 2.3.9, Hilt 2.60.1, Retrofit 3.0.0, OkHttp 5.4.0, kotlinx-serialization-json 1.11.0, security-crypto 1.1.0, Compose (BOM 2026.02.01), Nav3 1.0.1.

**Spec :** `docs/superpowers/specs/2026-07-08-home-assistant-client-design.md`

> ⚠️ **Commits :** l'utilisateur commite lui-même. **Ne pas committer.** Les étapes « Commit » indiquent le contenu logique.

> ⚠️ **Chaîne AGP 9 :** modules Android = `com.android.library`/`application` (+ `kotlin.compose`, `kotlin.serialization`, `hilt`, `ksp` au besoin). **Jamais** de plugin `kotlin.android`, jamais `android.builtInKotlin=false`.

> ⚠️ **Convention de nommage :** préfixe explicite `HomeAssistant` (jamais `Ha`).

> ⚠️ **Packages par module :**
> - `:core:network` → `com.francotte.homecontroller.core.network`
> - `:core:datastore` → `com.francotte.homecontroller.core.datastore`
> - `:core:data` → `com.francotte.homecontroller.core.data`
> - `:core:domain` → `com.francotte.homecontroller.core.domain`
> - `:feature:homeassistant` → `com.francotte.homecontroller.feature.homeassistant`

---

## Task 1 : Catalogue de versions + plugins racine

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (racine)

- [ ] **Step 1 : Ajouter les versions**

Sous `[versions]` de `gradle/libs.versions.toml`, ajouter :

```toml
retrofit = "3.0.0"
okhttp = "5.4.0"
kotlinxSerializationJson = "1.11.0"
datastore = "1.2.1"
```

- [ ] **Step 2 : Ajouter les libraries**

Sous `[libraries]`, ajouter :

```toml
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-kotlinx-serialization-converter = { group = "com.squareup.retrofit2", name = "converter-kotlinx-serialization", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging-interceptor = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerializationJson" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
```

> `kotlin-serialization` (plugin) est **déjà** déclaré dans `[plugins]` (référence `kotlin`). Rien à ajouter côté plugins.

- [ ] **Step 3 : Déclarer le plugin serialization au niveau racine**

Dans `build.gradle.kts` (racine), ajouter la ligne dans le bloc `plugins { … }` :

```kotlin
    alias(libs.plugins.kotlin.serialization) apply false
```

- [ ] **Step 4 : Vérifier la résolution**

Run: `./gradlew help -q`
Expected: `BUILD SUCCESSFUL` (versions/plugins se résolvent).

- [ ] **Step 5 : Commit (utilisateur)** — catalogue + plugin serialization.

---

## Task 2 : `:core:model` — modèles HA + erreurs typées

**Files:**
- Create: `core/model/src/main/java/com/francotte/homecontroller/core/model/HomeAssistantConfig.kt`
- Create: `core/model/src/main/java/com/francotte/homecontroller/core/model/HomeAssistantEntity.kt`
- Create: `core/model/src/main/java/com/francotte/homecontroller/core/model/HomeAssistantException.kt`

> `:core:model` est un module Kotlin/JVM pur (plugin `kotlin.jvm`) — pas de changement de `build.gradle.kts`.

- [ ] **Step 1 : `HomeAssistantConfig.kt`**

```kotlin
package com.francotte.homecontroller.core.model

/** Configuration de connexion à une instance Home Assistant. */
data class HomeAssistantConfig(
    val baseUrl: String,   // ex. "http://192.168.1.20:8123"
    val token: String      // jeton d'accès longue durée
)
```

- [ ] **Step 2 : `HomeAssistantEntity.kt`**

```kotlin
package com.francotte.homecontroller.core.model

/**
 * Une entité Home Assistant commandable (light/switch), vue par l'UI.
 *
 * @property entityId    identifiant HA, ex. "light.salon"
 * @property domain      préfixe avant le "." : "light" ou "switch"
 * @property friendlyName nom lisible (attribut friendly_name, sinon entityId)
 * @property isOn        true si l'état HA vaut "on"
 * @property rawState    état brut renvoyé par HA (ex. "on", "off", "unavailable")
 */
data class HomeAssistantEntity(
    val entityId: String,
    val domain: String,
    val friendlyName: String,
    val isOn: Boolean,
    val rawState: String
)
```

- [ ] **Step 3 : `HomeAssistantException.kt`**

Erreurs typées, visibles de toutes les couches. Elles héritent d'`IOException` pour pouvoir être levées depuis un interceptor OkHttp.

```kotlin
package com.francotte.homecontroller.core.model

import java.io.IOException

/** Erreurs typées de la couche Home Assistant. */
sealed class HomeAssistantException(message: String) : IOException(message) {
    /** Jeton refusé (HTTP 401). */
    data object Unauthorized : HomeAssistantException("Jeton d'accès refusé")
    /** Hôte injoignable (erreur réseau). */
    data object Unreachable : HomeAssistantException("Home Assistant injoignable")
    /** Aucune configuration enregistrée. */
    data object NotConfigured : HomeAssistantException("Home Assistant non configuré")
    /** Autre échec. */
    data object Unknown : HomeAssistantException("Erreur inconnue")
}
```

- [ ] **Step 4 : Vérifier la compilation**

Run: `./gradlew :core:model:compileKotlin -q`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5 : Commit (utilisateur)** — modèles HA dans `:core:model`.

---

## Task 3 : `:core:network` — Retrofit, data source, interceptor dynamique

**Files:**
- Modify: `settings.gradle.kts`
- Create: `core/network/build.gradle.kts`
- Create: `core/network/src/main/AndroidManifest.xml`
- Create: `core/network/src/main/res/xml/network_security_config.xml`
- Create: `core/network/src/main/java/com/francotte/homecontroller/core/network/NetworkModels.kt`
- Create: `core/network/src/main/java/com/francotte/homecontroller/core/network/HomeAssistantConfigProvider.kt`
- Create: `core/network/src/main/java/com/francotte/homecontroller/core/network/HomeAssistantNetworkDataSource.kt`
- Create: `core/network/src/main/java/com/francotte/homecontroller/core/network/HomeAssistantApiService.kt`
- Create: `core/network/src/main/java/com/francotte/homecontroller/core/network/HomeAssistantAuthInterceptor.kt`
- Create: `core/network/src/main/java/com/francotte/homecontroller/core/network/RetrofitHomeAssistantNetworkDataSource.kt`
- Create: `core/network/src/main/java/com/francotte/homecontroller/core/network/NetworkModule.kt`
- Test: `core/network/src/test/java/com/francotte/homecontroller/core/network/HomeAssistantAuthInterceptorTest.kt`
- Test: `core/network/src/test/java/com/francotte/homecontroller/core/network/RetrofitHomeAssistantNetworkDataSourceTest.kt`

- [ ] **Step 1 : Inclure le module**

Dans `settings.gradle.kts`, après `include(":feature:devicedetail")`, ajouter :

```kotlin
include(":core:network")
```

- [ ] **Step 2 : `.gitignore` du module**

Create `core/network/.gitignore` :

```
/build
```

- [ ] **Step 3 : `core/network/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.francotte.homecontroller.core.network"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

> Risque connu : valider que le plugin `kotlin.serialization` s'applique avec le Kotlin intégré d'AGP 9. Le premier build du module le confirmera (Step 15).

- [ ] **Step 4 : Manifest (permission INTERNET + config sécurité réseau)**

`core/network/src/main/AndroidManifest.xml` :

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />

    <!-- HTTP en clair autorisé : Home Assistant est joint en http sur le LAN.
         Fusionné dans l'app via android:networkSecurityConfig ci-dessous. -->
    <application android:networkSecurityConfig="@xml/network_security_config" />
</manifest>
```

- [ ] **Step 5 : Config sécurité réseau (cleartext)**

`core/network/src/main/res/xml/network_security_config.xml` :

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- Autorise le trafic HTTP en clair (Home Assistant sur IP locale). -->
    <base-config cleartextTrafficPermitted="true" />
</network-security-config>
```

- [ ] **Step 6 : Network models publics + DTOs internes**

`core/network/src/main/java/com/francotte/homecontroller/core/network/NetworkModels.kt` :

```kotlin
package com.francotte.homecontroller.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** État d'une entité tel que renvoyé par `GET /api/states`. */
@Serializable
data class NetworkEntityState(
    @SerialName("entity_id") val entityId: String,
    val state: String,
    val attributes: NetworkAttributes = NetworkAttributes()
)

@Serializable
data class NetworkAttributes(
    @SerialName("friendly_name") val friendlyName: String? = null
)

/** Corps de `POST /api/services/{domain}/{service}`. */
@Serializable
internal data class NetworkServiceCall(
    @SerialName("entity_id") val entityId: String
)

/** Réponse de `GET /api/`. */
@Serializable
internal data class NetworkApiInfo(val message: String)
```

- [ ] **Step 7 : Provider de config (interface publique lue par l'interceptor)**

`core/network/src/main/java/com/francotte/homecontroller/core/network/HomeAssistantConfigProvider.kt` :

```kotlin
package com.francotte.homecontroller.core.network

import com.francotte.homecontroller.core.model.HomeAssistantConfig

/**
 * Fournit, de façon synchrone, la configuration HA enregistrée à l'interceptor.
 * L'implémentation vit dans `:core:data` (adossée au store chiffré).
 */
interface HomeAssistantConfigProvider {
    fun current(): HomeAssistantConfig?
}
```

- [ ] **Step 8 : Interface data source publique + fonction d'autorisation pure**

`core/network/src/main/java/com/francotte/homecontroller/core/network/HomeAssistantNetworkDataSource.kt` :

```kotlin
package com.francotte.homecontroller.core.network

import com.francotte.homecontroller.core.model.HomeAssistantConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/** Accès réseau bas niveau à Home Assistant. Lève des [com.francotte.homecontroller.core.model.HomeAssistantException]. */
interface HomeAssistantNetworkDataSource {
    /** Pingue HA avec une config candidate (avant enregistrement). */
    suspend fun testConnection(config: HomeAssistantConfig)
    /** Toutes les entités (non filtrées). */
    suspend fun getStates(): List<NetworkEntityState>
    /** Appelle un service, ex. domain="light", service="turn_on". */
    suspend fun callService(domain: String, service: String, entityId: String)
}

/**
 * Réécrit une requête placeholder vers la config réelle (scheme/host/port) et
 * ajoute l'en-tête Bearer. Fonction pure → testable sans réseau.
 */
internal fun authorize(original: Request, config: HomeAssistantConfig): Request {
    val base = config.baseUrl.toHttpUrl()
    val url = original.url.newBuilder()
        .scheme(base.scheme)
        .host(base.host)
        .port(base.port)
        .build()
    return original.newBuilder()
        .url(url)
        .header("Authorization", "Bearer ${config.token}")
        .build()
}
```

- [ ] **Step 9 : Service Retrofit interne**

`core/network/src/main/java/com/francotte/homecontroller/core/network/HomeAssistantApiService.kt` :

```kotlin
package com.francotte.homecontroller.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

internal interface HomeAssistantApiService {
    @GET("api/")
    suspend fun ping(): NetworkApiInfo

    @GET("api/states")
    suspend fun getStates(): List<NetworkEntityState>

    @POST("api/services/{domain}/{service}")
    suspend fun callService(
        @Path("domain") domain: String,
        @Path("service") service: String,
        @Body body: NetworkServiceCall
    )
}
```

- [ ] **Step 10 : Interceptor**

`core/network/src/main/java/com/francotte/homecontroller/core/network/HomeAssistantAuthInterceptor.kt` :

```kotlin
package com.francotte.homecontroller.core.network

import com.francotte.homecontroller.core.model.HomeAssistantException
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

internal class HomeAssistantAuthInterceptor @Inject constructor(
    private val provider: HomeAssistantConfigProvider
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val config = provider.current() ?: throw HomeAssistantException.NotConfigured
        return chain.proceed(authorize(chain.request(), config))
    }
}
```

- [ ] **Step 11 : Écrire le test de l'interceptor (pure `authorize`)**

`core/network/src/test/java/com/francotte/homecontroller/core/network/HomeAssistantAuthInterceptorTest.kt` :

```kotlin
package com.francotte.homecontroller.core.network

import com.francotte.homecontroller.core.model.HomeAssistantConfig
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAssistantAuthInterceptorTest {

    @Test
    fun `authorize reecrit host port scheme et ajoute le Bearer`() {
        val original = Request.Builder().url("http://localhost/api/states").build()
        val config = HomeAssistantConfig("http://192.168.1.20:8123", "TOKEN123")

        val result = authorize(original, config)

        assertEquals("http", result.url.scheme)
        assertEquals("192.168.1.20", result.url.host)
        assertEquals(8123, result.url.port)
        assertEquals("/api/states", result.url.encodedPath)
        assertEquals("Bearer TOKEN123", result.header("Authorization"))
    }
}
```

- [ ] **Step 12 : Vérifier que le test échoue puis passe**

Run: `./gradlew :core:network:testDebugUnitTest --tests "*HomeAssistantAuthInterceptorTest*"`
Expected d'abord : échec de compilation tant que `authorize` n'est pas écrit ; après Step 8, `BUILD SUCCESSFUL`.

- [ ] **Step 13 : Impl du data source (mapping des erreurs)**

`core/network/src/main/java/com/francotte/homecontroller/core/network/RetrofitHomeAssistantNetworkDataSource.kt` :

```kotlin
package com.francotte.homecontroller.core.network

import com.francotte.homecontroller.core.model.HomeAssistantConfig
import com.francotte.homecontroller.core.model.HomeAssistantException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

internal class RetrofitHomeAssistantNetworkDataSource @Inject constructor(
    private val api: HomeAssistantApiService
) : HomeAssistantNetworkDataSource {

    // Client sans interceptor : les tests de connexion utilisent une config candidate.
    private val candidateClient = OkHttpClient()

    override suspend fun getStates(): List<NetworkEntityState> =
        call { api.getStates() }

    override suspend fun callService(domain: String, service: String, entityId: String) =
        call { api.callService(domain, service, NetworkServiceCall(entityId)) }

    override suspend fun testConnection(config: HomeAssistantConfig) = withContext(Dispatchers.IO) {
        val url = config.baseUrl.trimEnd('/') + "/api/"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.token}")
            .build()
        try {
            candidateClient.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> Unit
                    response.code == 401 -> throw HomeAssistantException.Unauthorized
                    else -> throw HomeAssistantException.Unknown
                }
            }
        } catch (e: HomeAssistantException) {
            throw e
        } catch (e: IOException) {
            throw HomeAssistantException.Unreachable
        }
    }

    private suspend fun <T> call(block: suspend () -> T): T =
        try {
            block()
        } catch (e: HomeAssistantException) {
            throw e
        } catch (e: HttpException) {
            throw if (e.code() == 401) HomeAssistantException.Unauthorized else HomeAssistantException.Unknown
        } catch (e: IOException) {
            throw HomeAssistantException.Unreachable
        }
}
```

- [ ] **Step 14 : Test du mapping d'erreurs du data source**

`core/network/src/test/java/com/francotte/homecontroller/core/network/RetrofitHomeAssistantNetworkDataSourceTest.kt` :

```kotlin
package com.francotte.homecontroller.core.network

import com.francotte.homecontroller.core.model.HomeAssistantException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class RetrofitHomeAssistantNetworkDataSourceTest {

    private fun api(block: FakeApi.() -> Unit) = FakeApi().apply(block)

    private class FakeApi : HomeAssistantApiService {
        var statesResult: () -> List<NetworkEntityState> = { emptyList() }
        var callServiceResult: () -> Unit = {}
        override suspend fun ping(): NetworkApiInfo = NetworkApiInfo("API running.")
        override suspend fun getStates(): List<NetworkEntityState> = statesResult()
        override suspend fun callService(domain: String, service: String, body: NetworkServiceCall) = callServiceResult()
    }

    @Test
    fun `getStates renvoie la liste en succes`() = runTest {
        val fake = api { statesResult = { listOf(NetworkEntityState("light.a", "on")) } }
        val ds = RetrofitHomeAssistantNetworkDataSource(fake)
        assertEquals("light.a", ds.getStates().single().entityId)
    }

    @Test
    fun `un 401 devient Unauthorized`() = runTest {
        val http401 = HttpException(Response.error<Any>(401, "".toResponseBody(null)))
        val fake = api { statesResult = { throw http401 } }
        val ds = RetrofitHomeAssistantNetworkDataSource(fake)
        val error = runCatching { ds.getStates() }.exceptionOrNull()
        assertTrue(error is HomeAssistantException.Unauthorized)
    }

    @Test
    fun `une IOException devient Unreachable`() = runTest {
        val fake = api { statesResult = { throw IOException("no route") } }
        val ds = RetrofitHomeAssistantNetworkDataSource(fake)
        val error = runCatching { ds.getStates() }.exceptionOrNull()
        assertTrue(error is HomeAssistantException.Unreachable)
    }
}
```

- [ ] **Step 15 : Module Hilt (Retrofit/OkHttp/serialization)**

`core/network/src/main/java/com/francotte/homecontroller/core/network/NetworkModule.kt` :

```kotlin
package com.francotte.homecontroller.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {

    @Provides
    @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun okHttpClient(interceptor: HomeAssistantAuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("http://localhost/")   // placeholder ; réécrit par l'interceptor
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun apiService(retrofit: Retrofit): HomeAssistantApiService =
        retrofit.create(HomeAssistantApiService::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NetworkBindsModule {
    @Binds
    abstract fun dataSource(impl: RetrofitHomeAssistantNetworkDataSource): HomeAssistantNetworkDataSource
}
```

> Import du convertisseur : le package officiel Retrofit 3 est
> `com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory`. Si
> l'IDE ne le résout pas, vérifier l'artefact `converter-kotlinx-serialization:3.0.0`
> (Task 1) et réessayer ; le nom de package peut être
> `retrofit2.converter.kotlinx.serialization.asConverterFactory` selon la version —
> utiliser celui qui compile.

- [ ] **Step 16 : Vérifier compilation + tests du module**

Run: `./gradlew :core:network:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` (interceptor + mapping d'erreurs verts ; plugin serialization OK).

- [ ] **Step 17 : Commit (utilisateur)** — module `:core:network`.

---

## Task 4 : `:core:datastore` — config (DataStore + Android Keystore)

> `security-crypto` (EncryptedSharedPreferences) étant **déprécié**, le jeton est
> chiffré par une clé AES-GCM adossée à l'**Android Keystore**, et l'ensemble est
> persisté dans **Preferences DataStore**. L'URL (non sensible) est en clair ; le jeton
> est stocké chiffré (ciphertext + IV en base64).

**Files:**
- Modify: `settings.gradle.kts`
- Create: `core/datastore/.gitignore`, `core/datastore/build.gradle.kts`
- Create: `core/datastore/src/main/java/com/francotte/homecontroller/core/datastore/HomeAssistantConfigStore.kt`
- Create: `core/datastore/src/main/java/com/francotte/homecontroller/core/datastore/TokenCrypto.kt`
- Create: `core/datastore/src/main/java/com/francotte/homecontroller/core/datastore/DataStoreHomeAssistantConfigStore.kt`
- Create: `core/datastore/src/main/java/com/francotte/homecontroller/core/datastore/DataStoreModule.kt`

- [ ] **Step 1 : Inclure le module**

Dans `settings.gradle.kts`, ajouter : `include(":core:datastore")`.

- [ ] **Step 2 : `.gitignore`**

Create `core/datastore/.gitignore` avec `/build`.

- [ ] **Step 3 : `core/datastore/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.francotte.homecontroller.core.datastore"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
```

- [ ] **Step 4 : Interface du store**

`core/datastore/src/main/java/com/francotte/homecontroller/core/datastore/HomeAssistantConfigStore.kt` :

```kotlin
package com.francotte.homecontroller.core.datastore

import com.francotte.homecontroller.core.model.HomeAssistantConfig
import kotlinx.coroutines.flow.StateFlow

/** Stockage sécurisé de la configuration Home Assistant. */
interface HomeAssistantConfigStore {
    /** Config courante (null = pas encore configuré). StateFlow → lecture synchrone via `.value`. */
    val config: StateFlow<HomeAssistantConfig?>
    suspend fun save(config: HomeAssistantConfig)
    suspend fun clear()
}
```

- [ ] **Step 5 : Chiffrement du jeton via l'Android Keystore**

`core/datastore/src/main/java/com/francotte/homecontroller/core/datastore/TokenCrypto.kt` :

```kotlin
package com.francotte.homecontroller.core.datastore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Chiffre/déchiffre une chaîne via une clé AES-GCM non exportable de l'Android Keystore. */
internal object TokenCrypto {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "home_assistant_token_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    /** IV + texte chiffré (les deux sont à persister). */
    data class Sealed(val iv: ByteArray, val ciphertext: ByteArray)

    fun encrypt(plaintext: String): Sealed {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Sealed(cipher.iv, ciphertext)
    }

    fun decrypt(iv: ByteArray, ciphertext: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
```

- [ ] **Step 6 : Impl du store (Preferences DataStore)**

`core/datastore/src/main/java/com/francotte/homecontroller/core/datastore/DataStoreHomeAssistantConfigStore.kt` :

```kotlin
package com.francotte.homecontroller.core.datastore

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.francotte.homecontroller.core.model.HomeAssistantConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.configDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "home_assistant_config")

@Singleton
internal class DataStoreHomeAssistantConfigStore @Inject constructor(
    @ApplicationContext context: Context
) : HomeAssistantConfigStore {

    private val dataStore = context.configDataStore

    // Seed synchrone (lecture unique, petite) pour offrir un `.value` à l'interceptor réseau.
    private val _config = MutableStateFlow(runBlocking { read() })
    override val config: StateFlow<HomeAssistantConfig?> = _config.asStateFlow()

    override suspend fun save(config: HomeAssistantConfig) {
        val sealed = TokenCrypto.encrypt(config.token)
        dataStore.edit { prefs ->
            prefs[KEY_URL] = config.baseUrl
            prefs[KEY_TOKEN_CIPHER] = sealed.ciphertext.toBase64()
            prefs[KEY_TOKEN_IV] = sealed.iv.toBase64()
        }
        _config.value = config
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
        _config.value = null
    }

    private suspend fun read(): HomeAssistantConfig? {
        val prefs = dataStore.data.first()
        val url = prefs[KEY_URL] ?: return null
        val cipher = prefs[KEY_TOKEN_CIPHER] ?: return null
        val iv = prefs[KEY_TOKEN_IV] ?: return null
        // Si la clé Keystore a disparu (restauration sur un autre appareil…), on repart non configuré.
        return runCatching {
            HomeAssistantConfig(url, TokenCrypto.decrypt(iv.fromBase64(), cipher.fromBase64()))
        }.getOrNull()
    }

    private fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        val KEY_URL = stringPreferencesKey("url")
        val KEY_TOKEN_CIPHER = stringPreferencesKey("token_cipher")
        val KEY_TOKEN_IV = stringPreferencesKey("token_iv")
    }
}
```

- [ ] **Step 7 : Module Hilt**

`core/datastore/src/main/java/com/francotte/homecontroller/core/datastore/DataStoreModule.kt` :

```kotlin
package com.francotte.homecontroller.core.datastore

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataStoreModule {
    @Binds
    abstract fun bindConfigStore(impl: DataStoreHomeAssistantConfigStore): HomeAssistantConfigStore
}
```

- [ ] **Step 8 : Vérifier la compilation**

Run: `./gradlew :core:datastore:compileDebugKotlin -q`
Expected: `BUILD SUCCESSFUL`.

> Pas de test unitaire JVM : DataStore et l'Android Keystore nécessitent Android. Validé
> manuellement en Task 9 (config persistée + chiffrée après redémarrage).

- [ ] **Step 9 : Commit (utilisateur)** — module `:core:datastore`.

---

## Task 5 : `:core:data` — repository + provider de config

**Files:**
- Modify: `settings.gradle.kts`
- Create: `core/data/.gitignore`, `core/data/build.gradle.kts`
- Create: `core/data/src/main/java/com/francotte/homecontroller/core/data/HomeAssistantRepository.kt`
- Create: `core/data/src/main/java/com/francotte/homecontroller/core/data/DefaultHomeAssistantRepository.kt`
- Create: `core/data/src/main/java/com/francotte/homecontroller/core/data/StoreBackedConfigProvider.kt`
- Create: `core/data/src/main/java/com/francotte/homecontroller/core/data/DataModule.kt`
- Test: `core/data/src/test/java/com/francotte/homecontroller/core/data/DefaultHomeAssistantRepositoryTest.kt`

- [ ] **Step 1 : Inclure le module**

Dans `settings.gradle.kts`, ajouter : `include(":core:data")`.

- [ ] **Step 2 : `.gitignore`**

Create `core/data/.gitignore` avec `/build`.

- [ ] **Step 3 : `core/data/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.francotte.homecontroller.core.data"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 4 : Interface repository**

`core/data/src/main/java/com/francotte/homecontroller/core/data/HomeAssistantRepository.kt` :

```kotlin
package com.francotte.homecontroller.core.data

import com.francotte.homecontroller.core.model.HomeAssistantConfig
import com.francotte.homecontroller.core.model.HomeAssistantEntity
import kotlinx.coroutines.flow.Flow

interface HomeAssistantRepository {
    val config: Flow<HomeAssistantConfig?>
    suspend fun saveConfig(config: HomeAssistantConfig)
    /** Teste une config candidate. Succès = Result.success ; échec porte la HomeAssistantException. */
    suspend fun testConnection(config: HomeAssistantConfig): Result<Unit>
    /** Entités light/switch, mappées et filtrées. */
    suspend fun getControllableEntities(): List<HomeAssistantEntity>
    /** Allume/éteint une entité (turn_on / turn_off sur son domaine). */
    suspend fun setEntityState(entityId: String, on: Boolean)
}
```

- [ ] **Step 5 : Écrire le test du repository (échoue d'abord)**

`core/data/src/test/java/com/francotte/homecontroller/core/data/DefaultHomeAssistantRepositoryTest.kt` :

```kotlin
package com.francotte.homecontroller.core.data

import com.francotte.homecontroller.core.model.HomeAssistantConfig
import com.francotte.homecontroller.core.model.HomeAssistantException
import com.francotte.homecontroller.core.network.HomeAssistantNetworkDataSource
import com.francotte.homecontroller.core.network.NetworkAttributes
import com.francotte.homecontroller.core.network.NetworkEntityState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultHomeAssistantRepositoryTest {

    private class FakeStore : com.francotte.homecontroller.core.datastore.HomeAssistantConfigStore {
        val flow = MutableStateFlow<HomeAssistantConfig?>(null)
        override val config: StateFlow<HomeAssistantConfig?> = flow
        var saved: HomeAssistantConfig? = null
        override suspend fun save(config: HomeAssistantConfig) { saved = config; flow.value = config }
        override suspend fun clear() { flow.value = null }
    }

    private class FakeDataSource : HomeAssistantNetworkDataSource {
        var states: List<NetworkEntityState> = emptyList()
        var testError: Throwable? = null
        val serviceCalls = mutableListOf<Triple<String, String, String>>()
        override suspend fun testConnection(config: HomeAssistantConfig) { testError?.let { throw it } }
        override suspend fun getStates(): List<NetworkEntityState> = states
        override suspend fun callService(domain: String, service: String, entityId: String) {
            serviceCalls.add(Triple(domain, service, entityId))
        }
    }

    private fun repo(ds: FakeDataSource = FakeDataSource(), store: FakeStore = FakeStore()) =
        DefaultHomeAssistantRepository(ds, store)

    @Test
    fun `getControllableEntities filtre light et switch et mappe`() = runTest {
        val ds = FakeDataSource().apply {
            states = listOf(
                NetworkEntityState("light.salon", "on", NetworkAttributes("Salon")),
                NetworkEntityState("switch.prise", "off"),
                NetworkEntityState("sensor.temp", "21.5")   // ignoré
            )
        }
        val result = repo(ds).getControllableEntities()

        assertEquals(listOf("light.salon", "switch.prise"), result.map { it.entityId })
        val salon = result.first()
        assertEquals("Salon", salon.friendlyName)   // friendly_name
        assertTrue(salon.isOn)
        val prise = result[1]
        assertEquals("switch.prise", prise.friendlyName)  // repli sur entityId
        assertFalse(prise.isOn)
        assertEquals("switch", prise.domain)
    }

    @Test
    fun `setEntityState allume via turn_on sur le bon domaine`() = runTest {
        val ds = FakeDataSource()
        repo(ds).setEntityState("light.salon", true)
        assertEquals(Triple("light", "turn_on", "light.salon"), ds.serviceCalls.single())
    }

    @Test
    fun `setEntityState eteint via turn_off`() = runTest {
        val ds = FakeDataSource()
        repo(ds).setEntityState("switch.prise", false)
        assertEquals(Triple("switch", "turn_off", "switch.prise"), ds.serviceCalls.single())
    }

    @Test
    fun `testConnection succes donne Result success`() = runTest {
        val result = repo().testConnection(HomeAssistantConfig("http://x:8123", "t"))
        assertTrue(result.isSuccess)
    }

    @Test
    fun `testConnection echec porte l exception`() = runTest {
        val ds = FakeDataSource().apply { testError = HomeAssistantException.Unauthorized }
        val result = repo(ds).testConnection(HomeAssistantConfig("http://x:8123", "bad"))
        assertTrue(result.exceptionOrNull() is HomeAssistantException.Unauthorized)
    }

    @Test
    fun `saveConfig delegue au store`() = runTest {
        val store = FakeStore()
        repo(store = store).saveConfig(HomeAssistantConfig("http://x:8123", "t"))
        assertEquals("http://x:8123", store.saved?.baseUrl)
    }
}
```

- [ ] **Step 6 : Impl repository**

`core/data/src/main/java/com/francotte/homecontroller/core/data/DefaultHomeAssistantRepository.kt` :

```kotlin
package com.francotte.homecontroller.core.data

import com.francotte.homecontroller.core.datastore.HomeAssistantConfigStore
import com.francotte.homecontroller.core.model.HomeAssistantConfig
import com.francotte.homecontroller.core.model.HomeAssistantEntity
import com.francotte.homecontroller.core.network.HomeAssistantNetworkDataSource
import com.francotte.homecontroller.core.network.NetworkEntityState
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

internal class DefaultHomeAssistantRepository @Inject constructor(
    private val dataSource: HomeAssistantNetworkDataSource,
    private val store: HomeAssistantConfigStore
) : HomeAssistantRepository {

    override val config: Flow<HomeAssistantConfig?> = store.config

    override suspend fun saveConfig(config: HomeAssistantConfig) = store.save(config)

    override suspend fun testConnection(config: HomeAssistantConfig): Result<Unit> =
        runCatching { dataSource.testConnection(config) }

    override suspend fun getControllableEntities(): List<HomeAssistantEntity> =
        dataSource.getStates()
            .filter { it.entityId.substringBefore(".") in CONTROLLABLE }
            .map { it.toDomain() }

    override suspend fun setEntityState(entityId: String, on: Boolean) =
        dataSource.callService(
            domain = entityId.substringBefore("."),
            service = if (on) "turn_on" else "turn_off",
            entityId = entityId
        )

    private fun NetworkEntityState.toDomain(): HomeAssistantEntity {
        val dom = entityId.substringBefore(".")
        return HomeAssistantEntity(
            entityId = entityId,
            domain = dom,
            friendlyName = attributes.friendlyName ?: entityId,
            isOn = state == "on",
            rawState = state
        )
    }

    private companion object {
        val CONTROLLABLE = setOf("light", "switch")
    }
}
```

- [ ] **Step 7 : Provider de config (adossé au store)**

`core/data/src/main/java/com/francotte/homecontroller/core/data/StoreBackedConfigProvider.kt` :

```kotlin
package com.francotte.homecontroller.core.data

import com.francotte.homecontroller.core.datastore.HomeAssistantConfigStore
import com.francotte.homecontroller.core.model.HomeAssistantConfig
import com.francotte.homecontroller.core.network.HomeAssistantConfigProvider
import javax.inject.Inject

/** Alimente l'interceptor réseau avec la config enregistrée (lecture synchrone du StateFlow). */
internal class StoreBackedConfigProvider @Inject constructor(
    private val store: HomeAssistantConfigStore
) : HomeAssistantConfigProvider {
    override fun current(): HomeAssistantConfig? = store.config.value
}
```

- [ ] **Step 8 : Module Hilt**

`core/data/src/main/java/com/francotte/homecontroller/core/data/DataModule.kt` :

```kotlin
package com.francotte.homecontroller.core.data

import com.francotte.homecontroller.core.network.HomeAssistantConfigProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    abstract fun bindRepository(impl: DefaultHomeAssistantRepository): HomeAssistantRepository

    @Binds
    abstract fun bindConfigProvider(impl: StoreBackedConfigProvider): HomeAssistantConfigProvider
}
```

- [ ] **Step 9 : Vérifier compilation + tests**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` (6 tests verts).

- [ ] **Step 10 : Commit (utilisateur)** — module `:core:data`.

---

## Task 6 : `:core:domain` — use cases

**Files:**
- Modify: `settings.gradle.kts`
- Create: `core/domain/.gitignore`, `core/domain/build.gradle.kts`
- Create: `core/domain/src/main/java/com/francotte/homecontroller/core/domain/HomeAssistantUseCases.kt`
- Test: `core/domain/src/test/java/com/francotte/homecontroller/core/domain/HomeAssistantUseCasesTest.kt`

- [ ] **Step 1 : Inclure le module**

Dans `settings.gradle.kts`, ajouter : `include(":core:domain")`.

- [ ] **Step 2 : `.gitignore`**

Create `core/domain/.gitignore` avec `/build`.

- [ ] **Step 3 : `core/domain/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.francotte.homecontroller.core.domain"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
    implementation(project(":core:data"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 4 : Use cases**

`core/domain/src/main/java/com/francotte/homecontroller/core/domain/HomeAssistantUseCases.kt` :

```kotlin
package com.francotte.homecontroller.core.domain

import com.francotte.homecontroller.core.data.HomeAssistantRepository
import com.francotte.homecontroller.core.model.HomeAssistantConfig
import com.francotte.homecontroller.core.model.HomeAssistantEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveConfigUseCase @Inject constructor(private val repo: HomeAssistantRepository) {
    operator fun invoke(): Flow<HomeAssistantConfig?> = repo.config
}

class SaveConfigUseCase @Inject constructor(private val repo: HomeAssistantRepository) {
    suspend operator fun invoke(config: HomeAssistantConfig) = repo.saveConfig(config)
}

class TestConnectionUseCase @Inject constructor(private val repo: HomeAssistantRepository) {
    suspend operator fun invoke(config: HomeAssistantConfig): Result<Unit> = repo.testConnection(config)
}

class GetControllableEntitiesUseCase @Inject constructor(private val repo: HomeAssistantRepository) {
    suspend operator fun invoke(): List<HomeAssistantEntity> = repo.getControllableEntities()
}

class SetEntityStateUseCase @Inject constructor(private val repo: HomeAssistantRepository) {
    suspend operator fun invoke(entityId: String, on: Boolean) = repo.setEntityState(entityId, on)
}
```

- [ ] **Step 5 : Test de délégation**

`core/domain/src/test/java/com/francotte/homecontroller/core/domain/HomeAssistantUseCasesTest.kt` :

```kotlin
package com.francotte.homecontroller.core.domain

import com.francotte.homecontroller.core.data.HomeAssistantRepository
import com.francotte.homecontroller.core.model.HomeAssistantConfig
import com.francotte.homecontroller.core.model.HomeAssistantEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAssistantUseCasesTest {

    private class FakeRepo : HomeAssistantRepository {
        override val config: Flow<HomeAssistantConfig?> = flowOf(null)
        var savedConfig: HomeAssistantConfig? = null
        val toggles = mutableListOf<Pair<String, Boolean>>()
        override suspend fun saveConfig(config: HomeAssistantConfig) { savedConfig = config }
        override suspend fun testConnection(config: HomeAssistantConfig) = Result.success(Unit)
        override suspend fun getControllableEntities() =
            listOf(HomeAssistantEntity("light.a", "light", "A", true, "on"))
        override suspend fun setEntityState(entityId: String, on: Boolean) { toggles.add(entityId to on) }
    }

    @Test
    fun `GetControllableEntities delegue au repository`() = runTest {
        val entities = GetControllableEntitiesUseCase(FakeRepo())()
        assertEquals("light.a", entities.single().entityId)
    }

    @Test
    fun `SetEntityState delegue au repository`() = runTest {
        val repo = FakeRepo()
        SetEntityStateUseCase(repo)("switch.p", false)
        assertEquals("switch.p" to false, repo.toggles.single())
    }

    @Test
    fun `SaveConfig delegue au repository`() = runTest {
        val repo = FakeRepo()
        SaveConfigUseCase(repo)(HomeAssistantConfig("http://x:8123", "t"))
        assertEquals("http://x:8123", repo.savedConfig?.baseUrl)
    }
}
```

- [ ] **Step 6 : Vérifier compilation + tests**

Run: `./gradlew :core:domain:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` (3 tests verts).

- [ ] **Step 7 : Commit (utilisateur)** — module `:core:domain`.

---

## Task 7 : `:feature:homeassistant` — UI + ViewModel

**Files:**
- Modify: `settings.gradle.kts`
- Create: `feature/homeassistant/.gitignore`, `feature/homeassistant/build.gradle.kts`
- Create: `feature/homeassistant/src/main/java/com/francotte/homecontroller/feature/homeassistant/HomeAssistantUiState.kt`
- Create: `feature/homeassistant/src/main/java/com/francotte/homecontroller/feature/homeassistant/HomeAssistantViewModel.kt`
- Create: `feature/homeassistant/src/main/java/com/francotte/homecontroller/feature/homeassistant/HomeAssistantScreen.kt`
- Test: `feature/homeassistant/src/test/java/com/francotte/homecontroller/feature/homeassistant/FakeHomeAssistantRepository.kt`
- Test: `feature/homeassistant/src/test/java/com/francotte/homecontroller/feature/homeassistant/HomeAssistantViewModelTest.kt`

- [ ] **Step 1 : Inclure le module**

Dans `settings.gradle.kts`, ajouter : `include(":feature:homeassistant")`.

- [ ] **Step 2 : `.gitignore`**

Create `feature/homeassistant/.gitignore` avec `/build`.

- [ ] **Step 3 : `feature/homeassistant/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.francotte.homecontroller.feature.homeassistant"
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
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
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

> `:feature:homeassistant` dépend de `:core:data` (en plus de `:core:domain`) pour
> pouvoir construire les use cases sur un faux `HomeAssistantRepository` dans les tests.

- [ ] **Step 4 : UiState**

`feature/homeassistant/src/main/java/com/francotte/homecontroller/feature/homeassistant/HomeAssistantUiState.kt` :

```kotlin
package com.francotte.homecontroller.feature.homeassistant

import com.francotte.homecontroller.core.model.HomeAssistantEntity

/** État de formulaire de configuration. */
data class ConfigFormState(
    val url: String = "",
    val token: String = "",
    val isTesting: Boolean = false,
    val error: String? = null
)

/** État de l'écran Home Assistant. */
sealed interface HomeAssistantUiState {
    /** Lecture initiale de la configuration. */
    data object Loading : HomeAssistantUiState

    /** Pas (ou plus) de configuration : on montre le formulaire. */
    data class Unconfigured(val form: ConfigFormState) : HomeAssistantUiState

    /** Configuré : liste des entités commandables. */
    data class Entities(
        val items: List<HomeAssistantEntity>,
        val isRefreshing: Boolean = false,
        val listError: String? = null,
        val transientError: String? = null
    ) : HomeAssistantUiState
}
```

- [ ] **Step 5 : Écrire le faux repository de test**

`feature/homeassistant/src/test/java/com/francotte/homecontroller/feature/homeassistant/FakeHomeAssistantRepository.kt` :

```kotlin
package com.francotte.homecontroller.feature.homeassistant

import com.francotte.homecontroller.core.data.HomeAssistantRepository
import com.francotte.homecontroller.core.model.HomeAssistantConfig
import com.francotte.homecontroller.core.model.HomeAssistantEntity
import com.francotte.homecontroller.core.model.HomeAssistantException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeHomeAssistantRepository : HomeAssistantRepository {
    val configFlow = MutableStateFlow<HomeAssistantConfig?>(null)
    override val config: Flow<HomeAssistantConfig?> = configFlow

    var entities: List<HomeAssistantEntity> = emptyList()
    var entitiesError: Throwable? = null
    var testResult: Result<Unit> = Result.success(Unit)
    var setError: Throwable? = null
    val toggles = mutableListOf<Pair<String, Boolean>>()

    override suspend fun saveConfig(config: HomeAssistantConfig) { configFlow.value = config }
    override suspend fun testConnection(config: HomeAssistantConfig): Result<Unit> = testResult
    override suspend fun getControllableEntities(): List<HomeAssistantEntity> {
        entitiesError?.let { throw it }
        return entities
    }
    override suspend fun setEntityState(entityId: String, on: Boolean) {
        toggles.add(entityId to on)
        setError?.let { throw it }
    }

    companion object {
        fun unauthorized(): Throwable = HomeAssistantException.Unauthorized
    }
}
```

- [ ] **Step 6 : Écrire le test du ViewModel (échoue d'abord)**

`feature/homeassistant/src/test/java/com/francotte/homecontroller/feature/homeassistant/HomeAssistantViewModelTest.kt` :

```kotlin
package com.francotte.homecontroller.feature.homeassistant

import com.francotte.homecontroller.core.data.HomeAssistantRepository
import com.francotte.homecontroller.core.domain.GetControllableEntitiesUseCase
import com.francotte.homecontroller.core.domain.ObserveConfigUseCase
import com.francotte.homecontroller.core.domain.SaveConfigUseCase
import com.francotte.homecontroller.core.domain.SetEntityStateUseCase
import com.francotte.homecontroller.core.domain.TestConnectionUseCase
import com.francotte.homecontroller.core.model.HomeAssistantConfig
import com.francotte.homecontroller.core.model.HomeAssistantEntity
import com.francotte.homecontroller.core.model.HomeAssistantException
import com.francotte.homecontroller.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeAssistantViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(repo: HomeAssistantRepository) = HomeAssistantViewModel(
        observeConfig = ObserveConfigUseCase(repo),
        saveConfig = SaveConfigUseCase(repo),
        testConnection = TestConnectionUseCase(repo),
        getEntities = GetControllableEntitiesUseCase(repo),
        setEntityState = SetEntityStateUseCase(repo)
    )

    @Test
    fun `sans config demarre sur Unconfigured`() = runTest {
        val model = vm(FakeHomeAssistantRepository())
        advanceUntilIdle()
        assertTrue(model.uiState.value is HomeAssistantUiState.Unconfigured)
    }

    @Test
    fun `avec config demarre sur Entities`() = runTest {
        val repo = FakeHomeAssistantRepository().apply {
            configFlow.value = HomeAssistantConfig("http://x:8123", "t")
            entities = listOf(HomeAssistantEntity("light.a", "light", "A", false, "off"))
        }
        val model = vm(repo)
        advanceUntilIdle()
        val state = model.uiState.value
        assertTrue(state is HomeAssistantUiState.Entities)
        assertEquals("light.a", (state as HomeAssistantUiState.Entities).items.single().entityId)
    }

    @Test
    fun `test and save reussi passe en Entities`() = runTest {
        val repo = FakeHomeAssistantRepository().apply {
            entities = listOf(HomeAssistantEntity("switch.p", "switch", "P", false, "off"))
        }
        val model = vm(repo)
        advanceUntilIdle()
        model.onUrlChange("http://192.168.1.20:8123")
        model.onTokenChange("TOKEN")
        model.onTestAndSave()
        advanceUntilIdle()
        assertTrue(model.uiState.value is HomeAssistantUiState.Entities)
    }

    @Test
    fun `test and save echoue affiche l erreur dans le formulaire`() = runTest {
        val repo = FakeHomeAssistantRepository().apply {
            testResult = Result.failure(HomeAssistantException.Unauthorized)
        }
        val model = vm(repo)
        advanceUntilIdle()
        model.onUrlChange("http://x:8123")
        model.onTokenChange("bad")
        model.onTestAndSave()
        advanceUntilIdle()
        val state = model.uiState.value
        assertTrue(state is HomeAssistantUiState.Unconfigured)
        assertNotNull((state as HomeAssistantUiState.Unconfigured).form.error)
        assertFalse(state.form.isTesting)
    }

    @Test
    fun `toggle optimiste bascule immediatement et appelle le repo`() = runTest {
        val repo = FakeHomeAssistantRepository().apply {
            configFlow.value = HomeAssistantConfig("http://x:8123", "t")
            entities = listOf(HomeAssistantEntity("light.a", "light", "A", false, "off"))
        }
        val model = vm(repo)
        advanceUntilIdle()
        model.onToggle("light.a", true)
        advanceUntilIdle()
        assertEquals("light.a" to true, repo.toggles.first())
        val state = model.uiState.value as HomeAssistantUiState.Entities
        assertTrue(state.items.single { it.entityId == "light.a" }.isOn)
    }

    @Test
    fun `un echec de toggle revient en arriere et remonte une erreur transitoire`() = runTest {
        val repo = FakeHomeAssistantRepository().apply {
            configFlow.value = HomeAssistantConfig("http://x:8123", "t")
            entities = listOf(HomeAssistantEntity("light.a", "light", "A", false, "off"))
            setError = HomeAssistantException.Unreachable
        }
        val model = vm(repo)
        advanceUntilIdle()
        model.onToggle("light.a", true)
        advanceUntilIdle()
        val state = model.uiState.value as HomeAssistantUiState.Entities
        assertFalse(state.items.single { it.entityId == "light.a" }.isOn)  // rollback
        assertNotNull(state.transientError)
    }
}
```

- [ ] **Step 7 : ViewModel**

`feature/homeassistant/src/main/java/com/francotte/homecontroller/feature/homeassistant/HomeAssistantViewModel.kt` :

```kotlin
package com.francotte.homecontroller.feature.homeassistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.francotte.homecontroller.core.domain.GetControllableEntitiesUseCase
import com.francotte.homecontroller.core.domain.ObserveConfigUseCase
import com.francotte.homecontroller.core.domain.SaveConfigUseCase
import com.francotte.homecontroller.core.domain.SetEntityStateUseCase
import com.francotte.homecontroller.core.domain.TestConnectionUseCase
import com.francotte.homecontroller.core.model.HomeAssistantConfig
import com.francotte.homecontroller.core.model.HomeAssistantException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeAssistantViewModel @Inject constructor(
    private val observeConfig: ObserveConfigUseCase,
    private val saveConfig: SaveConfigUseCase,
    private val testConnection: TestConnectionUseCase,
    private val getEntities: GetControllableEntitiesUseCase,
    private val setEntityState: SetEntityStateUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeAssistantUiState>(HomeAssistantUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private var form = ConfigFormState()

    init {
        viewModelScope.launch {
            if (observeConfig().first() != null) loadEntities() else showForm()
        }
    }

    fun onUrlChange(value: String) { form = form.copy(url = value); syncForm() }
    fun onTokenChange(value: String) { form = form.copy(token = value); syncForm() }

    fun onTestAndSave() {
        val candidate = HomeAssistantConfig(form.url.trim(), form.token.trim())
        form = form.copy(isTesting = true, error = null); syncForm()
        viewModelScope.launch {
            testConnection(candidate).fold(
                onSuccess = {
                    saveConfig(candidate)
                    loadEntities()
                },
                onFailure = {
                    form = form.copy(isTesting = false, error = it.toMessage())
                    syncForm()
                }
            )
        }
    }

    fun onEditConfig() { showForm() }

    fun onRefresh() {
        val current = _uiState.value
        if (current is HomeAssistantUiState.Entities) {
            _uiState.value = current.copy(isRefreshing = true, listError = null)
            viewModelScope.launch { loadEntities(isRefresh = true) }
        }
    }

    fun onToggle(entityId: String, on: Boolean) {
        val current = _uiState.value as? HomeAssistantUiState.Entities ?: return
        // Optimiste : bascule immédiate.
        _uiState.value = current.copy(
            items = current.items.map { if (it.entityId == entityId) it.copy(isOn = on) else it },
            transientError = null
        )
        viewModelScope.launch {
            try {
                setEntityState(entityId, on)
                loadEntities()   // réconcilie avec l'état réel
            } catch (t: Throwable) {
                val reverted = _uiState.value as? HomeAssistantUiState.Entities ?: return@launch
                _uiState.value = reverted.copy(
                    items = reverted.items.map { if (it.entityId == entityId) it.copy(isOn = !on) else it },
                    transientError = t.toMessage()
                )
            }
        }
    }

    private fun showForm() { _uiState.value = HomeAssistantUiState.Unconfigured(form) }
    private fun syncForm() {
        if (_uiState.value is HomeAssistantUiState.Unconfigured) {
            _uiState.value = HomeAssistantUiState.Unconfigured(form)
        }
    }

    private suspend fun loadEntities(isRefresh: Boolean = false) {
        try {
            val items = getEntities()
            _uiState.value = HomeAssistantUiState.Entities(items = items, isRefreshing = false)
        } catch (t: Throwable) {
            _uiState.value = HomeAssistantUiState.Entities(
                items = (_uiState.value as? HomeAssistantUiState.Entities)?.items ?: emptyList(),
                isRefreshing = false,
                listError = t.toMessage()
            )
        }
    }
}

/** Message utilisateur pour une erreur (typée ou générique). */
internal fun Throwable.toMessage(): String = when (this) {
    is HomeAssistantException.Unauthorized -> "Jeton refusé (401). Vérifie le jeton d'accès."
    is HomeAssistantException.Unreachable -> "Home Assistant injoignable à cette adresse."
    is HomeAssistantException.NotConfigured -> "Home Assistant n'est pas configuré."
    else -> message ?: "Erreur inconnue."
}
```

- [ ] **Step 8 : Écran (config + liste + toggle + pull-to-refresh)**

`feature/homeassistant/src/main/java/com/francotte/homecontroller/feature/homeassistant/HomeAssistantScreen.kt` :

```kotlin
package com.francotte.homecontroller.feature.homeassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.francotte.homecontroller.core.model.HomeAssistantEntity

@Composable
fun HomeAssistantScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeAssistantViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when (val state = uiState) {
                HomeAssistantUiState.Loading ->
                    CircularProgressIndicator()

                is HomeAssistantUiState.Unconfigured -> ConfigForm(
                    form = state.form,
                    onUrlChange = viewModel::onUrlChange,
                    onTokenChange = viewModel::onTokenChange,
                    onSubmit = viewModel::onTestAndSave
                )

                is HomeAssistantUiState.Entities -> EntitiesContent(
                    state = state,
                    onRefresh = viewModel::onRefresh,
                    onToggle = viewModel::onToggle,
                    onEditConfig = viewModel::onEditConfig
                )
            }
        }
    }
}

@Composable
private fun ConfigForm(
    form: ConfigFormState,
    onUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Connexion à Home Assistant", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = form.url,
            onValueChange = onUrlChange,
            label = { Text("URL (http://192.168.x.x:8123)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = form.token,
            onValueChange = onTokenChange,
            label = { Text("Jeton d'accès longue durée") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onSubmit, enabled = !form.isTesting) {
            Text(if (form.isTesting) "Test en cours…" else "Tester & enregistrer")
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EntitiesContent(
    state: HomeAssistantUiState.Entities,
    onRefresh: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onEditConfig: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Mes appareils", style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onEditConfig) {
            Icon(Icons.Filled.Settings, contentDescription = "Configuration")
        }
    }
    state.transientError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    state.listError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Spacer(Modifier.height(8.dp))

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        if (state.items.isEmpty() && state.listError == null) {
            Text("Aucune lumière ni prise trouvée.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.items, key = { it.entityId }) { entity ->
                    EntityRow(entity, onToggle)
                }
            }
        }
    }
}

@Composable
private fun EntityRow(entity: HomeAssistantEntity, onToggle: (String, Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(entity.friendlyName, style = MaterialTheme.typography.titleSmall)
                Text(entity.entityId, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = entity.isOn, onCheckedChange = { onToggle(entity.entityId, it) })
        }
    }
}
```

- [ ] **Step 9 : Vérifier compilation + tests du module**

Run: `./gradlew :feature:homeassistant:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` (6 tests VM verts).

- [ ] **Step 10 : Commit (utilisateur)** — module `:feature:homeassistant`.

---

## Task 8 : `:app` — shell bottom navigation

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/francotte/homecontroller/navigation/NavKeys.kt`
- Create: `app/src/main/java/com/francotte/homecontroller/navigation/HomeControllerAppShell.kt`
- Delete: `app/src/main/java/com/francotte/homecontroller/navigation/HomeControllerNavDisplay.kt`
- Modify: `app/src/main/java/com/francotte/homecontroller/MainActivity.kt`

- [ ] **Step 1 : Ajouter la dépendance feature au `:app`**

Dans `app/build.gradle.kts`, dans `dependencies { … }`, ajouter sous les autres `project(...)` :

```kotlin
    implementation(project(":feature:homeassistant"))
```

- [ ] **Step 2 : Ajouter la clé de navigation HA**

Dans `app/src/main/java/com/francotte/homecontroller/navigation/NavKeys.kt`, ajouter :

```kotlin
data object HomeAssistantKey : NavKey
```

(le fichier contient déjà `ScanKey` et `DeviceControlKey`).

- [ ] **Step 3 : Créer le shell bottom-nav**

`app/src/main/java/com/francotte/homecontroller/navigation/HomeControllerAppShell.kt` :

```kotlin
package com.francotte.homecontroller.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.francotte.homecontroller.feature.devicedetail.DeviceControlScreen
import com.francotte.homecontroller.feature.homeassistant.HomeAssistantScreen
import com.francotte.homecontroller.feature.scan.ScanScreen

private enum class TopTab(val label: String, val icon: ImageVector) {
    HomeAssistant("Home Assistant", Icons.Filled.Home),
    BluetoothDirect("Bluetooth", Icons.Filled.Search)
}

@Composable
fun HomeControllerAppShell() {
    var selected by rememberSaveable { mutableStateOf(TopTab.HomeAssistant) }

    // Un back stack mémorisé par onglet ; la position de navigation persiste au changement d'onglet.
    val haBackStack = remember { mutableStateListOf<NavKey>(HomeAssistantKey) }
    val bleBackStack = remember { mutableStateListOf<NavKey>(ScanKey) }
    val backStack = when (selected) {
        TopTab.HomeAssistant -> haBackStack
        TopTab.BluetoothDirect -> bleBackStack
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
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
            }
        )
    }
}
```

> Note comportement : la **position de navigation** de chaque onglet est conservée. L'état
> transitoire interne d'un écran (ex. compteur live BLE) peut se réinitialiser au changement
> d'onglet, car le `ViewModelStore` des entrées de l'onglet quitté est libéré. C'est le
> compromis retenu (un seul `NavDisplay`). Préservation complète = incrément ultérieur.

- [ ] **Step 4 : Supprimer l'ancien `HomeControllerNavDisplay.kt`**

Supprimer `app/src/main/java/com/francotte/homecontroller/navigation/HomeControllerNavDisplay.kt` (remplacé par le shell).

- [ ] **Step 5 : Pointer `MainActivity` sur le shell**

Dans `app/src/main/java/com/francotte/homecontroller/MainActivity.kt`, remplacer l'import et l'appel :

```kotlin
import com.francotte.homecontroller.navigation.HomeControllerAppShell
```

et dans `setContent { HomeControllerTheme { … } }`, remplacer `HomeControllerNavDisplay()` par :

```kotlin
                HomeControllerAppShell()
```

- [ ] **Step 6 : Build complet + toute la suite de tests**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. Le graphe Hilt de l'app se câble (repository, data source, store, use cases, VM).

> Si Hilt signale un binding manquant : vérifier que `:app` tire (transitivement)
> `:core:data`/`:core:network`/`:core:datastore` via `:feature:homeassistant`, et que
> `HomeAssistantConfigProvider` est bien lié dans `DataModule` (`:core:data`).

- [ ] **Step 7 : Commit (utilisateur)** — shell bottom-nav + câblage feature HA.

---

## Task 9 : Validation manuelle (téléphone + HA sur le Pi)

**Files:** aucun.

- [ ] **Step 1 : Installer sur le téléphone**

Téléphone branché :
Run: `./gradlew :app:installDebug`
Expected: `BUILD SUCCESSFUL`, app installée.

- [ ] **Step 2 : Parcours Home Assistant**

- [ ] Onglet **Home Assistant** ouvert par défaut → formulaire de config.
- [ ] Saisir l'URL (`http://<ip-du-pi>:8123`) + le jeton longue durée → **Tester & enregistrer**.
- [ ] Jeton/URL erronés → message clair (`401` / injoignable), on reste sur le formulaire.
- [ ] Config valide → liste des **light/switch** réels avec leur état.
- [ ] **Basculer** une lumière/prise → l'appareil réagit ; l'interrupteur suit (rollback + message si échec).
- [ ] **Pull-to-refresh** → la liste se met à jour.
- [ ] Icône **Configuration** → revient au formulaire.

- [ ] **Step 3 : Coexistence des deux mondes**

- [ ] Onglet **Bluetooth** → l'écran de scan BLE fonctionne comme avant (scan → contrôle ESP32).
- [ ] Changer d'onglet et revenir → la **position de navigation** de chaque onglet est conservée.
- [ ] Après redémarrage de l'app : la config HA est **toujours là** (stockage chiffré), l'app ouvre directement la liste.

- [ ] **Step 4 : Commit éventuel (utilisateur)** — ajustements.

---

## Validation croisée avec la spec

- **Périmètre** (shell + connexion + liste + commande, REST) → Tasks 3–8.
- **Entités light/switch (liste + toggle)** → Task 5 (filtre/map), Task 7 (UI/toggle).
- **URL configurable + cleartext** → Task 3 (interceptor dynamique, manifest+networkSecurityConfig).
- **Auth jeton longue durée** → Task 3 (Bearer), Task 4 (stockage chiffré), Task 7 (formulaire).
- **Stockage EncryptedSharedPreferences** → Task 4.
- **Pull-to-refresh, pas de polling** → Task 7 (`PullToRefreshBox`, refresh après commande).
- **Découpage NIA stratifié** → Tasks 2–8 (model/network/datastore/data/domain/feature/app).
- **Erreurs typées** → Task 2 (`HomeAssistantException`), Task 3 (mapping), Task 7 (messages).
- **Shell bottom-nav 2 onglets, back stack par onglet** → Task 8.
- **Tests (fakes + coroutines-test)** → Tasks 3, 5, 6, 7 ; **validation manuelle** → Task 9.
- **Hors périmètre** (WebSocket, autres domaines, zones/dashboards, mDNS) → non couvert, assumé.
```
