import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    plugins {
        id("com.android.application") version "9.2.0"
        id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
        kotlin("jvm") version "2.3.20"
    }
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

rootProject.name = "vibetgram-app"

include(":app")

// These paths are HTTPS submodules checked out at the gitlink commits recorded
// by the app superproject. Included builds keep repository boundaries explicit.
includeBuild("core")
includeBuild("mods")
includeBuild("gui")
