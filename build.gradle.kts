import org.gradle.api.artifacts.dsl.LockMode

plugins {
    id("com.android.application") version "9.2.0" apply false
    base
}

group = "org.vibetgram"
version = "0.1.0-SNAPSHOT"

description = "VibeTGram Android composition root"

dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}
