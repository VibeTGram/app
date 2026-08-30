import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    plugins {
        kotlin("jvm") version "2.3.20"
    }
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "vibetgram-core"
include(":core-api", ":core-storage", ":core-tdlib")
