pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HomeController"
include(":app")
include(":core:model")
include(":core:testing")
include(":core:designsystem")
include(":core:bluetooth")
include(":feature:scan")
include(":feature:devicedetail")
include(":core:network")
include(":core:datastore")
include(":core:data")
include(":core:domain")
include(":feature:homeassistant")
