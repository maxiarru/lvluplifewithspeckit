pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
}

// This file becomes settings.gradle.kts at the root of the prepared upload.
// The Android project and its SDK/plugins are intentionally absent.
rootProject.name = "lvl-up-life-server"
include(":domain", ":server")
