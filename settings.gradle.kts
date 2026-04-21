pluginManagement {
    repositories {
        google()            // Removed the restrictive 'content' filter
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral() // <--- Keep this at the top
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "ollama"
include(":app")