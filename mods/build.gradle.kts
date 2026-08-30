import org.gradle.api.artifacts.dsl.LockMode

plugins {
    base
}

group = "org.vibetgram.mods"
version = "0.1.0-SNAPSHOT"

description = "VibeTGram addon runtime and Mod SDK"

dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}
