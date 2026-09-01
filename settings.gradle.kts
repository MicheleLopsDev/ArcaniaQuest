pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ArcaniaQuest"

// Le regole non sanno che esiste uno schermo, il gioco le disegna,
// i due lanciatori lo avviano sulle rispettive piattaforme.
include(":regole")
include(":gioco")
include(":desktop")
include(":android")
