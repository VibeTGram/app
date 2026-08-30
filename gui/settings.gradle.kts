import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    plugins {
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

rootProject.name = "vibetgram-gui"
