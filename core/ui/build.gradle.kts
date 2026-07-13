plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.francotte.homecontroller.core.ui"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
}

composeCompiler {
    // :core:ui affiche des modèles de :core:model : on les traite comme stables côté Compose,
    // sans introduire de dépendance Compose dans la couche domaine.
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("compose_stability.conf")
    )
}

dependencies {
    // Composites « model-aware » : ils parlent le domaine (:core:model) et s'appuient sur
    // les atomes + le thème du design system. Exposés en `api` car ils font partie du
    // contrat des composables réutilisables.
    api(project(":core:designsystem"))
    api(project(":core:model"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3.expressive)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
